package org.ca65.debugger.backend;

import java.util.List;

/**
 * Canonical names for the 6502 CPU registers as reported by every
 * {@link DebuggerBackend}, defined once so the presentation layer and the
 * per-emulator clients never disagree on spelling. Backends translate
 * whatever their protocol calls the registers into these names; anything
 * beyond this set is machine-specific and shown in the "Machine" group.
 */
public final class Mos6502Registers {
    public static final String A = "A";
    public static final String X = "X";
    public static final String Y = "Y";
    /** The 8-bit stack pointer low byte (the $01 page base is implied). */
    public static final String SP = "SP";
    public static final String PC = "PC";
    /** The processor status register (NV-BDIZC flags). */
    public static final String FL = "FL";

    /** The registers every 6502 backend must report, in display order. */
    public static final List<String> PRIMARY = List.of(A, X, Y, SP, PC, FL);

    private Mos6502Registers() {
    }
}
