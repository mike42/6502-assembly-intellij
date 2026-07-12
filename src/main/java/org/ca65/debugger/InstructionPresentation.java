package org.ca65.debugger;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * The decoded instruction at the stop's PC, ready for display: ca65-style
 * disassembly, the addressing mode's name, the mode-specific operand
 * annotation (index register and computed address, pointer resolution,
 * branch target), and — for data instructions only — the byte currently at
 * the effective address. Control-flow targets carry no value: they point
 * at code. {@code directDataAccess} is true for a plain zero-page/absolute
 * data instruction, i.e. when the operand symbol IS the variable being
 * accessed (as opposed to serving as a base address or jump target).
 */
public record InstructionPresentation(@NotNull String disassembly, @NotNull String modeName,
                                      @Nullable String annotation,
                                      @Nullable Integer effectiveAddress,
                                      @Nullable Integer effectiveValue,
                                      boolean directDataAccess) {
}
