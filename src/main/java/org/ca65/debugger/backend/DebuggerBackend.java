package org.ca65.debugger.backend;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.Closeable;
import java.io.IOException;
import java.util.Map;

/**
 * An established debug connection to an emulator, abstracting over the
 * wildly different remote-debug protocols retro emulators expose (VICE
 * binary monitor, GDB remote serial, …). The debug process programs
 * against this interface only; every protocol quirk — stop-event chatter,
 * step-off-breakpoint dances, "any command pauses the machine" semantics —
 * is the implementing backend's job to hide.
 *
 * <p>Threading: all methods block and are called on the debug process's
 * single sequential protocol executor, never on the EDT. {@link Listener}
 * callbacks may arrive on the backend's reader thread <em>or</em>
 * synchronously from within a method of this interface; the caller must
 * tolerate both (the debug process re-posts to its executor).
 *
 * <p>Inspection methods ({@link #readRegisters}, {@link #readMemory}, …)
 * are only meaningful while the target is stopped. Breakpoint methods may
 * be called at any time; when the target is running the backend keeps it
 * running (pausing and resuming internally if its protocol requires it).
 */
public interface DebuggerBackend extends Closeable {

    enum StopReason {
        /** An armed breakpoint fired; {@link StopEvent#breakpointId()} says which. */
        BREAKPOINT,
        /** A {@link #stepInstruction()} or {@link #stepOut()} completed. */
        STEP,
        /** A {@link #pause()} took effect. */
        PAUSE,
        /** The CPU executed a halting instruction. */
        JAM,
        /** The target stopped for a reason the backend could not attribute. */
        UNKNOWN
    }

    /**
     * A user-relevant stop. {@code breakpointId} is the id returned by
     * {@link #setBreakpoint} for the breakpoint that caused the stop, or
     * -1 when the reason is not {@link StopReason#BREAKPOINT}.
     */
    record StopEvent(@NotNull StopReason reason, int pc, int breakpointId) {
    }

    interface Listener {
        /**
         * The target stopped and the stop should be shown to the user.
         * Backends filter out their protocol's transient stop chatter
         * (e.g. around autostart or mid-run commands) before this fires.
         */
        void onStopped(@NotNull StopEvent event);

        void onDisconnected(@Nullable Throwable cause);
    }

    /** Human-readable connection description for the console, e.g. "VICE 3.10". */
    @NotNull
    String getDescription();

    /** Whether {@link #setBreakpointCondition} works on this backend. */
    boolean supportsBreakpointConditions();

    /** Whether {@link #stepOut()} works on this backend. */
    boolean supportsStepOut();

    /**
     * Starts the program under debug, with breakpoints already armed. The
     * target runs until a breakpoint fires or {@link #pause()} is called.
     */
    void startProgram() throws IOException;

    /**
     * Best-effort emulator shutdown at the end of the session; must not
     * throw. The emulator process is killed by its process handler
     * regardless, so this only needs to leave the protocol in a state the
     * emulator can die from cleanly.
     */
    void terminate();

    /** Current register values under their {@link Mos6502Registers} names. */
    @NotNull
    Map<String, Integer> readRegisters() throws IOException;

    void writeRegister(@NotNull String name, int value) throws IOException;

    /** Reads [start, endInclusive] from the CPU's view of memory, without side effects. */
    byte @NotNull [] readMemory(int start, int endInclusive) throws IOException;

    void writeMemory(int start, byte @NotNull [] data) throws IOException;

    /**
     * Arms an execution breakpoint and returns its backend-assigned id.
     * A temporary breakpoint is removed by the backend once it fires.
     */
    int setBreakpoint(int address, boolean temporary) throws IOException;

    /** Removing an already-gone breakpoint (e.g. a fired temporary) is not an error. */
    void removeBreakpoint(int id) throws IOException;

    /**
     * Attaches a condition in the backend's native expression syntax.
     * Only valid when {@link #supportsBreakpointConditions()} is true.
     */
    void setBreakpointCondition(int id, @NotNull String expression) throws IOException;

    // Execution control: completion is always reported via Listener.onStopped.

    void resume() throws IOException;

    void stepInstruction() throws IOException;

    /** Runs until the current subroutine returns. Only valid when {@link #supportsStepOut()} is true. */
    void stepOut() throws IOException;

    void pause() throws IOException;

    boolean isClosed();
}
