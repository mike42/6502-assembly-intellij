package org.ca65.debugger.decode;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Locale;

/** Picks the instruction decoder for a ca65 CPU name. */
public final class InstructionDecoders {
    private InstructionDecoders() {
    }

    /**
     * Null when the CPU has no decoder yet — callers must degrade
     * gracefully (no disassembly / effective-address display). Notably the
     * 65816 uses different encodings, operand widths, and addressing modes
     * and needs its own implementation.
     */
    @Nullable
    public static InstructionDecoder forCpuName(@NotNull String cpuName) {
        return switch (cpuName.toLowerCase(Locale.ROOT)) {
            case "6502", "6502x", "6502dtv" -> new Mos6502Decoder(true);
            // CMOS derivatives: the NMOS-common subset decodes identically
            // and jmp (indirect) has the page-crossing bug fixed. Their own
            // added opcodes decode to null until they get dedicated tables.
            case "65sc02", "65c02", "w65c02", "65ce02", "4510", "45gs02", "huc6280" ->
                    new Mos6502Decoder(false);
            default -> null;
        };
    }
}
