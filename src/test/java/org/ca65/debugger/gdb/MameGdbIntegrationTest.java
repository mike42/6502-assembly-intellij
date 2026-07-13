package org.ca65.debugger.gdb;

import org.ca65.debugger.backend.DebuggerBackend;
import org.ca65.debugger.backend.Mos6502Registers;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.junit.After;
import org.junit.Assume;
import org.junit.Before;
import org.junit.Test;

import java.io.File;
import java.io.IOException;
import java.net.ServerSocket;
import java.nio.file.Files;
import java.util.Map;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

/**
 * Drives a real MAME gdbstub end to end with the 8bit-table-tennis NES
 * homebrew. Skipped unless the environment opts in:
 *
 * <pre>MAME_GDB_IT=1 ./gradlew test --tests 'org.ca65.debugger.gdb.MameGdbIntegrationTest'</pre>
 *
 * Needs {@code mame} on the PATH, a display, and the ROM built at
 * 8bit-table-tennis/build/table_tennis.nes (run its build.sh). Uses the
 * famicomo machine: the nes/famicom machines' rp2a03g CPU is missing from
 * the gdbstub register map and fatalerrors.
 */
public class MameGdbIntegrationTest {
    /** nmi handler entry ($8058 per the label file); fires every frame. */
    private static final int NMI_ADDRESS = 0x8058;
    private static final File ROM = new File("8bit-table-tennis/build/table_tennis.nes");

    private Process myMame;
    private GdbDebuggerBackend myBackend;
    private final BlockingQueue<DebuggerBackend.StopEvent> myStops = new LinkedBlockingQueue<>();

    @Before
    public void setUp() throws Exception {
        Assume.assumeTrue("set MAME_GDB_IT=1 to run", "1".equals(System.getenv("MAME_GDB_IT")));
        Assume.assumeTrue("ROM not built: " + ROM, ROM.isFile());

        int port;
        try (ServerSocket socket = new ServerSocket(0)) {
            port = socket.getLocalPort();
        }
        File scratch = Files.createTempDirectory("mame-gdb-it").toFile();
        myMame = new ProcessBuilder("mame", "famicomo",
                "-debug", "-debugger", "gdbstub",
                "-debugger_port", String.valueOf(port), "-debugger_host", "127.0.0.1",
                "-window", "-nomouse",
                "-cart", ROM.getAbsolutePath())
                .directory(scratch)
                .redirectErrorStream(true)
                .redirectOutput(new File(scratch, "mame.log"))
                .start();

        myBackend = GdbDebuggerBackend.connect("127.0.0.1", port, new DebuggerBackend.Listener() {
            @Override
            public void onStopped(DebuggerBackend.@NotNull StopEvent event) {
                myStops.add(event);
            }

            @Override
            public void onDisconnected(@Nullable Throwable cause) {
            }
        }, 60_000);
    }

    @After
    public void tearDown() throws Exception {
        if (myBackend != null && !myBackend.isClosed()) {
            myBackend.terminate();
        }
        if (myMame != null && !myMame.waitFor(10, TimeUnit.SECONDS)) {
            myMame.destroyForcibly();
        }
    }

    @Test
    public void fullSession() throws Exception {
        assertEquals("MAME gdbstub (m6502)", myBackend.getDescription());

        // Breakpoint before the program starts; hit once NMI is enabled.
        int id = myBackend.setBreakpoint(NMI_ADDRESS, false);
        myBackend.startProgram();
        DebuggerBackend.StopEvent hit = myStops.poll(30, TimeUnit.SECONDS);
        assertNotNull("nmi breakpoint never fired", hit);
        assertEquals(DebuggerBackend.StopReason.BREAKPOINT, hit.reason());
        assertEquals(id, hit.breakpointId());
        assertEquals(NMI_ADDRESS, hit.pc());

        // Registers under canonical names, SP as the 8-bit low byte.
        Map<String, Integer> registers = myBackend.readRegisters();
        assertEquals(Integer.valueOf(NMI_ADDRESS), registers.get(Mos6502Registers.PC));
        int sp = registers.get(Mos6502Registers.SP);
        assertTrue("SP should be the 8-bit stack pointer: " + sp, sp >= 0 && sp <= 0xff);

        // Program-space reads match the ROM file (PRG file offset =
        // address - $8000 + $10 for the iNES header).
        byte[] live = myBackend.readMemory(NMI_ADDRESS, NMI_ADDRESS + 7);
        byte[] rom = Files.readAllBytes(ROM.toPath());
        byte[] expected = new byte[8];
        System.arraycopy(rom, NMI_ADDRESS - 0x8000 + 0x10, expected, 0, 8);
        assertArrayEquals(expected, live);

        // Single step from the breakpoint address.
        myBackend.stepInstruction();
        DebuggerBackend.StopEvent step = myStops.poll(10, TimeUnit.SECONDS);
        assertNotNull(step);
        assertEquals(DebuggerBackend.StopReason.STEP, step.reason());
        assertTrue("PC should advance past the nmi entry", step.pc() != NMI_ADDRESS);

        // Resume runs a whole frame to the next NMI.
        myBackend.resume();
        DebuggerBackend.StopEvent nextFrame = myStops.poll(10, TimeUnit.SECONDS);
        assertNotNull(nextFrame);
        assertEquals(DebuggerBackend.StopReason.BREAKPOINT, nextFrame.reason());

        // The open design question: resuming with PC exactly on the armed
        // breakpoint must execute a full frame, not re-trigger in place.
        // A throttled frame is ~16 ms; an in-place re-trigger is sub-ms.
        assertEquals(NMI_ADDRESS, nextFrame.pc());
        long before = System.nanoTime();
        myBackend.resume();
        DebuggerBackend.StopEvent again = myStops.poll(10, TimeUnit.SECONDS);
        long elapsedMillis = (System.nanoTime() - before) / 1_000_000;
        assertNotNull(again);
        assertEquals(DebuggerBackend.StopReason.BREAKPOINT, again.reason());
        assertTrue("suspiciously fast re-stop (" + elapsedMillis + " ms): continue from a "
                + "breakpoint PC appears to re-trigger in place", elapsedMillis >= 3);

        // Memory write while stopped, read back.
        myBackend.writeMemory(0x00f0, new byte[]{(byte) 0xab});
        assertEquals((byte) 0xab, myBackend.readMemory(0x00f0, 0x00f0)[0]);

        // Register write, read back.
        myBackend.writeRegister(Mos6502Registers.A, 0x42);
        assertEquals(Integer.valueOf(0x42), myBackend.readRegisters().get(Mos6502Registers.A));

        // Mid-run breakpoint change (stop-modify-go) and pause.
        myBackend.removeBreakpoint(id);
        myBackend.resume();
        int lateBreakpoint = myBackend.setBreakpoint(NMI_ADDRESS, false);   // while running
        DebuggerBackend.StopEvent lateHit = myStops.poll(10, TimeUnit.SECONDS);
        assertNotNull("breakpoint armed mid-run never fired", lateHit);
        assertEquals(DebuggerBackend.StopReason.BREAKPOINT, lateHit.reason());
        myBackend.removeBreakpoint(lateBreakpoint);

        myBackend.resume();
        Thread.sleep(100);
        myBackend.pause();
        DebuggerBackend.StopEvent paused = myStops.poll(10, TimeUnit.SECONDS);
        assertNotNull(paused);
        assertEquals(DebuggerBackend.StopReason.PAUSE, paused.reason());

        // terminate() sends 'k'; MAME must exit on its own.
        myBackend.terminate();
        assertTrue("MAME did not exit after the kill packet",
                myMame.waitFor(10, TimeUnit.SECONDS));
    }
}
