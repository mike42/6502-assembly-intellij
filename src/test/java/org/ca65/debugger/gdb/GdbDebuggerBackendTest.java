package org.ca65.debugger.gdb;

import org.ca65.debugger.backend.DebuggerBackend;
import org.ca65.debugger.backend.Mos6502Registers;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.util.Map;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;

/**
 * Backend semantics on top of the fake stub: canonical register naming,
 * stop-reason correlation, and the stop-modify-go dance for mid-run
 * breakpoint changes.
 */
public class GdbDebuggerBackendTest {
    private FakeGdbStubServer myServer;
    private GdbDebuggerBackend myBackend;
    private final BlockingQueue<DebuggerBackend.StopEvent> myStops = new LinkedBlockingQueue<>();

    @Before
    public void setUp() throws Exception {
        myServer = new FakeGdbStubServer();
        myBackend = GdbDebuggerBackend.connect("127.0.0.1", myServer.getPort(),
                new DebuggerBackend.Listener() {
                    @Override
                    public void onStopped(DebuggerBackend.@NotNull StopEvent event) {
                        myStops.add(event);
                    }

                    @Override
                    public void onDisconnected(@Nullable Throwable cause) {
                    }
                }, 5000);
    }

    @After
    public void tearDown() {
        myBackend.close();
        myServer.close();
    }

    @Test
    public void handshakeConsumesInitialStopSilently() throws Exception {
        assertEquals("MAME gdbstub (m6502)", myBackend.getDescription());
        assertNull(myStops.poll(100, TimeUnit.MILLISECONDS));
    }

    @Test
    public void registersAreCanonicalWithEightBitSp() throws Exception {
        Map<String, Integer> registers = myBackend.readRegisters();
        assertEquals(Integer.valueOf(0x0f), registers.get(Mos6502Registers.A));
        assertEquals(Integer.valueOf(0xb4), registers.get(Mos6502Registers.FL));
        // MAME reports sp as the $01xx bus address; canonical SP is the low byte.
        assertEquals(Integer.valueOf(0xfd), registers.get(Mos6502Registers.SP));
        assertEquals(Integer.valueOf(0x8000), registers.get(Mos6502Registers.PC));
        assertFalse(registers.containsKey("p"));
    }

    @Test
    public void writeSpRestoresBusAddress() throws Exception {
        myBackend.writeRegister(Mos6502Registers.SP, 0xf0);
        assertEquals("P4=f001", myServer.lastPacket("P"));
    }

    @Test
    public void breakpointHitCorrelatesById() throws Exception {
        int id = myBackend.setBreakpoint(0x8493, false);
        assertEquals(0x8493, id);
        myBackend.startProgram();
        myServer.sendStop(0x8493, 0xf9);
        DebuggerBackend.StopEvent event = myStops.poll(5, TimeUnit.SECONDS);
        assertNotNull(event);
        assertEquals(DebuggerBackend.StopReason.BREAKPOINT, event.reason());
        assertEquals(0x8493, event.breakpointId());
        assertEquals(0x8493, event.pc());
    }

    @Test
    public void stepReportsStepReason() throws Exception {
        myServer.stopOnStep(0x8001, 0xfd);
        myBackend.stepInstruction();
        DebuggerBackend.StopEvent event = myStops.poll(5, TimeUnit.SECONDS);
        assertNotNull(event);
        assertEquals(DebuggerBackend.StopReason.STEP, event.reason());
        assertEquals(0x8001, event.pc());
    }

    @Test
    public void stepOntoBreakpointAddressIsStillAStep() throws Exception {
        myBackend.setBreakpoint(0x8001, false);
        myServer.stopOnStep(0x8001, 0xfd);
        myBackend.stepInstruction();
        DebuggerBackend.StopEvent event = myStops.poll(5, TimeUnit.SECONDS);
        assertNotNull(event);
        assertEquals(DebuggerBackend.StopReason.STEP, event.reason());
    }

    @Test
    public void pauseReportsPauseReason() throws Exception {
        myBackend.startProgram();
        myBackend.pause();
        DebuggerBackend.StopEvent event = myStops.poll(5, TimeUnit.SECONDS);
        assertNotNull(event);
        assertEquals(DebuggerBackend.StopReason.PAUSE, event.reason());
    }

    @Test
    public void unattributedStopIsUnknown() throws Exception {
        myBackend.startProgram();
        myServer.sendStop(0xc123, 0xfd);   // no breakpoint armed there
        DebuggerBackend.StopEvent event = myStops.poll(5, TimeUnit.SECONDS);
        assertNotNull(event);
        assertEquals(DebuggerBackend.StopReason.UNKNOWN, event.reason());
    }

    @Test
    public void midRunBreakpointUsesStopModifyGo() throws Exception {
        myBackend.startProgram();
        // While "running" (a 'c' is outstanding), arming a breakpoint must
        // interrupt (fake answers 0x03 with a stop), send Z0, and continue;
        // the intermediate stop must not surface.
        int id = myBackend.setBreakpoint(0x9000, false);
        assertEquals(0x9000, id);
        assertEquals("Z0,9000,1", myServer.lastPacket("Z"));
        assertNull(myStops.poll(200, TimeUnit.MILLISECONDS));
        // The target is running again: a later stop still surfaces.
        myServer.sendStop(0x9000, 0xfd);
        DebuggerBackend.StopEvent event = myStops.poll(5, TimeUnit.SECONDS);
        assertNotNull(event);
        assertEquals(DebuggerBackend.StopReason.BREAKPOINT, event.reason());
    }

    @Test
    public void temporaryBreakpointDropsFromBookkeepingWhenHit() throws Exception {
        myBackend.setBreakpoint(0x8500, true);
        myBackend.resume();
        myServer.sendStop(0x8500, 0xfd);
        DebuggerBackend.StopEvent event = myStops.poll(5, TimeUnit.SECONDS);
        assertNotNull(event);
        assertEquals(DebuggerBackend.StopReason.BREAKPOINT, event.reason());
        // The follow-up removal sends the z0 (the stub never auto-removes).
        myBackend.removeBreakpoint(0x8500);
        assertEquals("z0,8500,1", myServer.lastPacket("z"));
        // A later stop at the same address is no longer a breakpoint hit.
        myBackend.resume();
        myServer.sendStop(0x8500, 0xfd);
        DebuggerBackend.StopEvent second = myStops.poll(5, TimeUnit.SECONDS);
        assertNotNull(second);
        assertEquals(DebuggerBackend.StopReason.UNKNOWN, second.reason());
    }
}
