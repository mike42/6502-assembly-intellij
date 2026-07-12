package org.ca65.debugger.vice;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.Map;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

/**
 * Exercises framing, request/response correlation and event dispatch
 * against a scripted server speaking the binary monitor wire format
 * (byte layouts as captured from real VICE 3.10 in debugger-research/).
 */
public class ViceBinaryMonitorClientTest {
    private FakeViceServer myServer;
    private ViceBinaryMonitorClient myClient;
    private final BlockingQueue<Object[]> myEvents = new LinkedBlockingQueue<>();

    @Before
    public void setUp() throws Exception {
        myServer = new FakeViceServer();
        myClient = ViceBinaryMonitorClient.connect("127.0.0.1", myServer.getPort(),
                new ViceBinaryMonitorClient.EventListener() {
                    @Override
                    public void onStopped(int pc) {
                        myEvents.add(new Object[]{"stopped", pc});
                    }

                    @Override
                    public void onCheckpoint(ViceBinaryMonitorClient.@org.jetbrains.annotations.NotNull CheckpointInfo info) {
                        myEvents.add(new Object[]{"checkpoint", info});
                    }
                }, 5000);
    }

    @After
    public void tearDown() throws Exception {
        myClient.close();
        myServer.close();
    }

    @Test
    public void pingRoundTrip() throws Exception {
        myClient.ping();
        assertEquals(0x81, myServer.lastCommand());
    }

    @Test
    public void registersAvailableAndGet() throws Exception {
        myClient.fetchAvailableRegisters();
        Map<String, Integer> registers = myClient.getRegisters();
        assertEquals(Integer.valueOf(0x0f), registers.get("A"));
        assertEquals(Integer.valueOf(0x081f), registers.get("PC"));
    }

    @Test
    public void checkpointSetParsesAssignedNumber() throws Exception {
        ViceBinaryMonitorClient.CheckpointInfo info =
                myClient.setCheckpoint(0x081f, 0x081f, ViceBinaryMonitorClient.OP_EXEC, false);
        assertEquals(7, info.number());
        assertEquals(0x081f, info.start());
        assertTrue(info.enabled());
    }

    @Test
    public void checkpointToggleSendsNumberAndFlag() throws Exception {
        myClient.toggleCheckpoint(7, false);
        assertEquals(0x15, myServer.lastCommand());
        assertArrayEquals(new byte[]{7, 0, 0, 0, 0}, myServer.lastBody());
        myClient.toggleCheckpoint(7, true);
        assertArrayEquals(new byte[]{7, 0, 0, 0, 1}, myServer.lastBody());
    }

    @Test
    public void memoryRead() throws Exception {
        byte[] data = myClient.readMemory(0xc000, 0xc003);
        assertArrayEquals(new byte[]{0x20, 0x10, (byte) 0xc0, 0x60}, data);
    }

    @Test
    public void eventsAreDispatched() throws Exception {
        myServer.sendStoppedEvent(0x1234);
        Object[] event = myEvents.poll(5, TimeUnit.SECONDS);
        assertNotNull(event);
        assertEquals("stopped", event[0]);
        assertEquals(0x1234, event[1]);
    }

    @Test
    public void errorResponsesBecomeExceptions() {
        try {
            myClient.setCondition(99, "X == $05");   // fake server rejects with 0x01
            throw new AssertionError("expected protocol exception");
        } catch (IOException e) {
            assertTrue(e instanceof ViceBinaryMonitorClient.ViceProtocolException);
            assertEquals(0x01, ((ViceBinaryMonitorClient.ViceProtocolException) e).errorCode);
        }
    }

    /** Minimal scripted binary-monitor endpoint. */
    private static final class FakeViceServer implements AutoCloseable {
        private final ServerSocket myServerSocket;
        private final Thread myThread;
        private volatile Socket mySocket;
        private volatile int myLastCommand = -1;
        private volatile byte[] myLastBody = new byte[0];

        FakeViceServer() throws IOException {
            myServerSocket = new ServerSocket(0);
            myThread = new Thread(this::serve, "fake VICE");
            myThread.setDaemon(true);
            myThread.start();
        }

        int getPort() {
            return myServerSocket.getLocalPort();
        }

        int lastCommand() {
            return myLastCommand;
        }

        byte[] lastBody() {
            return myLastBody;
        }

        private void serve() {
            try {
                mySocket = myServerSocket.accept();
                DataInputStream in = new DataInputStream(mySocket.getInputStream());
                while (true) {
                    in.readUnsignedByte();                       // STX
                    in.readUnsignedByte();                       // API version
                    int bodyLength = Integer.reverseBytes(in.readInt());
                    int requestId = Integer.reverseBytes(in.readInt());
                    int command = in.readUnsignedByte();
                    byte[] body = new byte[bodyLength];
                    in.readFully(body);
                    myLastCommand = command;
                    myLastBody = body;
                    respond(command, requestId, body);
                }
            } catch (IOException ignored) {
            }
        }

        private void respond(int command, int requestId, byte[] body) throws IOException {
            switch (command) {
                case 0x83 -> {                                   // REGISTERS_AVAILABLE
                    ByteArrayOutputStream out = new ByteArrayOutputStream();
                    u16(out, 3);
                    registerDescriptor(out, 0, 8, "A");
                    registerDescriptor(out, 1, 8, "X");
                    registerDescriptor(out, 3, 16, "PC");
                    send(command, 0, requestId, out.toByteArray());
                }
                case 0x31 -> {                                   // REGISTERS_GET
                    ByteArrayOutputStream out = new ByteArrayOutputStream();
                    u16(out, 3);
                    registerValue(out, 0, 0x0f);
                    registerValue(out, 1, 0x05);
                    registerValue(out, 3, 0x081f);
                    send(command, 0, requestId, out.toByteArray());
                }
                case 0x12 -> {                                   // CHECKPOINT_SET → type 0x11
                    int start = (body[0] & 0xff) | ((body[1] & 0xff) << 8);
                    int end = (body[2] & 0xff) | ((body[3] & 0xff) << 8);
                    send(0x11, 0, requestId, checkpointBody(7, false, start, end));
                }
                case 0x01 -> {                                   // MEM_GET
                    ByteArrayOutputStream out = new ByteArrayOutputStream();
                    u16(out, 4);
                    out.writeBytes(new byte[]{0x20, 0x10, (byte) 0xc0, 0x60});
                    send(command, 0, requestId, out.toByteArray());
                }
                case 0x22 -> send(command, 0x01, requestId, new byte[0]);   // CONDITION_SET: reject
                default -> send(command, 0, requestId, new byte[0]);
            }
        }

        void sendStoppedEvent(int pc) throws IOException {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            u16(out, pc);
            send(0x62, 0, 0xffffffff, out.toByteArray());
        }

        private synchronized void send(int type, int error, int requestId, byte[] body) throws IOException {
            OutputStream out = mySocket.getOutputStream();
            ByteArrayOutputStream frame = new ByteArrayOutputStream();
            frame.write(0x02);
            frame.write(0x02);
            u32(frame, body.length);
            frame.write(type);
            frame.write(error);
            u32(frame, requestId);
            frame.writeBytes(body);
            out.write(frame.toByteArray());
            out.flush();
        }

        private static byte[] checkpointBody(int number, boolean hit, int start, int end) {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            u32(out, number);
            out.write(hit ? 1 : 0);
            u16(out, start);
            u16(out, end);
            out.write(1);                                        // stop
            out.write(1);                                        // enabled
            out.write(4);                                        // op: exec
            out.write(0);                                        // temporary
            u32(out, 0);                                         // hit count
            u32(out, 0);                                         // ignore count
            out.write(0);                                        // has condition
            out.write(0);                                        // memspace
            return out.toByteArray();
        }

        private static void registerDescriptor(ByteArrayOutputStream out, int id, int bits, String name) {
            out.write(3 + name.length());                        // item size (excl. this byte)
            out.write(id);
            out.write(bits);
            out.write(name.length());
            out.writeBytes(name.getBytes(java.nio.charset.StandardCharsets.US_ASCII));
        }

        private static void registerValue(ByteArrayOutputStream out, int id, int value) {
            out.write(3);
            out.write(id);
            u16(out, value);
        }

        private static void u16(ByteArrayOutputStream out, int value) {
            out.write(value & 0xff);
            out.write((value >> 8) & 0xff);
        }

        private static void u32(ByteArrayOutputStream out, int value) {
            out.write(value & 0xff);
            out.write((value >> 8) & 0xff);
            out.write((value >> 16) & 0xff);
            out.write((value >> 24) & 0xff);
        }

        @Override
        public void close() throws IOException {
            myServerSocket.close();
            Socket socket = mySocket;
            if (socket != null) {
                socket.close();
            }
        }
    }
}
