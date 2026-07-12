package org.ca65.debugger.decode;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.util.Map;

import static org.ca65.debugger.decode.AddressingMode.ABSOLUTE;
import static org.ca65.debugger.decode.AddressingMode.ABSOLUTE_X;
import static org.ca65.debugger.decode.AddressingMode.ABSOLUTE_Y;
import static org.ca65.debugger.decode.AddressingMode.ACCUMULATOR;
import static org.ca65.debugger.decode.AddressingMode.IMMEDIATE;
import static org.ca65.debugger.decode.AddressingMode.IMPLIED;
import static org.ca65.debugger.decode.AddressingMode.INDEXED_INDIRECT;
import static org.ca65.debugger.decode.AddressingMode.INDIRECT;
import static org.ca65.debugger.decode.AddressingMode.INDIRECT_INDEXED;
import static org.ca65.debugger.decode.AddressingMode.RELATIVE;
import static org.ca65.debugger.decode.AddressingMode.ZERO_PAGE;
import static org.ca65.debugger.decode.AddressingMode.ZERO_PAGE_X;
import static org.ca65.debugger.decode.AddressingMode.ZERO_PAGE_Y;

/**
 * The 151 documented MOS 6502 opcodes. Undocumented opcodes decode to
 * null. Covers the NMOS-common subset for CMOS derivatives too; their
 * added opcodes ({@code stz}, {@code bra}, zp-indirect, …) also decode to
 * null until a dedicated table exists.
 */
public class Mos6502Decoder implements InstructionDecoder {
    private static final String[] MNEMONICS = new String[256];
    private static final AddressingMode[] MODES = new AddressingMode[256];

    /**
     * NMOS {@code jmp ($xxFF)} fetches the pointer's high byte from
     * {@code $xx00} instead of crossing the page; CMOS parts fixed this.
     */
    private final boolean myIndirectPageBug;

    public Mos6502Decoder(boolean indirectPageBug) {
        myIndirectPageBug = indirectPageBug;
    }

    private static void op(int opcode, String mnemonic, AddressingMode mode) {
        MNEMONICS[opcode] = mnemonic;
        MODES[opcode] = mode;
    }

    static {
        op(0x69, "adc", IMMEDIATE); op(0x65, "adc", ZERO_PAGE); op(0x75, "adc", ZERO_PAGE_X);
        op(0x6d, "adc", ABSOLUTE); op(0x7d, "adc", ABSOLUTE_X); op(0x79, "adc", ABSOLUTE_Y);
        op(0x61, "adc", INDEXED_INDIRECT); op(0x71, "adc", INDIRECT_INDEXED);
        op(0x29, "and", IMMEDIATE); op(0x25, "and", ZERO_PAGE); op(0x35, "and", ZERO_PAGE_X);
        op(0x2d, "and", ABSOLUTE); op(0x3d, "and", ABSOLUTE_X); op(0x39, "and", ABSOLUTE_Y);
        op(0x21, "and", INDEXED_INDIRECT); op(0x31, "and", INDIRECT_INDEXED);
        op(0x0a, "asl", ACCUMULATOR); op(0x06, "asl", ZERO_PAGE); op(0x16, "asl", ZERO_PAGE_X);
        op(0x0e, "asl", ABSOLUTE); op(0x1e, "asl", ABSOLUTE_X);
        op(0x90, "bcc", RELATIVE); op(0xb0, "bcs", RELATIVE); op(0xf0, "beq", RELATIVE);
        op(0x30, "bmi", RELATIVE); op(0xd0, "bne", RELATIVE); op(0x10, "bpl", RELATIVE);
        op(0x50, "bvc", RELATIVE); op(0x70, "bvs", RELATIVE);
        op(0x24, "bit", ZERO_PAGE); op(0x2c, "bit", ABSOLUTE);
        op(0x00, "brk", IMPLIED);
        op(0x18, "clc", IMPLIED); op(0xd8, "cld", IMPLIED); op(0x58, "cli", IMPLIED);
        op(0xb8, "clv", IMPLIED);
        op(0xc9, "cmp", IMMEDIATE); op(0xc5, "cmp", ZERO_PAGE); op(0xd5, "cmp", ZERO_PAGE_X);
        op(0xcd, "cmp", ABSOLUTE); op(0xdd, "cmp", ABSOLUTE_X); op(0xd9, "cmp", ABSOLUTE_Y);
        op(0xc1, "cmp", INDEXED_INDIRECT); op(0xd1, "cmp", INDIRECT_INDEXED);
        op(0xe0, "cpx", IMMEDIATE); op(0xe4, "cpx", ZERO_PAGE); op(0xec, "cpx", ABSOLUTE);
        op(0xc0, "cpy", IMMEDIATE); op(0xc4, "cpy", ZERO_PAGE); op(0xcc, "cpy", ABSOLUTE);
        op(0xc6, "dec", ZERO_PAGE); op(0xd6, "dec", ZERO_PAGE_X);
        op(0xce, "dec", ABSOLUTE); op(0xde, "dec", ABSOLUTE_X);
        op(0xca, "dex", IMPLIED); op(0x88, "dey", IMPLIED);
        op(0x49, "eor", IMMEDIATE); op(0x45, "eor", ZERO_PAGE); op(0x55, "eor", ZERO_PAGE_X);
        op(0x4d, "eor", ABSOLUTE); op(0x5d, "eor", ABSOLUTE_X); op(0x59, "eor", ABSOLUTE_Y);
        op(0x41, "eor", INDEXED_INDIRECT); op(0x51, "eor", INDIRECT_INDEXED);
        op(0xe6, "inc", ZERO_PAGE); op(0xf6, "inc", ZERO_PAGE_X);
        op(0xee, "inc", ABSOLUTE); op(0xfe, "inc", ABSOLUTE_X);
        op(0xe8, "inx", IMPLIED); op(0xc8, "iny", IMPLIED);
        op(0x4c, "jmp", ABSOLUTE); op(0x6c, "jmp", INDIRECT);
        op(0x20, "jsr", ABSOLUTE);
        op(0xa9, "lda", IMMEDIATE); op(0xa5, "lda", ZERO_PAGE); op(0xb5, "lda", ZERO_PAGE_X);
        op(0xad, "lda", ABSOLUTE); op(0xbd, "lda", ABSOLUTE_X); op(0xb9, "lda", ABSOLUTE_Y);
        op(0xa1, "lda", INDEXED_INDIRECT); op(0xb1, "lda", INDIRECT_INDEXED);
        op(0xa2, "ldx", IMMEDIATE); op(0xa6, "ldx", ZERO_PAGE); op(0xb6, "ldx", ZERO_PAGE_Y);
        op(0xae, "ldx", ABSOLUTE); op(0xbe, "ldx", ABSOLUTE_Y);
        op(0xa0, "ldy", IMMEDIATE); op(0xa4, "ldy", ZERO_PAGE); op(0xb4, "ldy", ZERO_PAGE_X);
        op(0xac, "ldy", ABSOLUTE); op(0xbc, "ldy", ABSOLUTE_X);
        op(0x4a, "lsr", ACCUMULATOR); op(0x46, "lsr", ZERO_PAGE); op(0x56, "lsr", ZERO_PAGE_X);
        op(0x4e, "lsr", ABSOLUTE); op(0x5e, "lsr", ABSOLUTE_X);
        op(0xea, "nop", IMPLIED);
        op(0x09, "ora", IMMEDIATE); op(0x05, "ora", ZERO_PAGE); op(0x15, "ora", ZERO_PAGE_X);
        op(0x0d, "ora", ABSOLUTE); op(0x1d, "ora", ABSOLUTE_X); op(0x19, "ora", ABSOLUTE_Y);
        op(0x01, "ora", INDEXED_INDIRECT); op(0x11, "ora", INDIRECT_INDEXED);
        op(0x48, "pha", IMPLIED); op(0x08, "php", IMPLIED);
        op(0x68, "pla", IMPLIED); op(0x28, "plp", IMPLIED);
        op(0x2a, "rol", ACCUMULATOR); op(0x26, "rol", ZERO_PAGE); op(0x36, "rol", ZERO_PAGE_X);
        op(0x2e, "rol", ABSOLUTE); op(0x3e, "rol", ABSOLUTE_X);
        op(0x6a, "ror", ACCUMULATOR); op(0x66, "ror", ZERO_PAGE); op(0x76, "ror", ZERO_PAGE_X);
        op(0x6e, "ror", ABSOLUTE); op(0x7e, "ror", ABSOLUTE_X);
        op(0x40, "rti", IMPLIED); op(0x60, "rts", IMPLIED);
        op(0xe9, "sbc", IMMEDIATE); op(0xe5, "sbc", ZERO_PAGE); op(0xf5, "sbc", ZERO_PAGE_X);
        op(0xed, "sbc", ABSOLUTE); op(0xfd, "sbc", ABSOLUTE_X); op(0xf9, "sbc", ABSOLUTE_Y);
        op(0xe1, "sbc", INDEXED_INDIRECT); op(0xf1, "sbc", INDIRECT_INDEXED);
        op(0x38, "sec", IMPLIED); op(0xf8, "sed", IMPLIED); op(0x78, "sei", IMPLIED);
        op(0x85, "sta", ZERO_PAGE); op(0x95, "sta", ZERO_PAGE_X);
        op(0x8d, "sta", ABSOLUTE); op(0x9d, "sta", ABSOLUTE_X); op(0x99, "sta", ABSOLUTE_Y);
        op(0x81, "sta", INDEXED_INDIRECT); op(0x91, "sta", INDIRECT_INDEXED);
        op(0x86, "stx", ZERO_PAGE); op(0x96, "stx", ZERO_PAGE_Y); op(0x8e, "stx", ABSOLUTE);
        op(0x84, "sty", ZERO_PAGE); op(0x94, "sty", ZERO_PAGE_X); op(0x8c, "sty", ABSOLUTE);
        op(0xaa, "tax", IMPLIED); op(0xa8, "tay", IMPLIED); op(0xba, "tsx", IMPLIED);
        op(0x8a, "txa", IMPLIED); op(0x9a, "txs", IMPLIED); op(0x98, "tya", IMPLIED);
    }

    @Override
    public @Nullable DecodedInstruction decode(int opcode, int operandLow, int operandHigh) {
        String mnemonic = MNEMONICS[opcode & 0xff];
        if (mnemonic == null) {
            return null;
        }
        AddressingMode mode = MODES[opcode & 0xff];
        int operand = mode.getInstructionLength() == 3
                ? (operandLow & 0xff) | ((operandHigh & 0xff) << 8)
                : operandLow & 0xff;
        return new DecodedInstruction(mnemonic, mode, operand);
    }

    @Override
    public @Nullable Integer effectiveAddress(@NotNull DecodedInstruction instruction, int pc,
                                              @NotNull Map<String, Integer> registers,
                                              @NotNull MemoryReader memory) throws IOException {
        int x = registers.getOrDefault("X", 0) & 0xff;
        int y = registers.getOrDefault("Y", 0) & 0xff;
        int operand = instruction.operand();
        return switch (instruction.mode()) {
            case IMPLIED, ACCUMULATOR, IMMEDIATE -> null;
            case ZERO_PAGE -> operand;
            case ZERO_PAGE_X -> (operand + x) & 0xff;     // wraps within the zero page
            case ZERO_PAGE_Y -> (operand + y) & 0xff;
            case ABSOLUTE -> operand;
            case ABSOLUTE_X -> (operand + x) & 0xffff;
            case ABSOLUTE_Y -> (operand + y) & 0xffff;
            case INDIRECT -> {
                int high = myIndirectPageBug
                        ? (operand & 0xff00) | ((operand + 1) & 0xff)
                        : (operand + 1) & 0xffff;
                yield memory.read(operand) | (memory.read(high) << 8);
            }
            case INDEXED_INDIRECT -> {                    // (zp,x): pointer wraps in zero page
                int pointer = (operand + x) & 0xff;
                yield memory.read(pointer) | (memory.read((pointer + 1) & 0xff) << 8);
            }
            case INDIRECT_INDEXED -> {                    // (zp),y
                int base = memory.read(operand) | (memory.read((operand + 1) & 0xff) << 8);
                yield (base + y) & 0xffff;
            }
            case RELATIVE -> (pc + 2 + (byte) operand) & 0xffff;
        };
    }

    @Override
    public @NotNull String format(@NotNull DecodedInstruction instruction, int pc) {
        String mnemonic = instruction.mnemonic();
        int operand = instruction.operand();
        return switch (instruction.mode()) {
            case IMPLIED, ACCUMULATOR -> mnemonic;
            case IMMEDIATE -> String.format("%s #$%02x", mnemonic, operand);
            case ZERO_PAGE -> String.format("%s $%02x", mnemonic, operand);
            case ZERO_PAGE_X -> String.format("%s $%02x,x", mnemonic, operand);
            case ZERO_PAGE_Y -> String.format("%s $%02x,y", mnemonic, operand);
            case ABSOLUTE -> String.format("%s $%04x", mnemonic, operand);
            case ABSOLUTE_X -> String.format("%s $%04x,x", mnemonic, operand);
            case ABSOLUTE_Y -> String.format("%s $%04x,y", mnemonic, operand);
            case INDIRECT -> String.format("%s ($%04x)", mnemonic, operand);
            case INDEXED_INDIRECT -> String.format("%s ($%02x,x)", mnemonic, operand);
            case INDIRECT_INDEXED -> String.format("%s ($%02x),y", mnemonic, operand);
            // branches carry a signed byte; the resolved target is shown
            // by describeOperand so both components stay visible
            case RELATIVE -> String.format("%s %+d", mnemonic, (byte) operand);
        };
    }

    @Override
    public @Nullable String describeOperand(@NotNull DecodedInstruction instruction, int pc,
                                            @NotNull Map<String, Integer> registers,
                                            @NotNull MemoryReader memory) throws IOException {
        Integer effective = effectiveAddress(instruction, pc, registers, memory);
        if (effective == null) {
            return null;
        }
        int x = registers.getOrDefault("X", 0) & 0xff;
        int y = registers.getOrDefault("Y", 0) & 0xff;
        return switch (instruction.mode()) {
            // the disassembly already contains the full address
            case IMPLIED, ACCUMULATOR, IMMEDIATE, ZERO_PAGE, ABSOLUTE -> null;
            case ZERO_PAGE_X, ABSOLUTE_X, INDEXED_INDIRECT ->
                    String.format("[X=$%02x → $%04x]", x, effective);
            case ZERO_PAGE_Y, ABSOLUTE_Y, INDIRECT_INDEXED ->
                    String.format("[Y=$%02x → $%04x]", y, effective);
            case INDIRECT, RELATIVE -> String.format("[→ $%04x]", effective);
        };
    }

    @Override
    public boolean isControlFlow(@NotNull DecodedInstruction instruction) {
        return instruction.mode() == RELATIVE
                || switch (instruction.mnemonic()) {
            case "jmp", "jsr", "rts", "rti", "brk" -> true;
            default -> false;
        };
    }
}
