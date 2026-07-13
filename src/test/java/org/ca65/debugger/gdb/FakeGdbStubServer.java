package org.ca65.debugger.gdb;

import java.io.ByteArrayOutputStream;
import java.io.Closeable;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Minimal scripted gdbstub for tests. Single connection, single thread;
 * answers the packets the client sends the way MAME's stub does (ack mode,
 * E01 before target.xml was fetched, delayed reply to 'c'), serving the
 * m6502 register map exactly as MAME generates it.
 */
final class FakeGdbStubServer implements Closeable {
    /** The m6502 feature exactly as MAME generates it. */
    static final String TARGET_XML = "<?xml version=\"1.0\"?>"
            + "<!DOCTYPE target SYSTEM \"gdb-target.dtd\">"
            + "<target version=\"1.0\"><architecture>m6502</architecture>"
            + "<feature name=\"mame.m6502\">"
            + "<reg name=\"a\" bitsize=\"8\" type=\"int\"/>"
            + "<reg name=\"x\" bitsize=\"8\" type=\"int\"/>"
            + "<reg name=\"y\" bitsize=\"8\" type=\"int\"/>"
            + "<reg name=\"p\" bitsize=\"8\" type=\"int\"/>"
            + "<reg name=\"sp\" bitsize=\"16\" type=\"data_ptr\"/>"
            + "<reg name=\"pc\" bitsize=\"16\" type=\"code_ptr\"/>"
            + "</feature></target>";

    private final ServerSocket myServerSocket;
    private final Thread myThread;
    private volatile Socket mySocket;
    private volatile OutputStream myOut;
    private volatile boolean myClosed;
    private volatile boolean myTargetXmlSent;
    private volatile boolean myFailNext;
    private volatile int myXmlChunkSize = Integer.MAX_VALUE;
    private volatile int myStepPc = 0x8001;
    private volatile int myStepSp = 0xfd;
    private volatile int myBadChecksums;
    private final Map<String, String> myLastPacketByPrefix = new ConcurrentHashMap<>();
    private final Map<Integer, byte[]> myMemory = new ConcurrentHashMap<>();

    FakeGdbStubServer() throws IOException {
        myServerSocket = new ServerSocket(0);
        myThread = new Thread(this::serve, "fake gdbstub");
        myThread.setDaemon(true);
        myThread.start();
    }

    int getPort() {
        return myServerSocket.getLocalPort();
    }

    void chunkTargetXml(int size) {
        myXmlChunkSize = size;
    }

    void failNext() {
        myFailNext = true;
    }

    void stopOnStep(int pc, int sp) {
        myStepPc = pc;
        myStepSp = sp;
    }

    int badChecksums() {
        return myBadChecksums;
    }

    /** The last packet received whose payload started with the prefix. */
    String lastPacket(String prefix) {
        return myLastPacketByPrefix.get(prefix);
    }

    void setMemory(int address, byte[] data) {
        myMemory.put(address, data);
    }

    /** Emits a stop reply, as MAME does when a breakpoint fires during 'c'. */
    void sendStop(int pc, int sp) throws IOException {
        sendRaw(String.format("T0504:%s;05:%s;", hexLe(sp | 0x0100, 2), hexLe(pc, 2)));
    }

    void sendRaw(String payload) throws IOException {
        OutputStream out = myOut;
        if (out == null) {
            throw new IOException("No client connected");
        }
        int checksum = 0;
        for (byte b : payload.getBytes(StandardCharsets.US_ASCII)) {
            checksum = (checksum + (b & 0xff)) & 0xff;
        }
        synchronized (this) {
            out.write(('$' + payload + '#' + String.format("%02x", checksum))
                    .getBytes(StandardCharsets.US_ASCII));
            out.flush();
        }
    }

    private void serve() {
        try {
            mySocket = myServerSocket.accept();
            myOut = mySocket.getOutputStream();
            InputStream in = mySocket.getInputStream();
            while (!myClosed) {
                int c = in.read();
                if (c < 0) {
                    return;
                }
                if (c == 0x03) {
                    sendStop(0x8000, 0xfd);   // interrupt answers the pending 'c'
                    continue;
                }
                if (c != '$') {
                    continue;                 // ignore +/- acks
                }
                ByteArrayOutputStream payload = new ByteArrayOutputStream();
                int checksum = 0;
                int b;
                while ((b = in.read()) != '#') {
                    if (b < 0) {
                        return;
                    }
                    checksum = (checksum + b) & 0xff;
                    payload.write(b);
                }
                int expected = (Character.digit(in.read(), 16) << 4) | Character.digit(in.read(), 16);
                if (expected != checksum) {
                    myBadChecksums++;
                    myOut.write('-');
                    myOut.flush();
                    continue;
                }
                myOut.write('+');
                myOut.flush();
                handle(payload.toString(StandardCharsets.US_ASCII));
            }
        } catch (IOException ignored) {
            // client went away or server closed
        }
    }

    private void handle(String packet) throws IOException {
        myLastPacketByPrefix.put(packet.substring(0, 1), packet);
        if (myFailNext) {
            myFailNext = false;
            sendRaw("E01");
            return;
        }
        if (packet.equals("qSupported")) {
            sendRaw("PacketSize=4000;qXfer:features:read+");
        } else if (packet.startsWith("qXfer:features:read:target.xml:")) {
            String[] range = packet.substring(packet.lastIndexOf(':') + 1).split(",");
            int offset = Integer.parseInt(range[0], 16);
            int length = Math.min(Integer.parseInt(range[1], 16), myXmlChunkSize);
            myTargetXmlSent = true;
            if (offset >= TARGET_XML.length()) {
                sendRaw("l");
            } else if (offset + length >= TARGET_XML.length()) {
                sendRaw("l" + TARGET_XML.substring(offset));
            } else {
                sendRaw("m" + TARGET_XML.substring(offset, offset + length));
            }
        } else if (packet.startsWith("qRcmd,")) {
            sendRaw("6f6b0a");                // "ok\n"
        } else if (packet.equals("?")) {
            sendStop(0x8000, 0xfd);
        } else if (packet.equals("g")) {
            if (!myTargetXmlSent) {
                sendRaw("E01");               // MAME guards g/G/p/P this way
                return;
            }
            sendRaw("0f0500b4" + hexLe(0x01fd, 2) + hexLe(0x8000, 2));
        } else if (packet.startsWith("P")) {
            sendRaw(myTargetXmlSent ? "OK" : "E01");
        } else if (packet.startsWith("m")) {
            String[] parts = packet.substring(1).split(",");
            int address = Integer.parseInt(parts[0], 16);
            int length = Integer.parseInt(parts[1], 16);
            byte[] data = myMemory.getOrDefault(address, new byte[length]);
            StringBuilder hex = new StringBuilder();
            for (int i = 0; i < length; i++) {
                hex.append(String.format("%02x", i < data.length ? data[i] : 0));
            }
            sendRaw(hex.toString());
        } else if (packet.startsWith("M") || packet.startsWith("Z") || packet.startsWith("z")) {
            sendRaw("OK");
        } else if (packet.equals("s")) {
            sendStop(myStepPc, myStepSp);
        } else if (packet.equals("c") || packet.equals("D") || packet.equals("k")) {
            if (packet.equals("D")) {
                sendRaw("OK");
            }
            // 'c': no reply until sendStop(); 'k': no reply at all
        } else {
            sendRaw("");                      // unsupported, like the real stub
        }
    }

    private static String hexLe(int value, int byteCount) {
        StringBuilder hex = new StringBuilder();
        for (int i = 0; i < byteCount; i++) {
            hex.append(String.format("%02x", (value >> (8 * i)) & 0xff));
        }
        return hex.toString();
    }

    @Override
    public void close() {
        myClosed = true;
        try {
            myServerSocket.close();
        } catch (IOException ignored) {
        }
        Socket socket = mySocket;
        if (socket != null) {
            try {
                socket.close();
            } catch (IOException ignored) {
            }
        }
    }
}
