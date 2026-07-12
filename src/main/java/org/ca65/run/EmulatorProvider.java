package org.ca65.run;

import com.intellij.execution.ExecutionException;
import com.intellij.execution.configurations.GeneralCommandLine;
import com.intellij.openapi.extensions.ExtensionPointName;
import org.ca65.buildcfg.Ca65TargetSystem;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * One way of running a built binary on an emulator. Contributed through the
 * plugin-internal {@code org.ca65.emulatorProvider} extension point, so the
 * UI only ever offers emulators that support the selected target system —
 * there is no global list of emulators anywhere.
 */
public interface EmulatorProvider {
    ExtensionPointName<EmulatorProvider> EP_NAME = ExtensionPointName.create("org.ca65.emulatorProvider");

    @NotNull String getId();

    @NotNull String getDisplayName();

    boolean supports(@NotNull Ca65TargetSystem system);

    /** Whether debug sessions are possible on this target (used from milestone 3 on). */
    default boolean supportsDebugging(@NotNull Ca65TargetSystem system) {
        return false;
    }

    /**
     * Executable used when the run configuration does not override it
     * (bare name = resolved from $PATH), e.g. "x64sc" for the C64.
     */
    @NotNull String getDefaultExecutable(@NotNull Ca65TargetSystem system);

    @NotNull GeneralCommandLine createCommandLine(@NotNull EmulatorLaunchRequest request) throws ExecutionException;

    /**
     * Command line for a debug session: the emulator must expose its debug
     * interface on {@code debugPort} and must NOT auto-run the program (the
     * debugger loads it over the protocol once breakpoints are armed).
     * Only called when {@link #supportsDebugging} returned true.
     */
    default @NotNull GeneralCommandLine createDebugCommandLine(@NotNull EmulatorLaunchRequest request,
                                                               int debugPort) throws ExecutionException {
        throw new ExecutionException(getDisplayName() + " does not support debugging");
    }

    @NotNull
    static List<EmulatorProvider> forSystem(@Nullable Ca65TargetSystem system) {
        List<EmulatorProvider> result = new ArrayList<>();
        if (system != null) {
            for (EmulatorProvider provider : EP_NAME.getExtensionList()) {
                if (provider.supports(system)) {
                    result.add(provider);
                }
            }
        }
        return result;
    }

    @Nullable
    static EmulatorProvider findById(@Nullable String id) {
        if (id == null || id.isEmpty()) {
            return null;
        }
        for (EmulatorProvider provider : EP_NAME.getExtensionList()) {
            if (provider.getId().equals(id)) {
                return provider;
            }
        }
        return null;
    }
}
