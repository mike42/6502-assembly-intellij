package org.ca65.debugger.gdb;

import com.intellij.openapi.diagnostic.Logger;
import org.ca65.debugger.backend.DebuggerBackend;
import org.ca65.debugger.backend.Mos6502Registers;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * {@link DebuggerBackend} over the GDB remote serial protocol, as spoken
 * by MAME's gdbstub. The wire protocol lives in {@link GdbRemoteClient};
 * this class maps the backend contract onto RSP semantics:
 *
 * <ul>
 * <li>Register names from MAME's m6502 target.xml (a/x/y/p/sp/pc) are
 *     translated to the canonical {@link Mos6502Registers} names. MAME
 *     reports sp as the 16-bit $01xx address; the canonical SP is the low
 *     byte.</li>
 * <li>Breakpoints are identified by address (Z0/z0 carry no id).</li>
 * <li>Breakpoint changes while the target runs use stop-modify-go: a raw
 *     0x03 interrupt, the mutation, then continue — the intermediate stop
 *     is backend-internal and never surfaced.</li>
 * <li>Stops always arrive as T05 (SIGTRAP); the reason is correlated
 *     client-side from what was requested last (step, pause, or the armed
 *     breakpoint set).</li>
 * </ul>
 */
public class GdbDebuggerBackend implements DebuggerBackend, GdbRemoteClient.Listener {
    private static final Logger LOG = Logger.getInstance(GdbDebuggerBackend.class);

    /** MAME m6502 target.xml register name → canonical name. */
    private static final Map<String, String> TO_CANONICAL = Map.of(
            "a", Mos6502Registers.A,
            "x", Mos6502Registers.X,
            "y", Mos6502Registers.Y,
            "p", Mos6502Registers.FL,
            "sp", Mos6502Registers.SP,
            "pc", Mos6502Registers.PC);
    private static final Map<String, String> FROM_CANONICAL = Map.of(
            Mos6502Registers.A, "a",
            Mos6502Registers.X, "x",
            Mos6502Registers.Y, "y",
            Mos6502Registers.FL, "p",
            Mos6502Registers.SP, "sp",
            Mos6502Registers.PC, "pc");

    private final Listener myListener;

    private volatile GdbRemoteClient myClient;
    private volatile String myDescription = "gdb stub";

    /** Addresses with an armed breakpoint (the address is the Z0 identity). */
    private final Set<Integer> myBreakpoints = ConcurrentHashMap.newKeySet();
    private final Set<Integer> myTemporary = ConcurrentHashMap.newKeySet();

    private volatile boolean myRunning;
    private volatile boolean myStepPending;
    private volatile boolean myPauseRequested;
    /** Set while a stop-modify-go is in flight; captures its stop reply. */
    private volatile CompletableFuture<GdbRemoteClient.StopReply> myInternalStop;

    private GdbDebuggerBackend(@NotNull Listener listener) {
        myListener = listener;
    }

    /** Connects with retry, runs the RSP handshake, and consumes the initial stop. */
    @NotNull
    public static GdbDebuggerBackend connect(@NotNull String host, int port, @NotNull Listener listener,
                                             long timeoutMillis) throws IOException {
        GdbDebuggerBackend backend = new GdbDebuggerBackend(listener);
        backend.myClient = GdbRemoteClient.connect(host, port, backend, timeoutMillis);
        try {
            // The initial stop state ("?") is the machine paused at reset,
            // before startProgram(); it is never surfaced to the listener.
            backend.myClient.handshake();
            backend.myDescription = "MAME gdbstub (" + backend.myClient.getArchitecture() + ")";
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
        // Z0 carries no condition. (qRcmd "bpset addr,{cond}" could, but
        // needs bpset output parsing and separate clear bookkeeping.)
        return false;
    }

    @Override
    public boolean supportsStepOut() {
        // The stub has no equivalent, and qRcmd "out" is unusable for
        // execution control: console commands do not arm the stop-reply
        // flag, so the resulting stop would never reach the client. The
        // debug process falls back to a return-address breakpoint.
        return false;
    }

    @Override
    public void startProgram() throws IOException {
        // The ROM was loaded via the launch command line and the machine
        // sits stopped at reset; starting is just the first continue.
        myRunning = true;
        myClient.sendContinue();
    }

    @Override
    public void terminate() {
        GdbRemoteClient client = myClient;
        client.kill();   // schedules MAME's exit; no reply comes
        client.close();
    }

    // ---- inspection -------------------------------------------------------

    @Override
    public @NotNull Map<String, Integer> readRegisters() throws IOException {
        Map<String, Integer> canonical = new LinkedHashMap<>();
        for (Map.Entry<String, Integer> entry : myClient.readRegisters().entrySet()) {
            String name = TO_CANONICAL.getOrDefault(entry.getKey(), entry.getKey());
            int value = entry.getValue();
            if (Mos6502Registers.SP.equals(name)) {
                value &= 0xff;   // MAME reports the sp as its $01xx bus address
            }
            canonical.put(name, value);
        }
        return canonical;
    }

    @Override
    public void writeRegister(@NotNull String name, int value) throws IOException {
        String gdbName = FROM_CANONICAL.get(name);
        if (gdbName == null) {
            throw new IOException("Unknown register: " + name);
        }
        if (Mos6502Registers.SP.equals(name)) {
            value = 0x0100 | (value & 0xff);
        }
        myClient.writeRegister(gdbName, value);
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
        whileStopped(() -> myClient.setBreakpoint(address));
        myBreakpoints.add(address);
        if (temporary) {
            myTemporary.add(address);
        }
        return address;
    }

    @Override
    public void removeBreakpoint(int id) throws IOException {
        myBreakpoints.remove(id);
        myTemporary.remove(id);
        // Always send the z0: MAME never auto-removes Z0 breakpoints, so
        // even a fired temporary (already dropped from the bookkeeping by
        // onStopReply) is still armed in the stub. A z0 for a breakpoint
        // the stub no longer knows is tolerated per the backend contract.
        try {
            whileStopped(() -> myClient.removeBreakpoint(id));
        } catch (GdbRemoteClient.GdbProtocolException e) {
            LOG.warn("z0 for an unknown breakpoint at " + id, e);
        }
    }

    @Override
    public void setBreakpointCondition(int id, @NotNull String expression) throws IOException {
        throw new IOException("Breakpoint conditions are not supported over the gdb stub");
    }

    private interface ProtocolAction {
        void run() throws IOException;
    }

    /**
     * Runs a mutation with the target stopped, transparently interrupting
     * and resuming a running target (stop-modify-go). The intermediate
     * stop is internal chatter and never surfaced; this also preserves
     * RSP's one-outstanding-request rule — a plain request while 'c' is
     * pending would race with the eventual stop reply.
     */
    private void whileStopped(@NotNull ProtocolAction action) throws IOException {
        if (!myRunning) {
            action.run();
            return;
        }
        CompletableFuture<GdbRemoteClient.StopReply> stopped = new CompletableFuture<>();
        myInternalStop = stopped;
        if (!myRunning) {
            // A real stop surfaced between the check and arming the future;
            // the target is stopped now, so mutate directly.
            myInternalStop = null;
            action.run();
            return;
        }
        try {
            myClient.interrupt();
            try {
                stopped.get(15, TimeUnit.SECONDS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new IOException("Interrupted while pausing the target", e);
            } catch (java.util.concurrent.ExecutionException | TimeoutException e) {
                throw new IOException("No stop after interrupting the target", e);
            }
            action.run();
        } finally {
            myInternalStop = null;
        }
        myClient.sendContinue();
    }

    // ---- execution control --------------------------------------------------

    @Override
    public void resume() throws IOException {
        // Continuing from a PC with a breakpoint on it: MAME's debugger
        // does not re-trigger on the instruction it is stopped at, so a
        // plain continue suffices — no VICE-style step-off (verified
        // against MAME 0.288; if a version regresses on this,
        // step-then-continue goes here).
        myRunning = true;
        myClient.sendContinue();
    }

    @Override
    public void stepInstruction() throws IOException {
        myRunning = true;
        myStepPending = true;
        myClient.sendStep();
    }

    @Override
    public void stepOut() throws IOException {
        throw new IOException("Step out is not supported over the gdb stub");
    }

    @Override
    public void pause() throws IOException {
        myPauseRequested = true;
        myClient.interrupt();   // answers the outstanding continue with a stop reply
    }

    // ---- stop replies (on the client reader thread) ---------------------------

    @Override
    public void onStopReply(GdbRemoteClient.@NotNull StopReply reply) {
        CompletableFuture<GdbRemoteClient.StopReply> internal = myInternalStop;
        if (internal != null) {
            internal.complete(reply);   // consumed by a blocked stop-modify-go
            return;
        }
        int pc = reply.registers().getOrDefault("pc", -1);
        StopEvent event;
        if (myPauseRequested) {
            event = new StopEvent(StopReason.PAUSE, pc, -1);
        } else if (myStepPending) {
            event = new StopEvent(StopReason.STEP, pc, -1);
        } else if (myBreakpoints.contains(pc)) {
            if (myTemporary.remove(pc)) {
                // Bookkeeping only — this thread delivers protocol replies
                // and must never issue requests itself. The debug process
                // follows up with removeBreakpoint() for the actual z0.
                myBreakpoints.remove(pc);
            }
            event = new StopEvent(StopReason.BREAKPOINT, pc, pc);
        } else {
            event = new StopEvent(StopReason.UNKNOWN, pc, -1);
        }
        myRunning = false;
        myStepPending = false;
        myPauseRequested = false;
        myListener.onStopped(event);
    }

    @Override
    public void onDisconnected(@Nullable Throwable cause) {
        myListener.onDisconnected(cause);
    }

    // ---- lifecycle -------------------------------------------------------------

    @Override
    public boolean isClosed() {
        return myClient.isClosed();
    }

    @Override
    public void close() {
        myClient.close();
    }
}
