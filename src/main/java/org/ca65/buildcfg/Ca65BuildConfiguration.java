package org.ca65.buildcfg;

import com.intellij.util.xmlb.annotations.Tag;
import com.intellij.util.xmlb.annotations.Transient;
import com.intellij.util.xmlb.annotations.XCollection;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * How to build a 6502 program: a set of top-level source files (each
 * assembled to its own object file) linked into a single binary. Embedded
 * in {@code Ca65RunConfiguration} and serialized as part of it.
 */
@Tag("configuration")
public class Ca65BuildConfiguration {
    /** Derived from the owning run configuration's name; not serialized. */
    @Transient
    public String name = "";

    /** Project-relative paths of top-level assembly files, one .o each. */
    @XCollection(propertyElementName = "sources", elementName = "source", valueAttributeName = "path")
    public List<String> sources = new ArrayList<>();

    /** Feature flags, NAME or NAME=value, passed to ca65 as -D. */
    @XCollection(propertyElementName = "defines", elementName = "define", valueAttributeName = "value")
    public List<String> defines = new ArrayList<>();

    /**
     * Built-in target system id ({@link Ca65TargetSystem#id}), or null/empty
     * when a custom linker config is used instead.
     */
    public String targetSystem = Ca65TargetSystem.C64.id;

    /**
     * Project-relative path of a custom ld65 .cfg. Required when targetSystem
     * is unset; for a built-in target it optionally overrides the target's
     * default linker config (the target still drives CPU default, output
     * extension and emulator choice).
     */
    public String customConfigFile = "";

    /** ca65 --cpu name; empty means "derive from the target system". */
    public String cpu = "";

    /** Project-relative build output directory; empty means build/<name>. */
    public String outputDir = "";

    public boolean assemblerDebugInfo = true;   // ca65 -g
    public boolean generateDbgFile = true;      // ld65 --dbgfile
    public boolean generateLabelFile = true;    // ld65 -Ln (VICE labels)
    public boolean generateMapFile = false;     // ld65 -m

    public String extraAssemblerArgs = "";
    public String extraLinkerArgs = "";

    public boolean isCustomTarget() {
        return targetSystem == null || targetSystem.isBlank();
    }

    public boolean hasCustomConfigFile() {
        return customConfigFile != null && !customConfigFile.isBlank();
    }

    @Nullable
    public Ca65TargetSystem getTargetSystemEnum() {
        return isCustomTarget() ? null : Ca65TargetSystem.findById(targetSystem);
    }

    /** Effective ca65 CPU name after applying target-system defaults. */
    @NotNull
    public String getEffectiveCpuName() {
        if (cpu != null && !cpu.isBlank()) {
            return cpu;
        }
        Ca65TargetSystem system = getTargetSystemEnum();
        return system != null ? system.defaultCpuName : "6502";
    }

    /** Project-relative output directory after applying the default. */
    @NotNull
    public String getEffectiveOutputDir() {
        if (outputDir != null && !outputDir.isBlank()) {
            return outputDir;
        }
        return "build/" + name;
    }

    /** File name of the linked binary within the output directory. */
    @NotNull
    public String getOutputBinaryName() {
        Ca65TargetSystem system = getTargetSystemEnum();
        String extension = system != null ? system.outputExtension : "bin";
        return name + "." + extension;
    }

    @NotNull
    public Ca65BuildConfiguration copy() {
        Ca65BuildConfiguration copy = new Ca65BuildConfiguration();
        copy.name = name;
        copy.sources = new ArrayList<>(sources);
        copy.defines = new ArrayList<>(defines);
        copy.targetSystem = targetSystem;
        copy.customConfigFile = customConfigFile;
        copy.cpu = cpu;
        copy.outputDir = outputDir;
        copy.assemblerDebugInfo = assemblerDebugInfo;
        copy.generateDbgFile = generateDbgFile;
        copy.generateLabelFile = generateLabelFile;
        copy.generateMapFile = generateMapFile;
        copy.extraAssemblerArgs = extraAssemblerArgs;
        copy.extraLinkerArgs = extraLinkerArgs;
        return copy;
    }

    @Override
    public boolean equals(Object o) {
        if (!(o instanceof Ca65BuildConfiguration other)) {
            return false;
        }
        return Objects.equals(name, other.name)
                && Objects.equals(sources, other.sources)
                && Objects.equals(defines, other.defines)
                && Objects.equals(targetSystem, other.targetSystem)
                && Objects.equals(customConfigFile, other.customConfigFile)
                && Objects.equals(cpu, other.cpu)
                && Objects.equals(outputDir, other.outputDir)
                && assemblerDebugInfo == other.assemblerDebugInfo
                && generateDbgFile == other.generateDbgFile
                && generateLabelFile == other.generateLabelFile
                && generateMapFile == other.generateMapFile
                && Objects.equals(extraAssemblerArgs, other.extraAssemblerArgs)
                && Objects.equals(extraLinkerArgs, other.extraLinkerArgs);
    }

    @Override
    public int hashCode() {
        return Objects.hash(name, sources, defines, targetSystem, customConfigFile, cpu, outputDir,
                assemblerDebugInfo, generateDbgFile, generateLabelFile, generateMapFile,
                extraAssemblerArgs, extraLinkerArgs);
    }
}
