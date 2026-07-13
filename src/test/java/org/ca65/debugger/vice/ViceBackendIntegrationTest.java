package org.ca65.debugger.vice;

import org.ca65.debugger.backend.DebuggerBackend;
import org.ca65.debugger.backend.Mos6502Registers;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.junit.After;
import org.junit.Assume;
import org.junit.Before;
import org.junit.Test;

import java.io.File;
import java.net.ServerSocket;
import java.nio.file.Files;
import java.util.Map;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

/**
 * Drives a real VICE (x64sc) binary monitor end to end through the
 * {@link ViceDebuggerBackend} — the regression net for refactors of the
 * backend abstraction. Skipped unless the environment opts in:
 *
 * <pre>VICE_MONITOR_IT=1 ./gradlew test --tests 'org.ca65.debugger.vice.ViceBackendIntegrationTest'</pre>
 *
 * Needs {@code x64sc} on the PATH, a display, and the hello-c64 sample
 * (CHROUT banner, then an endless counter loop at $081f).
 */
public class ViceBackendIntegrationTest {
    /** The "loop:" label ($081f per hello.lbl); executes continuously. */
    private static final int LOOP_ADDRESS = 0x081f;
    private static final File PRG = new File("debugger-research/hello-c64/hello.prg");

    private Process myVice;
    private ViceDebuggerBackend myBackend;
    private final BlockingQueue<DebuggerBackend.StopEvent> myStops = new LinkedBlockingQueue<>();

    @Before
    public void setUp() throws Exception {
        Assume.assumeTrue("set VICE_MONITOR_IT=1 to run", "1".equals(System.getenv("VICE_MONITOR_IT")));
        Assume.assumeTrue("sample not found: " + PRG, PRG.isFile());

        int port;
        try (ServerSocket socket = new ServerSocket(0)) {
            port = socket.getLocalPort();
        }
        File scratch = Files.createTempDirectory("vice-monitor-it").toFile();
        myVice = new ProcessBuilder("x64sc", "-sounddev", "dummy", "+confirmonexit",
                "-binarymonitor", "-binarymonitoraddress", "ip4://127.0.0.1:" + port)
                .directory(scratch)
                .redirectErrorStream(true)
                .redirectOutput(new File(scratch, "vice.log"))
                .start();

        myBackend = ViceDebuggerBackend.connect("127.0.0.1", port, new DebuggerBackend.Listener() {
            @Override
            public void onStopped(DebuggerBackend.@NotNull StopEvent event) {
                myStops.add(event);
            }

            @Override
            public void onDisconnected(@Nullable Throwable cause) {
            }
        }, 60_000, PRG.getAbsolutePath());
    }

    @After
    public void tearDown() throws Exception {
        if (myBackend != null && !myBackend.isClosed()) {
            myBackend.terminate();
        }
        if (myVice != null && !myVice.waitFor(10, TimeUnit.SECONDS)) {
            myVice.destroyForcibly();
        }
    }

    @Test
    public void fullSession() throws Exception {
        assertTrue(myBackend.getDescription().startsWith("VICE "));

        // Breakpoint before autostart; C64 boot + program load take a while.
        int id = myBackend.setBreakpoint(LOOP_ADDRESS, false);
        myBackend.startProgram();
        DebuggerBackend.StopEvent hit = myStops.poll(60, TimeUnit.SECONDS);
        assertNotNull("loop breakpoint never fired", hit);
        assertEquals(DebuggerBackend.StopReason.BREAKPOINT, hit.reason());
        assertEquals(id, hit.breakpointId());
        assertEquals(LOOP_ADDRESS, hit.pc());

        Map<String, Integer> registers = myBackend.readRegisters();
        assertEquals(Integer.valueOf(LOOP_ADDRESS), registers.get(Mos6502Registers.PC));
        assertTrue(registers.containsKey(Mos6502Registers.A));
        assertTrue(registers.containsKey(Mos6502Registers.FL));

        // Stepping with PC on the armed breakpoint exercises the step-off
        // dance (disable, advance one, re-enable).
        myBackend.stepInstruction();
        DebuggerBackend.StopEvent step = myStops.poll(10, TimeUnit.SECONDS);
        assertNotNull(step);
        assertEquals(DebuggerBackend.StopReason.STEP, step.reason());
        assertTrue("PC should advance past the loop head", step.pc() != LOOP_ADDRESS);

        // Resume completes the loop iteration back to the breakpoint.
        myBackend.resume();
        DebuggerBackend.StopEvent again = myStops.poll(10, TimeUnit.SECONDS);
        assertNotNull(again);
        assertEquals(DebuggerBackend.StopReason.BREAKPOINT, again.reason());
        assertEquals(LOOP_ADDRESS, again.pc());

        // The loop increments COUNTER ($02): resume and check it moves.
        int counterBefore = myBackend.readMemory(0x0002, 0x0002)[0] & 0xff;
        myBackend.resume();
        assertNotNull(myStops.poll(10, TimeUnit.SECONDS));
        int counterAfter = myBackend.readMemory(0x0002, 0x0002)[0] & 0xff;
        assertTrue("the loop should have incremented the counter",
                counterAfter != counterBefore);

        // Memory and register writes while stopped.
        myBackend.writeMemory(0x00f0, new byte[]{(byte) 0xab});
        assertEquals((byte) 0xab, myBackend.readMemory(0x00f0, 0x00f0)[0]);
        myBackend.writeRegister(Mos6502Registers.A, 0x42);
        assertEquals(Integer.valueOf(0x42), myBackend.readRegisters().get(Mos6502Registers.A));

        // Free-run and pause.
        myBackend.removeBreakpoint(id);
        myBackend.resume();
        Thread.sleep(200);
        myBackend.pause();
        DebuggerBackend.StopEvent paused = myStops.poll(10, TimeUnit.SECONDS);
        assertNotNull(paused);
        assertEquals(DebuggerBackend.StopReason.PAUSE, paused.reason());

        // terminate() deletes checkpoints and quits VICE.
        myBackend.terminate();
        assertTrue("VICE did not exit after QUIT", myVice.waitFor(10, TimeUnit.SECONDS));
    }
}
