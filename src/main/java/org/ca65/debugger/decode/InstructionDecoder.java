package org.ca65.debugger.decode;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.util.Map;

/**
 * Decodes machine instructions for one CPU family. Instruction encodings
 * are CPU-specific — the 65816 in particular has different opcodes, widths
 * and extra addressing modes — so each supported CPU gets its own
 * implementation, selected via {@link InstructionDecoders#forCpuName}.
 */
public interface InstructionDecoder {
    /** A decoded instruction; {@code operand} is the raw operand value (low | high << 8). */
    record DecodedInstruction(@NotNull String mnemonic, @NotNull AddressingMode mode, int operand) {
        public int length() {
            return mode.getInstructionLength();
        }
    }

    /** Byte-granular memory access for indirect-mode pointer reads. */
    interface MemoryReader {
        int read(int address) throws IOException;
    }

    /**
     * Decodes the instruction whose opcode and (up to two) operand bytes
     * are given; null for opcodes this decoder does not know.
     */
    @Nullable
    DecodedInstruction decode(int opcode, int operandLow, int operandHigh);

    /**
     * The memory address the instruction actually operates on, given the
     * machine state — including index-register arithmetic and indirection —
     * or null when there is none (implied, accumulator, immediate).
     */
    @Nullable
    Integer effectiveAddress(@NotNull DecodedInstruction instruction, int pc,
                             @NotNull Map<String, Integer> registers,
                             @NotNull MemoryReader memory) throws IOException;

    /**
     * ca65-style disassembly, e.g. {@code lda $0824,x}. Relative modes show
     * their raw signed operand ({@code bne -4}); the resolved target comes
     * from {@link #describeOperand}.
     */
    @NotNull
    String format(@NotNull DecodedInstruction instruction, int pc);

    /**
     * Annotation of how the operand resolves in the current machine state,
     * showing the components that matter for the mode — the index register
     * and computed address for indexed modes ({@code [X=$09 → $082d]}), the
     * pointer resolution for indirect modes, the target for branches — or
     * null when the disassembly already says everything (implied, immediate,
     * plain zero page/absolute).
     */
    @Nullable
    String describeOperand(@NotNull DecodedInstruction instruction, int pc,
                           @NotNull Map<String, Integer> registers,
                           @NotNull MemoryReader memory) throws IOException;

    /**
     * Whether the instruction transfers control (jumps, calls, branches,
     * returns). The byte at such an instruction's target is code, not data,
     * so displays must not present it as a value.
     */
    boolean isControlFlow(@NotNull DecodedInstruction instruction);
}
