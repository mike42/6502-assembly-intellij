package org.ca65.buildcfg;

import org.ca65.helpers.Cpu;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Set;

/**
 * The built-in ld65 target systems, with the default CPU each implies.
 * Mirrors the table in cc65 src/common/target.c; the id is what both
 * {@code ca65 -t} and {@code ld65 -t} accept.
 */
public enum Ca65TargetSystem {
    NONE("none", "None (plain binary)", "6502", "bin"),
    MODULE("module", "Module (o65)", "6502", "o65"),
    ATARI("atari", "Atari 8-bit", "6502", "xex"),
    ATARI2600("atari2600", "Atari 2600", "6502", "bin"),
    ATARI5200("atari5200", "Atari 5200", "6502", "bin"),
    ATARI7800("atari7800", "Atari 7800", "6502", "bin"),
    ATARIXL("atarixl", "Atari XL", "6502", "xex"),
    VIC20("vic20", "Commodore VIC-20", "6502", "prg"),
    C16("c16", "Commodore 16", "6502", "prg"),
    C64("c64", "Commodore 64", "6502", "prg"),
    C128("c128", "Commodore 128", "6502", "prg"),
    PLUS4("plus4", "Commodore Plus/4", "6502", "prg"),
    CBM510("cbm510", "Commodore CBM 510", "6502", "prg"),
    CBM610("cbm610", "Commodore CBM 610", "6502", "prg"),
    OSIC1P("osic1p", "Ohio Scientific C1P", "6502", "bin"),
    PET("pet", "Commodore PET", "6502", "prg"),
    BBC("bbc", "BBC Micro", "6502", "bin"),
    APPLE2("apple2", "Apple II", "6502", "bin"),
    APPLE2ENH("apple2enh", "Apple IIe (enhanced)", "65C02", "bin"),
    GEOS_CBM("geos-cbm", "GEOS (Commodore)", "6502", "cvt"),
    CREATIVISION("creativision", "VTech CreatiVision", "6502", "bin"),
    GEOS_APPLE("geos-apple", "GEOS (Apple)", "65C02", "cvt"),
    LUNIX("lunix", "LUnix", "6502", "o65"),
    ATMOS("atmos", "Oric Atmos", "6502", "tap"),
    TELESTRAT("telestrat", "Oric Telestrat", "6502", "tap"),
    NES("nes", "Nintendo Entertainment System", "6502", "nes"),
    SUPERVISION("supervision", "Watara Supervision", "65SC02", "bin"),
    LYNX("lynx", "Atari Lynx", "65SC02", "lnx"),
    SIM6502("sim6502", "sim65 (6502)", "6502", "bin"),
    SIM65C02("sim65c02", "sim65 (65C02)", "65C02", "bin"),
    PCENGINE("pce", "PC Engine", "huc6280", "pce"),
    GAMATE("gamate", "Bit Corporation Gamate", "6502", "bin"),
    C65("c65", "Commodore 65", "4510", "prg"),
    CX16("cx16", "Commander X16", "W65C02", "prg"),
    SYM1("sym1", "Synertek Systems Sym-1", "6502", "bin"),
    MEGA65("mega65", "MEGA65", "45GS02", "prg"),
    KIM1("kim1", "MOS KIM-1", "6502", "bin"),
    RP6502("rp6502", "Picocomputer 6502", "W65C02", "rp6502"),
    AGAT("agat", "Agat", "6502", "bin");

    /**
     * Targets for which cc65 ships a {@code <sys>-asm.cfg}. The default
     * {@code <sys>.cfg} selected by {@code ld65 -t} is laid out for the C
     * runtime and does not link pure-assembly programs, so these targets
     * link with {@code -C <sys>-asm.cfg} instead (ld65 resolves the name
     * against its config search path).
     */
    private static final Set<String> ASM_CONFIG_TARGETS = Set.of(
            "apple2", "apple2enh", "atari", "c128", "c16", "c64", "c65",
            "cx16", "mega65", "osic1p", "plus4", "vic20");

    public final String id;
    public final String displayName;
    /** Default CPU as a ca65 {@code --cpu} name (cc65 src/common/cpu.c). */
    public final String defaultCpuName;
    /** Conventional file extension for the linked binary. */
    public final String outputExtension;

    Ca65TargetSystem(String id, String displayName, String defaultCpuName, String outputExtension) {
        this.id = id;
        this.displayName = displayName;
        this.defaultCpuName = defaultCpuName;
        this.outputExtension = outputExtension;
    }

    /** The linker config file for pure-assembly builds, or null to use {@code -t}. */
    @Nullable
    public String getAsmConfigName() {
        return ASM_CONFIG_TARGETS.contains(id) ? id + "-asm.cfg" : null;
    }

    @Nullable
    public static Ca65TargetSystem findById(@Nullable String id) {
        if (id == null) {
            return null;
        }
        for (Ca65TargetSystem system : values()) {
            if (system.id.equals(id)) {
                return system;
            }
        }
        return null;
    }

    /**
     * Maps a ca65 CPU name onto the (coarser) CPU model the editor uses for
     * mnemonic validation and completion.
     */
    @NotNull
    public static Cpu editorCpuFor(@Nullable String cpuName) {
        if (cpuName == null) {
            return Cpu.CPU_6502;
        }
        return switch (cpuName.toLowerCase()) {
            case "65816" -> Cpu.CPU_65C816;
            // CMOS variants and extensions: closest supported editor model.
            case "65sc02", "65c02", "w65c02", "65ce02", "4510", "45gs02", "huc6280" -> Cpu.CPU_65C02;
            default -> Cpu.CPU_6502;
        };
    }

    @Override
    public String toString() {
        return displayName;
    }
}
