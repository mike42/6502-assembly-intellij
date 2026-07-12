package org.ca65.debugger.vice;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.ByteArrayOutputStream;
import java.io.Closeable;
import java.io.DataInputStream;
import java.io.EOFException;
import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Client for the VICE binary monitor protocol (API version 2, VICE ≥ 3.5;
 * spec: vice.texi "Binary monitor"). All values little-endian.
 *
 * <pre>
 * command:  02 | api(02) | body_len u32 | request_id u32 | cmd u8 | body
 * response: 02 | api(02) | body_len u32 | type u8 | err u8 | request_id u32 | body
 * </pre>
 *
 * <p>Responses are correlated by request id; frames with request id
 * 0xffffffff are asynchronous events, dispatched to the {@link EventListener}.
 * All request methods block and must not be called on the EDT.
 *
 * <p>Protocol quirks the caller must respect (verified by experiment: any
 * command traps the emulator into the monitor — follow up with {@link #exitMonitor()}
 * to keep it running; delete all checkpoints before detaching or VICE wedges;
 * STOPPED events are not all user-relevant (autostart/reset produce
 * transient stop/resume chatter).
 */
public class ViceBinaryMonitorClient implements Closeable {
    // Commands
    private static final int CMD_MEM_GET = 0x01;
    private static final int CMD_MEM_SET = 0x02;
    private static final int CMD_CHECKPOINT_SET = 0x12;
    private static final int CMD_CHECKPOINT_DELETE = 0x13;
    private static final int CMD_CHECKPOINT_TOGGLE = 0x15;
    private static final int CMD_CONDITION_SET = 0x22;
    private static final int CMD_REGISTERS_GET = 0x31;
    private static final int CMD_REGISTERS_SET = 0x32;
    private static final int CMD_ADVANCE_INSTRUCTIONS = 0x71;
    private static final int CMD_EXECUTE_UNTIL_RETURN = 0x73;
    private static final int CMD_PING = 0x81;
    private static final int CMD_REGISTERS_AVAILABLE = 0x83;
    private static final int CMD_VICE_INFO = 0x85;
    private static final int CMD_EXIT = 0xaa;
    private static final int CMD_QUIT = 0xbb;
    private static final int CMD_RESET = 0xcc;
    private static final int CMD_AUTOSTART = 0xdd;

    // Event/response types
    private static final int TYPE_CHECKPOINT_INFO = 0x11;
    private static final int TYPE_REGISTER_INFO = 0x31;
    private static final int TYPE_JAM = 0x61;
    private static final int TYPE_STOPPED = 0x62;
    private static final int TYPE_RESUMED = 0x63;

    private static final int EVENT_REQUEST_ID = 0xffffffff;
    private static final int ERR_OBJECT_MISSING = 0x01;
    private static final long REQUEST_TIMEOUT_SECONDS = 15;

    /** Checkpoint operation bits. */
    public static final int OP_LOAD = 1;
    public static final int OP_STORE = 2;
    public static final int OP_EXEC = 4;

    public interface EventListener {
        default void onStopped(int pc) {
        }

        default void onResumed(int pc) {
        }

        default void onCheckpoint(@NotNull CheckpointInfo info) {
        }

        default void onRegisters(@NotNull Map<String, Integer> registers) {
        }

        default void onJam(int pc) {
        }

        default void onDisconnected(@Nullable Throwable cause) {
        }
    }

    public record CheckpointInfo(int number, boolean hit, int start, int end, boolean stop,
                                 boolean enabled, int operation, boolean temporary,
                                 long hitCount, long ignoreCount, boolean hasCondition, int memspace) {
    }

    public static class ViceProtocolException extends IOException {
        public final int errorCode;

        ViceProtocolException(int command, int errorCode) {
            super(String.format("VICE rejected command 0x%02x with error 0x%02x", command, errorCode));
            this.errorCode = errorCode;
        }
    }

    private record Response(int type, int error, byte[] body) {
    }

    private final Socket mySocket;
    private final DataInputStream myIn;
    private final OutputStream myOut;
    private final EventListener myListener;
    private final Thread myReaderThread;
    private final AtomicInteger myNextRequestId = new AtomicInteger(1);
    private final Map<Integer, CompletableFuture<Response>> myPending = new ConcurrentHashMap<>();
    private final AtomicBoolean myClosed = new AtomicBoolean();
    /** Register id → name, discovered via REGISTERS_AVAILABLE. */
    private volatile Map<Integer, String> myRegisterNames = Map.of();

    private ViceBinaryMonitorClient(@NotNull Socket socket, @NotNull EventListener listener) throws IOException {
        mySocket = socket;
        myIn = new DataInputStream(socket.getInputStream());
        myOut = socket.getOutputStream();
        myListener = listener;
        myReaderThread = new Thread(this::readLoop, "VICE binary monitor reader");
        myReaderThread.setDaemon(true);
        myReaderThread.start();
    }

    /** Connects with retry while the emulator starts up. */
    @NotNull
    public static ViceBinaryMonitorClient connect(@NotNull String host, int port,
                                                  @NotNull EventListener listener,
                                                  long timeoutMillis) throws IOException {
        long deadline = System.currentTimeMillis() + timeoutMillis;
        IOException lastFailure = null;
        while (System.currentTimeMillis() < deadline) {
            try {
                Socket socket = new Socket();
                socket.setTcpNoDelay(true);
                socket.connect(new InetSocketAddress(host, port), 1000);
                return new ViceBinaryMonitorClient(socket, listener);
            } catch (IOException e) {
                lastFailure = e;
                try {
                    Thread.sleep(200);
                } catch (InterruptedException interrupted) {
                    Thread.currentThread().interrupt();
                    throw new IOException("Interrupted while connecting to VICE", interrupted);
                }
            }
        }
        throw new IOException("Could not connect to the VICE binary monitor on port " + port, lastFailure);
    }

    // ---- high-level commands -------------------------------------------

    public void ping() throws IOException {
        request(CMD_PING, new byte[0]);
    }

    /** e.g. "3.10". */
    @NotNull
    public String getViceVersion() throws IOException {
        byte[] body = request(CMD_VICE_INFO, new byte[0]).body;
        int versionLength = body[0] & 0xff;
        StringBuilder version = new StringBuilder();
        for (int i = 0; i < versionLength; i++) {
            if (i > 0) {
                version.append('.');
            }
            version.append(body[1 + i] & 0xff);
        }
        return version.toString();
    }

    /** Discovers the machine's register set; must be called once before register access. */
    public void fetchAvailableRegisters() throws IOException {
        byte[] body = request(CMD_REGISTERS_AVAILABLE, new byte[]{0}).body;
        Map<Integer, String> names = new HashMap<>();
        int count = readU16(body, 0);
        int offset = 2;
        for (int i = 0; i < count; i++) {
            int itemSize = body[offset] & 0xff;
            int id = body[offset + 1] & 0xff;
            int nameLength = body[offset + 3] & 0xff;
            names.put(id, new String(body, offset + 4, nameLength, StandardCharsets.US_ASCII));
            offset += itemSize + 1;
        }
        myRegisterNames = names;
    }

    /** Current register values by name (A, X, Y, PC, SP, FL, …). Pauses the emulator. */
    @NotNull
    public Map<String, Integer> getRegisters() throws IOException {
        byte[] body = request(CMD_REGISTERS_GET, new byte[]{0}).body;
        return parseRegisters(body);
    }

    public void setRegister(@NotNull String name, int value) throws IOException {
        Integer id = null;
        for (Map.Entry<Integer, String> entry : myRegisterNames.entrySet()) {
            if (entry.getValue().equals(name)) {
                id = entry.getKey();
                break;
            }
        }
        if (id == null) {
            throw new IOException("Unknown register: " + name);
        }
        ByteArrayOutputStream body = new ByteArrayOutputStream();
        body.write(0);                       // memspace
        writeU16(body, 1);                   // count
        body.write(3);                       // item size
        body.write(id);
        writeU16(body, value);
        request(CMD_REGISTERS_SET, body.toByteArray());
    }

    /** Reads [start, end] inclusive from the CPU view without side effects. Pauses the emulator. */
    public byte @NotNull [] readMemory(int start, int end) throws IOException {
        ByteArrayOutputStream body = new ByteArrayOutputStream();
        body.write(0);                       // no side effects
        writeU16(body, start);
        writeU16(body, end);
        body.write(0);                       // memspace: main
        writeU16(body, 0);                   // bank: cpu view
        byte[] response = request(CMD_MEM_GET, body.toByteArray()).body;
        int length = readU16(response, 0);
        byte[] data = new byte[length];
        System.arraycopy(response, 2, data, 0, length);
        return data;
    }

    public void writeMemory(int start, byte @NotNull [] data) throws IOException {
        ByteArrayOutputStream body = new ByteArrayOutputStream();
        body.write(0);
        writeU16(body, start);
        writeU16(body, start + data.length - 1);
        body.write(0);
        writeU16(body, 0);
        body.writeBytes(data);
        request(CMD_MEM_SET, body.toByteArray());
    }

    /** Sets a checkpoint; the response carries the assigned number. */
    @NotNull
    public CheckpointInfo setCheckpoint(int start, int end, int operation, boolean temporary)
            throws IOException {
        ByteArrayOutputStream body = new ByteArrayOutputStream();
        writeU16(body, start);
        writeU16(body, end);
        body.write(1);                       // stop when hit
        body.write(1);                       // enabled
        body.write(operation);
        body.write(temporary ? 1 : 0);
        byte[] response = request(CMD_CHECKPOINT_SET, body.toByteArray()).body;
        return parseCheckpoint(response);
    }

    /** Deleting an already-gone checkpoint (e.g. a hit temporary) is not an error. */
    public void deleteCheckpoint(int number) throws IOException {
        ByteArrayOutputStream body = new ByteArrayOutputStream();
        writeU32(body, number);
        try {
            request(CMD_CHECKPOINT_DELETE, body.toByteArray());
        } catch (ViceProtocolException e) {
            if (e.errorCode != ERR_OBJECT_MISSING) {
                throw e;
            }
        }
    }

    /** Enables or disables a checkpoint without deleting it. */
    public void toggleCheckpoint(int number, boolean enabled) throws IOException {
        ByteArrayOutputStream body = new ByteArrayOutputStream();
        writeU32(body, number);
        body.write(enabled ? 1 : 0);
        request(CMD_CHECKPOINT_TOGGLE, body.toByteArray());
    }

    /** Condition in VICE monitor expression syntax, e.g. {@code X == $05} or {@code @ram:($02) == $10}. */
    public void setCondition(int checkpointNumber, @NotNull String expression) throws IOException {
        byte[] text = expression.getBytes(StandardCharsets.US_ASCII);
        ByteArrayOutputStream body = new ByteArrayOutputStream();
        writeU32(body, checkpointNumber);
        body.write(text.length);
        body.writeBytes(text);
        request(CMD_CONDITION_SET, body.toByteArray());
    }

    /**
     * Steps by whole instructions. Server-side step-over (SO=1) is only
     * reliable in a clean machine state; the debugger implements step-over
     * client-side and uses this with {@code stepOver=false}.
     */
    public void advanceInstructions(boolean stepOver, int count) throws IOException {
        ByteArrayOutputStream body = new ByteArrayOutputStream();
        body.write(stepOver ? 1 : 0);
        writeU16(body, count);
        request(CMD_ADVANCE_INSTRUCTIONS, body.toByteArray());
    }

    public void executeUntilReturn() throws IOException {
        request(CMD_EXECUTE_UNTIL_RETURN, new byte[0]);
    }

    /** Resumes execution (EXIT). Harmless when already running. */
    public void exitMonitor() throws IOException {
        request(CMD_EXIT, new byte[0]);
    }

    public void quit() throws IOException {
        request(CMD_QUIT, new byte[0]);
    }

    public void reset(boolean hard) throws IOException {
        request(CMD_RESET, new byte[]{(byte) (hard ? 1 : 0)});
    }

    /** Loads and (optionally) runs a program file, like dragging a .prg onto VICE. */
    public void autostart(@NotNull String filePath, boolean runAfterLoad) throws IOException {
        byte[] path = filePath.getBytes(StandardCharsets.UTF_8);
        ByteArrayOutputStream body = new ByteArrayOutputStream();
        body.write(runAfterLoad ? 1 : 0);
        writeU16(body, 0);                   // file index (for disk images)
        body.write(path.length);
        body.writeBytes(path);
        request(CMD_AUTOSTART, body.toByteArray());
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

    // ---- wire format ----------------------------------------------------

    @NotNull
    private Response request(int command, byte @NotNull [] body) throws IOException {
        CompletableFuture<Response> future = new CompletableFuture<>();
        int requestId = myNextRequestId.getAndIncrement();
        myPending.put(requestId, future);
        try {
            synchronized (myOut) {
                ByteArrayOutputStream frame = new ByteArrayOutputStream();
                frame.write(0x02);           // STX
                frame.write(0x02);           // API version
                writeU32(frame, body.length);
                writeU32(frame, requestId);
                frame.write(command);
                frame.writeBytes(body);
                myOut.write(frame.toByteArray());
                myOut.flush();
            }
            Response response = future.get(REQUEST_TIMEOUT_SECONDS, TimeUnit.SECONDS);
            if (response.error != 0) {
                throw new ViceProtocolException(command, response.error);
            }
            return response;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException("Interrupted waiting for VICE response", e);
        } catch (ExecutionException e) {
            throw new IOException("VICE connection failed", e.getCause());
        } catch (TimeoutException e) {
            throw new IOException(String.format("VICE did not answer command 0x%02x", command), e);
        } finally {
            myPending.remove(requestId);
        }
    }

    private void readLoop() {
        try {
            while (!myClosed.get()) {
                if (myIn.readUnsignedByte() != 0x02) {
                    continue;                // resync on STX
                }
                myIn.readUnsignedByte();     // API version
                int bodyLength = Integer.reverseBytes(myIn.readInt());
                int type = myIn.readUnsignedByte();
                int error = myIn.readUnsignedByte();
                int requestId = Integer.reverseBytes(myIn.readInt());
                byte[] body = new byte[bodyLength];
                myIn.readFully(body);
                if (requestId == EVENT_REQUEST_ID) {
                    dispatchEvent(type, body);
                } else {
                    CompletableFuture<Response> future = myPending.remove(requestId);
                    if (future != null) {
                        future.complete(new Response(type, error, body));
                    }
                }
            }
        } catch (EOFException | java.net.SocketException e) {
            handleDisconnect(myClosed.get() ? null : e);
        } catch (IOException e) {
            handleDisconnect(e);
        }
    }

    private void handleDisconnect(@Nullable Throwable cause) {
        boolean wasOpen = myClosed.compareAndSet(false, true);
        failPending(new IOException("VICE connection lost", cause));
        if (wasOpen) {
            try {
                mySocket.close();
            } catch (IOException ignored) {
            }
            myListener.onDisconnected(cause);
        }
    }

    private void failPending(IOException failure) {
        for (CompletableFuture<Response> future : myPending.values()) {
            future.completeExceptionally(failure);
        }
        myPending.clear();
    }

    private void dispatchEvent(int type, byte[] body) {
        switch (type) {
            case TYPE_STOPPED -> myListener.onStopped(readU16(body, 0));
            case TYPE_RESUMED -> myListener.onResumed(readU16(body, 0));
            case TYPE_JAM -> myListener.onJam(readU16(body, 0));
            case TYPE_CHECKPOINT_INFO -> myListener.onCheckpoint(parseCheckpoint(body));
            case TYPE_REGISTER_INFO -> myListener.onRegisters(parseRegisters(body));
            default -> {
                // other events (banks, display, …) are not interesting yet
            }
        }
    }

    @NotNull
    private Map<String, Integer> parseRegisters(byte[] body) {
        Map<Integer, String> names = myRegisterNames;
        Map<String, Integer> registers = new LinkedHashMap<>();
        int count = readU16(body, 0);
        int offset = 2;
        for (int i = 0; i < count; i++) {
            int itemSize = body[offset] & 0xff;
            int id = body[offset + 1] & 0xff;
            int value = readU16(body, offset + 2);
            String name = names.get(id);
            registers.put(name != null ? name : "reg" + id, value);
            offset += itemSize + 1;
        }
        return registers;
    }

    @NotNull
    private static CheckpointInfo parseCheckpoint(byte[] body) {
        return new CheckpointInfo(
                readU32(body, 0),
                body[4] != 0,
                readU16(body, 5),
                readU16(body, 7),
                body[9] != 0,
                body[10] != 0,
                body[11] & 0xff,
                body[12] != 0,
                readU32(body, 13) & 0xffffffffL,
                readU32(body, 17) & 0xffffffffL,
                body[21] != 0,
                body.length > 22 ? body[22] & 0xff : 0);
    }

    private static int readU16(byte[] data, int offset) {
        return (data[offset] & 0xff) | ((data[offset + 1] & 0xff) << 8);
    }

    private static int readU32(byte[] data, int offset) {
        return (data[offset] & 0xff) | ((data[offset + 1] & 0xff) << 8)
                | ((data[offset + 2] & 0xff) << 16) | ((data[offset + 3] & 0xff) << 24);
    }

    private static void writeU16(ByteArrayOutputStream out, int value) {
        out.write(value & 0xff);
        out.write((value >> 8) & 0xff);
    }

    private static void writeU32(ByteArrayOutputStream out, int value) {
        out.write(value & 0xff);
        out.write((value >> 8) & 0xff);
        out.write((value >> 16) & 0xff);
        out.write((value >> 24) & 0xff);
    }
}
