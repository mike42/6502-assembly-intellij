package org.ca65.debugger.gdb;

import com.intellij.openapi.diagnostic.Logger;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.ByteArrayOutputStream;
import java.io.Closeable;
import java.io.EOFException;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Client for the GDB remote serial protocol as spoken by MAME's gdbstub
 * debugger module ({@code -debugger gdbstub}). Packets are framed as
 * {@code $payload#XX} where XX is the two-hex-digit 8-bit sum of the
 * payload bytes.
 *
 * <p>MAME's stub is a deliberately small RSP subset (verified against the
 * MAME source): no {@code QStartNoAckMode} (plain ack mode is mandatory,
 * though the stub never retransmits on a NAK), no {@code X} binary writes
 * (memory writes use hex {@code M}), no {@code vCont}. Register access
 * ({@code g/G/p/P}) returns {@code E01} until {@code target.xml} has been
 * fetched once via {@code qXfer:features:read}, so {@link #handshake()}
 * must run before anything else.
 *
 * <p>RSP allows a single outstanding request. Most commands get an
 * immediate reply ({@link #transact}); {@code c} and {@code s} are only
 * answered when the target stops again, so {@link #sendContinue()} and
 * {@link #sendStep()} return immediately and the eventual stop reply is
 * dispatched to {@link Listener#onStopReply}. All request methods block
 * and must not be called on the EDT.
 */
public class GdbRemoteClient implements Closeable {
    private static final Logger LOG = Logger.getInstance(GdbRemoteClient.class);
    private static final long REQUEST_TIMEOUT_SECONDS = 15;
    private static final Pattern TARGET_XML_REG =
            Pattern.compile("<reg\\s+name=\"([^\"]+)\"\\s+bitsize=\"(\\d+)\"[^/]*/>");

    /** One register slot from target.xml, in the order the {@code g} packet uses. */
    public record GdbRegister(@NotNull String name, int bitsize) {
        int byteCount() {
            return (bitsize + 7) / 8;
        }
    }

    /**
     * A parsed stop reply ({@code T05...}). Register values the stub chose
     * to include are keyed by register name; MAME's m6502 map sends sp and
     * pc with every stop. {@code watchKind} is "watch", "rwatch" or
     * "awatch" when a watchpoint triggered, else null.
     */
    public record StopReply(int signal, @NotNull Map<String, Integer> registers,
                            @Nullable String watchKind, int watchAddress) {
    }

    public interface Listener {
        /** A delayed stop reply to a continue/step arrived (reader thread). */
        void onStopReply(@NotNull StopReply reply);

        void onDisconnected(@Nullable Throwable cause);
    }

    /** The stub answered {@code E NN}. */
    public static class GdbProtocolException extends IOException {
        public final String errorReply;

        GdbProtocolException(@NotNull String packet, @NotNull String errorReply) {
            super("gdbstub rejected \"" + packet + "\" with " + errorReply);
            this.errorReply = errorReply;
        }
    }

    private final Socket mySocket;
    private final InputStream myIn;
    private final OutputStream myOut;
    private final Listener myListener;
    private final Thread myReaderThread;
    private final AtomicBoolean myClosed = new AtomicBoolean();
    /** RSP allows one outstanding request; armed while a transact() waits. */
    private final AtomicReference<CompletableFuture<String>> myPendingReply = new AtomicReference<>();

    private volatile List<GdbRegister> myRegisters = List.of();
    private volatile String myArchitecture = "";
    private volatile int myPacketSize = 0x4000;

    private GdbRemoteClient(@NotNull Socket socket, @NotNull Listener listener) throws IOException {
        mySocket = socket;
        myIn = socket.getInputStream();
        myOut = socket.getOutputStream();
        myListener = listener;
        myReaderThread = new Thread(this::readLoop, "GDB remote reader");
        myReaderThread.setDaemon(true);
        myReaderThread.start();
    }

    /**
     * Connects with retry while the emulator starts up. MAME opens its
     * listening socket only once emulation reaches the first debugger stop
     * — after driver init and ROM load — so this can take several seconds.
     */
    @NotNull
    public static GdbRemoteClient connect(@NotNull String host, int port, @NotNull Listener listener,
                                          long timeoutMillis) throws IOException {
        long deadline = System.currentTimeMillis() + timeoutMillis;
        IOException lastFailure = null;
        while (System.currentTimeMillis() < deadline) {
            try {
                Socket socket = new Socket();
                socket.setTcpNoDelay(true);
                socket.connect(new InetSocketAddress(host, port), 1000);
                return new GdbRemoteClient(socket, listener);
            } catch (IOException e) {
                lastFailure = e;
                try {
                    Thread.sleep(200);
                } catch (InterruptedException interrupted) {
                    Thread.currentThread().interrupt();
                    throw new IOException("Interrupted while connecting to the gdb stub", interrupted);
                }
            }
        }
        throw new IOException("Could not connect to the gdb stub on port " + port, lastFailure);
    }

    // ---- handshake --------------------------------------------------------

    /**
     * Runs the mandatory startup sequence and returns the initial stop
     * state: qSupported, the complete target.xml (register access errors
     * until it has been fetched), then "?".
     */
    @NotNull
    public StopReply handshake() throws IOException {
        String supported = transact("qSupported");
        for (String feature : supported.split(";")) {
            if (feature.startsWith("PacketSize=")) {
                try {
                    myPacketSize = Integer.parseInt(feature.substring("PacketSize=".length()), 16);
                } catch (NumberFormatException e) {
                    LOG.warn("Unparseable PacketSize in " + supported);
                }
            }
        }
        String targetXml = fetchTargetXml();
        Matcher architecture = Pattern.compile("<architecture>([^<]*)</architecture>").matcher(targetXml);
        if (architecture.find()) {
            myArchitecture = architecture.group(1);
        }
        List<GdbRegister> registers = new ArrayList<>();
        Matcher reg = TARGET_XML_REG.matcher(targetXml);
        while (reg.find()) {
            registers.add(new GdbRegister(reg.group(1), Integer.parseInt(reg.group(2))));
        }
        if (registers.isEmpty()) {
            throw new IOException("target.xml listed no registers: " + targetXml);
        }
        myRegisters = List.copyOf(registers);
        return parseStopReply(transact("?"));
    }

    @NotNull
    private String fetchTargetXml() throws IOException {
        StringBuilder xml = new StringBuilder();
        int offset = 0;
        while (true) {
            String chunk = transact("qXfer:features:read:target.xml:"
                    + Integer.toHexString(offset) + "," + Integer.toHexString(myPacketSize / 2));
            if (chunk.isEmpty()) {
                throw new IOException("The gdb stub does not support qXfer:features:read");
            }
            char kind = chunk.charAt(0);
            xml.append(chunk, 1, chunk.length());
            if (kind == 'l') {
                return xml.toString();
            }
            if (kind != 'm') {
                throw new IOException("Unexpected qXfer reply: " + chunk);
            }
            offset = xml.length();
        }
    }

    /** Register layout from target.xml, in {@code g}-packet order. Empty before {@link #handshake()}. */
    @NotNull
    public List<GdbRegister> getRegisterLayout() {
        return myRegisters;
    }

    /** The {@code <architecture>} string from target.xml, e.g. "m6502". */
    @NotNull
    public String getArchitecture() {
        return myArchitecture;
    }

    // ---- registers ----------------------------------------------------------

    /** All register values by target.xml name, decoded little-endian. */
    @NotNull
    public Map<String, Integer> readRegisters() throws IOException {
        String hex = transact("g");
        Map<String, Integer> values = new LinkedHashMap<>();
        int pos = 0;
        for (GdbRegister register : myRegisters) {
            int chars = register.byteCount() * 2;
            if (pos + chars > hex.length()) {
                throw new IOException("Short g reply: " + hex);
            }
            values.put(register.name(), decodeHexLe(hex.substring(pos, pos + chars)));
            pos += chars;
        }
        return values;
    }

    public void writeRegister(@NotNull String name, int value) throws IOException {
        for (int number = 0; number < myRegisters.size(); number++) {
            GdbRegister register = myRegisters.get(number);
            if (register.name().equals(name)) {
                transact("P" + Integer.toHexString(number) + "="
                        + encodeHexLe(value, register.byteCount()));
                return;
            }
        }
        throw new IOException("Unknown register: " + name);
    }

    // ---- memory -------------------------------------------------------------

    /** Reads [start, endInclusive] from the CPU's program address space. */
    public byte @NotNull [] readMemory(int start, int endInclusive) throws IOException {
        int length = endInclusive - start + 1;
        byte[] data = new byte[length];
        int done = 0;
        // Stay well inside the advertised packet size (2 hex chars per byte).
        int chunkLimit = Math.max(1, myPacketSize / 2 - 32);
        while (done < length) {
            int chunk = Math.min(length - done, chunkLimit);
            String hex = transact("m" + Integer.toHexString(start + done) + ","
                    + Integer.toHexString(chunk));
            if (hex.length() != chunk * 2) {
                throw new IOException("Short m reply: expected " + chunk + " bytes, got " + hex);
            }
            for (int i = 0; i < chunk; i++) {
                data[done + i] = (byte) Integer.parseInt(hex.substring(i * 2, i * 2 + 2), 16);
            }
            done += chunk;
        }
        return data;
    }

    public void writeMemory(int start, byte @NotNull [] data) throws IOException {
        // MAME's stub has no X (binary) write; M with hex payload only.
        StringBuilder hex = new StringBuilder(data.length * 2);
        for (byte b : data) {
            hex.append(String.format("%02x", b));
        }
        transact("M" + Integer.toHexString(start) + "," + Integer.toHexString(data.length)
                + ":" + hex);
    }

    // ---- breakpoints ----------------------------------------------------------

    /** Arms a software breakpoint; the address is its identity. */
    public void setBreakpoint(int address) throws IOException {
        transact("Z0," + Integer.toHexString(address) + ",1");
    }

    public void removeBreakpoint(int address) throws IOException {
        transact("z0," + Integer.toHexString(address) + ",1");
    }

    // ---- execution --------------------------------------------------------------

    /** Continues the target; the stop reply arrives via {@link Listener#onStopReply}. */
    public void sendContinue() throws IOException {
        sendPacket("c");
    }

    /** Steps one instruction; the stop reply arrives via {@link Listener#onStopReply}. */
    public void sendStep() throws IOException {
        sendPacket("s");
    }

    /**
     * Interrupts a running target: a raw 0x03 byte between packets, not a
     * packet itself. The stop reply answers the outstanding continue/step.
     */
    public void interrupt() throws IOException {
        synchronized (myOut) {
            myOut.write(0x03);
            myOut.flush();
        }
    }

    /** Runs a command on the emulator's own debugger console (qRcmd / "monitor"). */
    @NotNull
    public String monitorCommand(@NotNull String command) throws IOException {
        StringBuilder packet = new StringBuilder("qRcmd,");
        for (byte b : command.getBytes(StandardCharsets.US_ASCII)) {
            packet.append(String.format("%02x", b));
        }
        String reply = transact(packet.toString());
        if (reply.equals("OK")) {
            return "";
        }
        StringBuilder text = new StringBuilder();
        for (int i = 0; i + 1 < reply.length(); i += 2) {
            text.append((char) Integer.parseInt(reply.substring(i, i + 2), 16));
        }
        return text.toString();
    }

    /** Kills the emulator process. Fire-and-forget: MAME exits without replying. */
    public void kill() {
        try {
            sendPacket("k");
        } catch (IOException e) {
            LOG.warn("Could not send kill packet", e);
        }
    }

    /** Detaches, leaving the emulator running. */
    public void detach() throws IOException {
        transact("D");
    }

    // ---- stop replies -------------------------------------------------------------

    /**
     * Parses {@code T05n:v;...} / {@code S05}. Register values are matched
     * by number against the target.xml layout; watchpoint annotations use
     * the named keys watch/rwatch/awatch.
     */
    @NotNull
    StopReply parseStopReply(@NotNull String reply) throws IOException {
        if (reply.length() < 3 || (reply.charAt(0) != 'T' && reply.charAt(0) != 'S')) {
            throw new IOException("Unexpected stop reply: " + reply);
        }
        int signal = Integer.parseInt(reply.substring(1, 3), 16);
        Map<String, Integer> registers = new LinkedHashMap<>();
        String watchKind = null;
        int watchAddress = -1;
        for (String pair : reply.substring(3).split(";")) {
            int colon = pair.indexOf(':');
            if (colon < 0) {
                continue;
            }
            String key = pair.substring(0, colon);
            String value = pair.substring(colon + 1);
            if (key.equals("watch") || key.equals("rwatch") || key.equals("awatch")) {
                watchKind = key;
                watchAddress = Integer.parseInt(value, 16);
            } else if (key.matches("[0-9a-fA-F]+")) {
                int number = Integer.parseInt(key, 16);
                if (number < myRegisters.size()) {
                    registers.put(myRegisters.get(number).name(), decodeHexLe(value));
                }
            }
        }
        return new StopReply(signal, registers, watchKind, watchAddress);
    }

    public boolean isClosed() {
        return myClosed.get();
    }

    @Override
    public void close() {
        if (myClosed.compareAndSet(false, true)) {
            try {
                mySocket.close();
            } catch (IOException ignored) {
            }
            failPending(new IOException("Connection closed"));
        }
    }

    // ---- wire format ------------------------------------------------------------

    /** Sends a packet and blocks for its immediate reply. */
    @NotNull
    String transact(@NotNull String packet) throws IOException {
        CompletableFuture<String> future = new CompletableFuture<>();
        if (!myPendingReply.compareAndSet(null, future)) {
            throw new IOException("A request is already outstanding (sent \"" + packet + "\")");
        }
        try {
            sendPacket(packet);
            String reply = future.get(REQUEST_TIMEOUT_SECONDS, TimeUnit.SECONDS);
            if (reply.matches("E[0-9a-fA-F]{2}")) {
                throw new GdbProtocolException(packet, reply);
            }
            return reply;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException("Interrupted waiting for a gdb stub reply", e);
        } catch (ExecutionException e) {
            throw new IOException("gdb stub connection failed", e.getCause());
        } catch (TimeoutException e) {
            throw new IOException("The gdb stub did not answer \"" + packet + "\"", e);
        } finally {
            myPendingReply.compareAndSet(future, null);
        }
    }

    private void sendPacket(@NotNull String payload) throws IOException {
        byte[] bytes = payload.getBytes(StandardCharsets.US_ASCII);
        int checksum = 0;
        for (byte b : bytes) {
            checksum = (checksum + (b & 0xff)) & 0xff;
        }
        synchronized (myOut) {
            myOut.write('$');
            myOut.write(bytes);
            myOut.write('#');
            myOut.write(String.format("%02x", checksum).getBytes(StandardCharsets.US_ASCII));
            myOut.flush();
        }
    }

    private void readLoop() {
        try {
            while (!myClosed.get()) {
                int c = myIn.read();
                if (c < 0) {
                    throw new EOFException();
                }
                if (c == '+') {
                    continue;                    // ack for our last packet
                }
                if (c == '-') {
                    // The stub NAKed us. MAME acks anything with a valid
                    // checksum and never requests retransmission itself,
                    // so treat this as fatal rather than resending.
                    LOG.warn("gdb stub NAKed a packet");
                    continue;
                }
                if (c != '$') {
                    continue;                    // resync on packet start
                }
                ByteArrayOutputStream payload = new ByteArrayOutputStream();
                int checksum = 0;
                boolean escaped = false;
                while (true) {
                    int b = myIn.read();
                    if (b < 0) {
                        throw new EOFException();
                    }
                    if (b == '#') {
                        break;
                    }
                    checksum = (checksum + b) & 0xff;
                    if (escaped) {
                        payload.write(b ^ 0x20);
                        escaped = false;
                    } else if (b == '}') {       // RSP escape (used in target.xml)
                        escaped = true;
                    } else {
                        payload.write(b);
                    }
                }
                int expected = (hexDigit(myIn.read()) << 4) | hexDigit(myIn.read());
                if (expected != checksum) {
                    LOG.warn("gdb packet checksum mismatch");
                    synchronized (myOut) {
                        myOut.write('-');
                        myOut.flush();
                    }
                    continue;
                }
                synchronized (myOut) {
                    myOut.write('+');
                    myOut.flush();
                }
                dispatch(payload.toString(StandardCharsets.US_ASCII));
            }
        } catch (EOFException | java.net.SocketException e) {
            handleDisconnect(myClosed.get() ? null : e);
        } catch (IOException e) {
            handleDisconnect(e);
        }
    }

    private void dispatch(@NotNull String packet) {
        CompletableFuture<String> pending = myPendingReply.getAndSet(null);
        if (pending != null) {
            pending.complete(packet);
            return;
        }
        // No request outstanding: this is the delayed reply to a continue
        // or step. Anything else is stray and logged.
        if (packet.startsWith("T") || packet.startsWith("S")) {
            try {
                myListener.onStopReply(parseStopReply(packet));
            } catch (IOException e) {
                LOG.warn("Unparseable stop reply: " + packet, e);
            }
        } else {
            LOG.warn("Unexpected gdb packet with no request outstanding: " + packet);
        }
    }

    private static int hexDigit(int c) throws IOException {
        int value = Character.digit(c, 16);
        if (value < 0) {
            throw new IOException("Bad checksum digit: " + c);
        }
        return value;
    }

    private void handleDisconnect(@Nullable Throwable cause) {
        boolean wasOpen = myClosed.compareAndSet(false, true);
        failPending(new IOException("gdb stub connection lost", cause));
        if (wasOpen) {
            try {
                mySocket.close();
            } catch (IOException ignored) {
            }
            myListener.onDisconnected(cause);
        }
    }

    private void failPending(IOException failure) {
        CompletableFuture<String> pending = myPendingReply.getAndSet(null);
        if (pending != null) {
            pending.completeExceptionally(failure);
        }
    }

    /** Little-endian hex: "fd01" → 0x01fd. */
    private static int decodeHexLe(@NotNull String hex) throws IOException {
        if (hex.length() % 2 != 0) {
            throw new IOException("Odd-length hex value: " + hex);
        }
        int value = 0;
        for (int i = hex.length() - 2; i >= 0; i -= 2) {
            value = (value << 8) | Integer.parseInt(hex.substring(i, i + 2), 16);
        }
        return value;
    }

    private static String encodeHexLe(int value, int byteCount) {
        StringBuilder hex = new StringBuilder(byteCount * 2);
        for (int i = 0; i < byteCount; i++) {
            hex.append(String.format("%02x", (value >> (8 * i)) & 0xff));
        }
        return hex.toString();
    }
}
