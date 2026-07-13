package org.ca65.debugger.gdb;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

/**
 * Exercises RSP framing, the handshake, and stop-reply routing against a
 * scripted server answering like MAME's gdbstub for the m6502 register map
 * (packet behavior as verified in the MAME source: ack mode only, qXfer
 * feature reads, T05 stop replies carrying sp and pc, E01 errors).
 */
public class GdbRemoteClientTest {
    private FakeGdbStubServer myServer;
    private GdbRemoteClient myClient;
    private final BlockingQueue<GdbRemoteClient.StopReply> myStops = new LinkedBlockingQueue<>();
    private final BlockingQueue<Object> myDisconnects = new LinkedBlockingQueue<>();

    @Before
    public void setUp() throws Exception {
        myServer = new FakeGdbStubServer();
        myClient = GdbRemoteClient.connect("127.0.0.1", myServer.getPort(),
                new GdbRemoteClient.Listener() {
                    @Override
                    public void onStopReply(GdbRemoteClient.@NotNull StopReply reply) {
                        myStops.add(reply);
                    }

                    @Override
                    public void onDisconnected(@Nullable Throwable cause) {
                        myDisconnects.add(cause == null ? "clean" : cause);
                    }
                }, 5000);
    }

    @After
    public void tearDown() {
        myClient.close();
        myServer.close();
    }

    @Test
    public void handshakeFetchesTargetXmlAndInitialStop() throws Exception {
        GdbRemoteClient.StopReply stop = myClient.handshake();
        assertEquals(5, stop.signal());
        assertEquals("m6502", myClient.getArchitecture());
        List<GdbRemoteClient.GdbRegister> layout = myClient.getRegisterLayout();
        assertEquals(6, layout.size());
        assertEquals("a", layout.get(0).name());
        assertEquals(8, layout.get(0).bitsize());
        assertEquals("pc", layout.get(5).name());
        assertEquals(16, layout.get(5).bitsize());
        // The initial "?" reply carries sp and pc, little-endian.
        assertEquals(Integer.valueOf(0x01fd), stop.registers().get("sp"));
        assertEquals(Integer.valueOf(0x8000), stop.registers().get("pc"));
        // Framing: every request the server saw had a valid checksum.
        assertEquals(0, myServer.badChecksums());
    }

    @Test
    public void handshakeConcatenatesChunkedTargetXml() throws Exception {
        myServer.chunkTargetXml(64);
        myClient.handshake();
        assertEquals("m6502", myClient.getArchitecture());
        assertEquals(6, myClient.getRegisterLayout().size());
    }

    @Test
    public void readRegistersDecodesLittleEndianWidths() throws Exception {
        myClient.handshake();
        Map<String, Integer> registers = myClient.readRegisters();
        assertEquals(Integer.valueOf(0x0f), registers.get("a"));
        assertEquals(Integer.valueOf(0x05), registers.get("x"));
        assertEquals(Integer.valueOf(0x00), registers.get("y"));
        assertEquals(Integer.valueOf(0xb4), registers.get("p"));
        assertEquals(Integer.valueOf(0x01fd), registers.get("sp"));
        assertEquals(Integer.valueOf(0x8000), registers.get("pc"));
    }

    @Test
    public void writeRegisterUsesRegnumAndLittleEndianHex() throws Exception {
        myClient.handshake();
        myClient.writeRegister("pc", 0x8493);
        assertEquals("P5=9384", myServer.lastPacket("P"));
        myClient.writeRegister("a", 0x2a);
        assertEquals("P0=2a", myServer.lastPacket("P"));
    }

    @Test
    public void memoryReadAndWriteRoundTrip() throws Exception {
        myClient.handshake();
        myServer.setMemory(0x0002, new byte[]{0x10, 0x62});
        byte[] data = myClient.readMemory(0x0002, 0x0003);
        assertArrayEquals(new byte[]{0x10, 0x62}, data);
        myClient.writeMemory(0x0002, new byte[]{(byte) 0xab, 0x01});
        assertEquals("M2,2:ab01", myServer.lastPacket("M"));
    }

    @Test
    public void breakpointPacketsUseAddressIdentity() throws Exception {
        myClient.handshake();
        myClient.setBreakpoint(0x8493);
        assertEquals("Z0,8493,1", myServer.lastPacket("Z"));
        myClient.removeBreakpoint(0x8493);
        assertEquals("z0,8493,1", myServer.lastPacket("z"));
    }

    @Test
    public void continueDeliversDelayedStopToListener() throws Exception {
        myClient.handshake();
        myClient.sendContinue();
        assertNull(myStops.poll(100, TimeUnit.MILLISECONDS));   // still running
        myServer.sendStop(0x8493, 0xf9);
        GdbRemoteClient.StopReply stop = myStops.poll(5, TimeUnit.SECONDS);
        assertNotNull(stop);
        assertEquals(Integer.valueOf(0x8493), stop.registers().get("pc"));
        assertEquals(Integer.valueOf(0x01f9), stop.registers().get("sp"));
        assertNull(stop.watchKind());
    }

    @Test
    public void stepGetsImmediateStopReply() throws Exception {
        myClient.handshake();
        myServer.stopOnStep(0x8001, 0xfd);
        myClient.sendStep();
        GdbRemoteClient.StopReply stop = myStops.poll(5, TimeUnit.SECONDS);
        assertNotNull(stop);
        assertEquals(Integer.valueOf(0x8001), stop.registers().get("pc"));
    }

    @Test
    public void interruptSendsRawByteAndRoutesStop() throws Exception {
        myClient.handshake();
        myClient.sendContinue();
        myClient.interrupt();
        // The fake answers a raw 0x03 with a stop reply, like MAME does
        // for the pending continue.
        GdbRemoteClient.StopReply stop = myStops.poll(5, TimeUnit.SECONDS);
        assertNotNull(stop);
        assertEquals(5, stop.signal());
    }

    @Test
    public void watchpointStopCarriesKindAndAddress() throws Exception {
        myClient.handshake();
        myClient.sendContinue();
        myServer.sendRaw("T05watch:00fe;04:f901;05:9384;");
        GdbRemoteClient.StopReply stop = myStops.poll(5, TimeUnit.SECONDS);
        assertNotNull(stop);
        assertEquals("watch", stop.watchKind());
        assertEquals(0xfe, stop.watchAddress());
    }

    @Test
    public void errorReplyBecomesProtocolException() throws Exception {
        myClient.handshake();
        myServer.failNext();
        GdbRemoteClient.GdbProtocolException e = assertThrows(
                GdbRemoteClient.GdbProtocolException.class,
                () -> myClient.readMemory(0xfff0, 0xffff));
        assertEquals("E01", e.errorReply);
    }

    @Test
    public void registerAccessBeforeTargetXmlFails() {
        // MAME answers g/G/p/P with E01 until target.xml was fetched; the
        // client enforces the ordering itself by requiring handshake().
        assertTrue(myClient.getRegisterLayout().isEmpty());
        assertThrows(IOException.class, () -> myClient.readRegisters());
    }

    @Test
    public void monitorCommandHexEncodesAndDecodes() throws Exception {
        myClient.handshake();
        String output = myClient.monitorCommand("help");
        assertEquals("qRcmd,68656c70", myServer.lastPacket("q"));
        assertEquals("ok\n", output);
    }

    @Test
    public void serverCloseFiresDisconnect() throws Exception {
        myClient.handshake();
        myServer.close();
        assertNotNull(myDisconnects.poll(5, TimeUnit.SECONDS));
    }

}
