package org.ca65.buildcfg;

import com.intellij.util.execution.ParametersListUtil;
import org.ca65.toolchain.Cc65ToolchainService;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Turns a build configuration into the ca65/ld65 command lines that realize
 * it. Pure logic: all paths stay project-relative; the runner executes the
 * commands with the project root as working directory.
 */
public final class Ca65CommandBuilder {
    /** One external command: argv plus a short title for build output. */
    public record Command(@NotNull String title, @NotNull List<String> argv) {
    }

    private Ca65CommandBuilder() {
    }

    @NotNull
    public static List<Command> build(@NotNull Ca65BuildConfiguration config, @NotNull String toolchainBinDir) {
        List<Command> commands = new ArrayList<>();
        String outDir = config.getEffectiveOutputDir();
        List<String> objectFiles = objectFileNames(config.sources);

        for (int i = 0; i < config.sources.size(); i++) {
            String source = config.sources.get(i);
            List<String> argv = new ArrayList<>();
            argv.add(Cc65ToolchainService.getToolPath(toolchainBinDir, "ca65"));
            if (config.assemblerDebugInfo) {
                argv.add("-g");
            }
            // Deliberately no -t here: it would remap string literals to the
            // target character set (PETSCII on CBM), garbling text in sources
            // that expect their bytes verbatim. Users who want the mapping
            // and the __C64__-style defines can add -t via the extra
            // assembler arguments.
            argv.add("--cpu");
            argv.add(config.getEffectiveCpuName());
            for (String define : config.defines) {
                if (!define.isBlank()) {
                    argv.add("-D");
                    argv.add(define.trim());
                }
            }
            argv.addAll(ParametersListUtil.parse(config.extraAssemblerArgs == null ? "" : config.extraAssemblerArgs));
            argv.add("-o");
            argv.add(outDir + "/" + objectFiles.get(i));
            argv.add(source);
            commands.add(new Command("ca65 " + source, argv));
        }

        List<String> argv = new ArrayList<>();
        argv.add(Cc65ToolchainService.getToolPath(toolchainBinDir, "ld65"));
        Ca65TargetSystem system = config.getTargetSystemEnum();
        if (system == null) {
            argv.add("-C");
            argv.add(config.customConfigFile);
        } else if (system.getAsmConfigName() != null) {
            // -t <sys> would pick the C-runtime <sys>.cfg, which does not
            // link pure-assembly programs; use the shipped -asm variant.
            argv.add("-C");
            argv.add(system.getAsmConfigName());
        } else {
            argv.add("-t");
            argv.add(system.id);
        }
        String binary = outDir + "/" + config.getOutputBinaryName();
        argv.add("-o");
        argv.add(binary);
        if (config.generateDbgFile) {
            argv.add("--dbgfile");
            argv.add(outDir + "/" + config.name + ".dbg");
        }
        if (config.generateLabelFile) {
            argv.add("-Ln");
            argv.add(outDir + "/" + config.name + ".lbl");
        }
        if (config.generateMapFile) {
            argv.add("-m");
            argv.add(outDir + "/" + config.name + ".map");
        }
        argv.addAll(ParametersListUtil.parse(config.extraLinkerArgs == null ? "" : config.extraLinkerArgs));
        for (String objectFile : objectFiles) {
            argv.add(outDir + "/" + objectFile);
        }
        commands.add(new Command("ld65 " + binary, argv));
        return commands;
    }

    /**
     * Object file name for each source: the base name where unique, the
     * path flattened with underscores where two sources share a base name.
     */
    @NotNull
    static List<String> objectFileNames(@NotNull List<String> sources) {
        List<String> baseNames = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        Set<String> duplicates = new HashSet<>();
        for (String source : sources) {
            String base = stripExtension(source.substring(source.lastIndexOf('/') + 1));
            baseNames.add(base);
            if (!seen.add(base)) {
                duplicates.add(base);
            }
        }
        List<String> result = new ArrayList<>();
        for (int i = 0; i < sources.size(); i++) {
            String base = baseNames.get(i);
            if (duplicates.contains(base)) {
                result.add(stripExtension(sources.get(i)).replace('/', '_') + ".o");
            } else {
                result.add(base + ".o");
            }
        }
        return result;
    }

    private static String stripExtension(String path) {
        int dot = path.lastIndexOf('.');
        return dot > path.lastIndexOf('/') ? path.substring(0, dot) : path;
    }
}
