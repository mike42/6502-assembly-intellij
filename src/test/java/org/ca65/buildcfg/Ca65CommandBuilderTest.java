package org.ca65.buildcfg;

import org.junit.Test;

import java.util.List;

import static org.junit.Assert.assertEquals;

public class Ca65CommandBuilderTest {

    private static Ca65BuildConfiguration c64Config() {
        Ca65BuildConfiguration config = new Ca65BuildConfiguration();
        config.name = "hello";
        config.sources = List.of("hello.s");
        config.targetSystem = Ca65TargetSystem.C64.id;
        return config;
    }

    @Test
    public void builtinTargetUsesAsmLinkerConfigWhereShipped() {
        List<Ca65CommandBuilder.Command> commands = Ca65CommandBuilder.build(c64Config(), "");
        assertEquals(2, commands.size());
        assertEquals(List.of("ca65", "-g", "--cpu", "6502",
                        "-o", "build/hello/hello.o", "hello.s"),
                commands.get(0).argv());
        // c64.cfg (ld65 -t c64) is the C-runtime layout; asm builds use c64-asm.cfg.
        assertEquals(List.of("ld65", "-C", "c64-asm.cfg", "-o", "build/hello/hello.prg",
                        "--dbgfile", "build/hello/hello.dbg",
                        "-Ln", "build/hello/hello.lbl",
                        "build/hello/hello.o"),
                commands.get(1).argv());
    }

    @Test
    public void builtinTargetWithoutAsmConfigUsesDashT() {
        Ca65BuildConfiguration config = c64Config();
        config.targetSystem = Ca65TargetSystem.NES.id;
        List<String> argv = Ca65CommandBuilder.build(config, "").get(1).argv();
        assertEquals(List.of("ld65", "-t", "nes", "-o", "build/hello/hello.nes",
                        "--dbgfile", "build/hello/hello.dbg",
                        "-Ln", "build/hello/hello.lbl",
                        "build/hello/hello.o"),
                argv);
    }

    @Test
    public void customTargetUsesConfigFileAndExplicitCpu() {
        Ca65BuildConfiguration config = c64Config();
        config.targetSystem = "";
        config.customConfigFile = "conf/game.cfg";
        config.cpu = "65C02";
        List<Ca65CommandBuilder.Command> commands = Ca65CommandBuilder.build(config, "");
        assertEquals(List.of("ca65", "-g", "--cpu", "65C02",
                        "-o", "build/hello/hello.o", "hello.s"),
                commands.get(0).argv());
        assertEquals("-C", commands.get(1).argv().get(1));
        assertEquals("conf/game.cfg", commands.get(1).argv().get(2));
    }

    @Test
    public void customTargetWithoutCpuDefaultsTo6502() {
        Ca65BuildConfiguration config = c64Config();
        config.targetSystem = null;
        config.customConfigFile = "game.cfg";
        List<String> argv = Ca65CommandBuilder.build(config, "").get(0).argv();
        int cpuIndex = argv.indexOf("--cpu");
        assertEquals("6502", argv.get(cpuIndex + 1));
    }

    @Test
    public void customConfigOverridesBuiltinTargetDefault() {
        Ca65BuildConfiguration config = c64Config();
        config.targetSystem = Ca65TargetSystem.NES.id;
        config.customConfigFile = "table_tennis.cfg";
        List<String> argv = Ca65CommandBuilder.build(config, "").get(1).argv();
        assertEquals(List.of("ld65", "-C", "table_tennis.cfg", "-o", "build/hello/hello.nes",
                        "--dbgfile", "build/hello/hello.dbg",
                        "-Ln", "build/hello/hello.lbl",
                        "build/hello/hello.o"),
                argv);
    }

    @Test
    public void customConfigWinsOverShippedAsmConfig() {
        Ca65BuildConfiguration config = c64Config();
        config.customConfigFile = "conf/my-c64.cfg";
        List<String> argv = Ca65CommandBuilder.build(config, "").get(1).argv();
        assertEquals("-C", argv.get(1));
        assertEquals("conf/my-c64.cfg", argv.get(2));
        assertEquals(-1, argv.indexOf("c64-asm.cfg"));
    }

    @Test
    public void cpuOverrideOnBuiltinTargetIsUsed() {
        Ca65BuildConfiguration config = c64Config();
        config.cpu = "6502X";
        List<String> argv = Ca65CommandBuilder.build(config, "").get(0).argv();
        assertEquals(List.of("ca65", "-g", "--cpu", "6502X",
                "-o", "build/hello/hello.o", "hello.s"), argv);
    }

    @Test
    public void assemblerNeverGetsDashT() {
        // ca65 -t would remap string literals to the target character set
        // (PETSCII on CBM), garbling text in sources that expect their
        // bytes verbatim; the CPU is selected explicitly instead.
        List<String> argv = Ca65CommandBuilder.build(c64Config(), "").get(0).argv();
        assertEquals(-1, argv.indexOf("-t"));
    }

    @Test
    public void definesAndExtraArgsArePassedThrough() {
        Ca65BuildConfiguration config = c64Config();
        config.defines = List.of("DEBUG", "REVISION=2");
        config.extraAssemblerArgs = "--feature underline_in_numbers";
        config.extraLinkerArgs = "-vm";
        List<Ca65CommandBuilder.Command> commands = Ca65CommandBuilder.build(config, "");
        assertEquals(List.of("ca65", "-g", "--cpu", "6502", "-D", "DEBUG", "-D", "REVISION=2",
                        "--feature", "underline_in_numbers",
                        "-o", "build/hello/hello.o", "hello.s"),
                commands.get(0).argv());
        assertEquals(true, commands.get(1).argv().contains("-vm"));
    }

    @Test
    public void toolchainBinDirPrefixesExecutables() {
        List<Ca65CommandBuilder.Command> commands = Ca65CommandBuilder.build(c64Config(), "/opt/cc65/bin");
        assertEquals("/opt/cc65/bin/ca65", commands.get(0).argv().get(0));
        assertEquals("/opt/cc65/bin/ld65", commands.get(1).argv().get(0));
    }

    @Test
    public void debugInfoFlagsCanBeDisabled() {
        Ca65BuildConfiguration config = c64Config();
        config.assemblerDebugInfo = false;
        config.generateDbgFile = false;
        config.generateLabelFile = false;
        config.generateMapFile = true;
        List<Ca65CommandBuilder.Command> commands = Ca65CommandBuilder.build(config, "");
        assertEquals(List.of("ca65", "--cpu", "6502", "-o", "build/hello/hello.o", "hello.s"),
                commands.get(0).argv());
        assertEquals(List.of("ld65", "-C", "c64-asm.cfg", "-o", "build/hello/hello.prg",
                        "-m", "build/hello/hello.map", "build/hello/hello.o"),
                commands.get(1).argv());
    }

    @Test
    public void explicitOutputDirIsRespected() {
        Ca65BuildConfiguration config = c64Config();
        config.outputDir = "out";
        List<String> argv = Ca65CommandBuilder.build(config, "").get(0).argv();
        assertEquals("out/hello.o", argv.get(argv.indexOf("-o") + 1));
    }

    @Test
    public void duplicateBaseNamesAreFlattened() {
        assertEquals(List.of("a_main.o", "b_main.o", "util.o"),
                Ca65CommandBuilder.objectFileNames(List.of("a/main.s", "b/main.s", "lib/util.s")));
    }

    @Test
    public void uniqueBaseNamesStayShort() {
        assertEquals(List.of("main.o", "util.o"),
                Ca65CommandBuilder.objectFileNames(List.of("src/main.s", "src/util.s")));
    }
}
