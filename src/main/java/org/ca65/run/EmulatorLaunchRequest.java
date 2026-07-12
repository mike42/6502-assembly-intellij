package org.ca65.run;

import org.ca65.buildcfg.Ca65TargetSystem;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Everything an {@link EmulatorProvider} needs to compose a launch command.
 *
 * @param targetSystem       the build configuration's target system
 * @param binaryPath         absolute path of the linked program
 * @param labelFilePath      absolute path of the VICE label file, or null when not generated
 * @param executableOverride emulator executable chosen in the run configuration, or empty for the default
 * @param extraArgs          raw additional command line arguments from the run configuration
 */
public record EmulatorLaunchRequest(@NotNull Ca65TargetSystem targetSystem,
                                    @NotNull String binaryPath,
                                    @Nullable String labelFilePath,
                                    @NotNull String executableOverride,
                                    @NotNull String extraArgs) {
}
