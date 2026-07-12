package org.ca65.buildcfg;

import org.jetbrains.annotations.NotNull;

import java.io.File;

/** Absolute locations of a build configuration's outputs. */
public final class Ca65BuildPaths {
    private Ca65BuildPaths() {
    }

    @NotNull
    public static File outputDir(@NotNull String basePath, @NotNull Ca65BuildConfiguration config) {
        return new File(basePath, config.getEffectiveOutputDir());
    }

    @NotNull
    public static File binaryFile(@NotNull String basePath, @NotNull Ca65BuildConfiguration config) {
        return new File(outputDir(basePath, config), config.getOutputBinaryName());
    }

    @NotNull
    public static File labelFile(@NotNull String basePath, @NotNull Ca65BuildConfiguration config) {
        return new File(outputDir(basePath, config), config.name + ".lbl");
    }

    @NotNull
    public static File dbgFile(@NotNull String basePath, @NotNull Ca65BuildConfiguration config) {
        return new File(outputDir(basePath, config), config.name + ".dbg");
    }
}
