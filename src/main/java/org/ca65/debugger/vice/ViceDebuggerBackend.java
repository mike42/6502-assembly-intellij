package org.ca65.debugger.vice;

import com.intellij.openapi.diagnostic.Logger;
import org.ca65.debugger.backend.DebuggerBackend;
import org.ca65.debugger.backend.Mos6502Registers;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * {@link DebuggerBackend} over the VICE binary monitor. The wire protocol
 * lives in {@link ViceBinaryMonitorClient}; this class hides VICE's
 * behavioral quirks behind the backend contract:
 *
 * <ul>
 * <li>VICE emits STOPPED/RESUMED chatter during autostart, reset and around
 *     every mid-run command. A stop reaches the listener only when it is
 *     (a) preceded by a checkpoint-hit event, (b) the completion of a step
 *     request, or (c) an explicit pause; everything else is transient.</li>
 * <li>Every command traps the emulator into the monitor; mid-run breakpoint
 *     changes are followed by EXIT to keep the machine running.</li>
 * <li>Resuming or stepping while an enabled exec checkpoint sits at the
 *     current PC — without it having just fired — stalls VICE, so every
 *     such continuation first steps off the checkpoint, GDB-style.</li>
 * </ul>
 */
public class ViceDebuggerBackend implements DebuggerBackend, ViceBinaryMonitorClient.EventListener {
    private static final Logger LOG = Logger.getInstance(ViceDebuggerBackend.class);
    private static final int OPCODE_RTS = 0x60;
    private static final int OPCODE_RTI = 0x40;

    private final Listener myListener;
    private final String myBinaryPath;

    private volatile ViceBinaryMonitorClient myClient;
    private volatile String myDescription = "VICE";

    // Stop-filtering state
    private final AtomicInteger myLastHitCheckpoint = new AtomicInteger(-1);
    private volatile boolean myStepPending;
    private volatile boolean myPauseRequested;
    /** Whether the target is logically running from the caller's point of view. */
    private volatile boolean myRunning;
    /** Set while a step-off is in flight; captures its stop event. */
    private volatile CompletableFuture<Integer> myStepOffStop;

    /** Address of each armed checkpoint, for the step-off logic. */
    private final Map<Integer, Integer> myCheckpointAddress = new ConcurrentHashMap<>();
    private final Set<Integer> myTemporary = ConcurrentHashMap.newKeySet();

    private ViceDebuggerBackend(@NotNull Listener listener, @NotNull String binaryPath) {
        myListener = listener;
        myBinaryPath = binaryPath;
    }

    /** Connects with retry while the emulator starts up. */
    @NotNull
    public static ViceDebuggerBackend connect(@NotNull String host, int port, @NotNull Listener listener,
                                              long timeoutMillis, @NotNull String binaryPath) throws IOException {
        ViceDebuggerBackend backend = new ViceDebuggerBackend(listener, binaryPath);
        backend.myClient = ViceBinaryMonitorClient.connect(host, port, backend, timeoutMillis);
        try {
            backend.myDescription = "VICE " + backend.myClient.getViceVersion();
            backend.myClient.fetchAvailableRegisters();
        } catch (IOException e) {
            backend.myClient.close();
            throw e;
        }
        return backend;
    }

    @Override
    public @NotNull String getDescription() {
        return myDescription;
    }

    @Override
    public boolean supportsBreakpointConditions() {
        return true;
    }

    @Override
    public boolean supportsStepOut() {
        return true;
    }

    @Override
    public void startProgram() throws IOException {
        myRunning = true;
        myClient.autostart(myBinaryPath, true);
        myClient.exitMonitor();
    }

    @Override
    public void terminate() {
        ViceBinaryMonitorClient client = myClient;
        try {
            // Mandatory hygiene: a checkpoint firing with no client attached
            // wedges VICE in its own monitor. Delete everything, then quit.
            for (Integer number : List.copyOf(myCheckpointAddress.keySet())) {
                client.deleteCheckpoint(number);
            }
            client.quit();
        } catch (IOException ignored) {
            // the process handler will kill the emulator regardless
        } finally {
            client.close();
        }
    }

    // ---- inspection -------------------------------------------------------

    @Override
    public @NotNull Map<String, Integer> readRegisters() throws IOException {
        // VICE already reports the canonical A/X/Y/SP/PC/FL names, plus
        // machine extras (6510 port, raster position) passed through as-is.
        return myClient.getRegisters();
    }

    @Override
    public void writeRegister(@NotNull String name, int value) throws IOException {
        myClient.setRegister(name, value);
    }

    @Override
    public byte @NotNull [] readMemory(int start, int endInclusive) throws IOException {
        return myClient.readMemory(start, endInclusive);
    }

    @Override
    public void writeMemory(int start, byte @NotNull [] data) throws IOException {
        myClient.writeMemory(start, data);
    }

    // ---- breakpoints ------------------------------------------------------

    @Override
    public int setBreakpoint(int address, boolean temporary) throws IOException {
        boolean wasRunning = myRunning;
        ViceBinaryMonitorClient.CheckpointInfo checkpoint =
                myClient.setCheckpoint(address, address, ViceBinaryMonitorClient.OP_EXEC, temporary);
        myCheckpointAddress.put(checkpoint.number(), address);
        if (temporary) {
            myTemporary.add(checkpoint.number());
        }
        if (wasRunning) {
            myClient.exitMonitor();   // the command trapped the machine; keep it running
        }
        return checkpoint.number();
    }

    @Override
    public void removeBreakpoint(int id) throws IOException {
        myCheckpointAddress.remove(id);
        myTemporary.remove(id);
        boolean wasRunning = myRunning;
        myClient.deleteCheckpoint(id);   // tolerates an already-gone checkpoint
        if (wasRunning) {
            myClient.exitMonitor();
        }
    }

    @Override
    public void setBreakpointCondition(int id, @NotNull String expression) throws IOException {
        boolean wasRunning = myRunning;
        myClient.setCondition(id, expression);
        if (wasRunning) {
            myClient.exitMonitor();
        }
    }

    // ---- execution control -------------------------------------------------

    @Override
    public void resume() throws IOException {
        Integer atPc = checkpointAt(currentPc());
        if (atPc != null) {
            int landedPc = stepOff(atPc);
            Integer landed = checkpointAt(landedPc);
            if (landed != null) {
                // The single step already reached another breakpoint (or a
                // step/run-to-cursor target); resuming would stall on it.
                if (myTemporary.remove(landed)) {
                    myCheckpointAddress.remove(landed);
                    myClient.deleteCheckpoint(landed);   // never fired; delete explicitly
                }
                stopped(new StopEvent(StopReason.BREAKPOINT, landedPc, landed));
                return;
            }
        }
        myRunning = true;
        myClient.exitMonitor();
    }

    @Override
    public void stepInstruction() throws IOException {
        Integer atPc = checkpointAt(currentPc());
        if (atPc != null) {
            int landedPc = stepOff(atPc);   // the step-off is the whole step
            stopped(new StopEvent(StopReason.STEP, landedPc, -1));
            return;
        }
        myRunning = true;
        myStepPending = true;
        myClient.advanceInstructions(false, 1);
    }

    @Override
    public void stepOut() throws IOException {
        int pc = currentPc();
        Integer atPc = checkpointAt(pc);
        if (atPc != null) {
            int opcode = myClient.readMemory(pc, pc)[0] & 0xff;
            int landedPc = stepOff(atPc);
            if (opcode == OPCODE_RTS || opcode == OPCODE_RTI) {
                stopped(new StopEvent(StopReason.STEP, landedPc, -1));   // already out
                return;
            }
            Integer landed = checkpointAt(landedPc);
            if (landed != null) {
                stopped(new StopEvent(StopReason.BREAKPOINT, landedPc, landed));
                return;
            }
        }
        myRunning = true;
        myStepPending = true;
        myClient.executeUntilReturn();
    }

    @Override
    public void pause() throws IOException {
        myPauseRequested = true;
        // There is no pause command: any command traps the emulator into
        // the monitor and it stays stopped until EXIT.
        myClient.getRegisters();
    }

    // ---- step-off machinery -------------------------------------------------

    private int currentPc() throws IOException {
        return myClient.getRegisters().getOrDefault(Mos6502Registers.PC, 0);
    }

    @Nullable
    private Integer checkpointAt(int pc) {
        for (Map.Entry<Integer, Integer> entry : myCheckpointAddress.entrySet()) {
            if (entry.getValue() == pc) {
                return entry.getKey();
            }
        }
        return null;
    }

    /**
     * Executes one instruction with the given checkpoint disabled and blocks
     * until the machine stops again, returning the new PC. Resuming or
     * stepping while an enabled exec checkpoint sits at the current PC —
     * without it having just fired — stalls VICE: the resume consumes no
     * instructions and later stops lose their checkpoint-hit correlation
     * (verified against x64sc 3.10). So every continuation from such a
     * position first steps off, GDB-style.
     */
    private int stepOff(int checkpointNumber) throws IOException {
        CompletableFuture<Integer> stoppedAt = new CompletableFuture<>();
        myStepOffStop = stoppedAt;
        try {
            myClient.toggleCheckpoint(checkpointNumber, false);
            myClient.advanceInstructions(false, 1);
            return stoppedAt.get(15, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException("Interrupted while stepping off a breakpoint", e);
        } catch (java.util.concurrent.ExecutionException | TimeoutException e) {
            throw new IOException("No stop after stepping off a breakpoint", e);
        } finally {
            myStepOffStop = null;
            try {
                myClient.toggleCheckpoint(checkpointNumber, true);
            } catch (IOException e) {
                LOG.warn("Could not re-enable checkpoint " + checkpointNumber, e);
            }
        }
    }

    // ---- monitor events (on the client reader thread) -----------------------

    @Override
    public void onCheckpoint(@NotNull ViceBinaryMonitorClient.CheckpointInfo info) {
        if (info.hit()) {
            myLastHitCheckpoint.set(info.number());
        }
    }

    @Override
    public void onStopped(int pc) {
        CompletableFuture<Integer> stepOff = myStepOffStop;
        if (stepOff != null) {
            stepOff.complete(pc);   // consumed by the blocked step-off
            return;
        }
        int hit = myLastHitCheckpoint.getAndSet(-1);
        if (hit >= 0 && myCheckpointAddress.containsKey(hit)) {
            if (myTemporary.remove(hit)) {
                myCheckpointAddress.remove(hit);   // VICE deletes a fired temporary itself
            }
            stopped(new StopEvent(StopReason.BREAKPOINT, pc, hit));
        } else if (myStepPending) {
            stopped(new StopEvent(StopReason.STEP, pc, -1));
        } else if (myPauseRequested) {
            stopped(new StopEvent(StopReason.PAUSE, pc, -1));
        }
        // else: transient stop (autostart/reset chatter); VICE resumes it itself
    }

    @Override
    public void onJam(int pc) {
        stopped(new StopEvent(StopReason.JAM, pc, -1));
    }

    @Override
    public void onDisconnected(@Nullable Throwable cause) {
        myListener.onDisconnected(cause);
    }

    /** Every surfaced stop resets the one-shot request flags. */
    private void stopped(@NotNull StopEvent event) {
        myRunning = false;
        myStepPending = false;
        myPauseRequested = false;
        myListener.onStopped(event);
    }

    // ---- lifecycle -----------------------------------------------------------

    @Override
    public boolean isClosed() {
        return myClient.isClosed();
    }

    @Override
    public void close() {
        myClient.close();
    }
}
