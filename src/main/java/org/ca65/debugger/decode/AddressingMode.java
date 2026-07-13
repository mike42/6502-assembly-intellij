package org.ca65.debugger.decode;

import org.jetbrains.annotations.NotNull;

/**
 * Addressing modes of the 6502 family. Each decoder maps opcodes onto
 * these; the display name is shown to the user next to decoded
 * instructions. Other CPUs (65816) will extend this set with their own
 * modes when they get a decoder.
 */
public enum AddressingMode {
    IMPLIED("implied", 1),
    ACCUMULATOR("accumulator", 1),
    IMMEDIATE("immediate", 2),
    ZERO_PAGE("zero page", 2),
    ZERO_PAGE_X("zero page,X", 2),
    ZERO_PAGE_Y("zero page,Y", 2),
    ABSOLUTE("absolute", 3),
    ABSOLUTE_X("absolute,X", 3),
    ABSOLUTE_Y("absolute,Y", 3),
    INDIRECT("(indirect)", 3),
    INDEXED_INDIRECT("(zero page,X)", 2),
    INDIRECT_INDEXED("(zero page),Y", 2),
    RELATIVE("relative", 2);

    private final String myDisplayName;
    private final int myInstructionLength;

    AddressingMode(@NotNull String displayName, int instructionLength) {
        myDisplayName = displayName;
        myInstructionLength = instructionLength;
    }

    @NotNull
    public String getDisplayName() {
        return myDisplayName;
    }

    /** Total instruction length in bytes, including the opcode. */
    public int getInstructionLength() {
        return myInstructionLength;
    }
}
