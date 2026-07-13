package org.ca65.run;

import com.intellij.execution.ExecutionException;
import com.intellij.execution.configurations.GeneralCommandLine;
import com.intellij.util.execution.ParametersListUtil;
import org.ca65.buildcfg.Ca65TargetSystem;
import org.ca65.debugger.backend.DebuggerBackend;
import org.ca65.debugger.vice.ViceDebuggerBackend;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.io.IOException;
import java.util.Map;

/**
 * VICE (<a href="https://vice-emu.sourceforge.io/">vice-emu</a>) covers the
 * whole Commodore family with one emulator per machine. The same launch
 * shape (and later, the same binary monitor protocol) works for all of them.
 */
public class ViceEmulatorProvider implements EmulatorProvider {
    public static final String ID = "vice";

    /** cc65 target system id → VICE emulator executable. */
    private static final Map<String, String> EXECUTABLES = Map.of(
            Ca65TargetSystem.C64.id, "x64sc",
            Ca65TargetSystem.C128.id, "x128",
            Ca65TargetSystem.VIC20.id, "xvic",
            Ca65TargetSystem.PET.id, "xpet",
            Ca65TargetSystem.PLUS4.id, "xplus4",
            Ca65TargetSystem.C16.id, "xplus4",
            Ca65TargetSystem.CBM510.id, "xcbm5x0",
            Ca65TargetSystem.CBM610.id, "xcbm2");

    @Override
    public @NotNull String getId() {
        return ID;
    }

    @Override
    public @NotNull String getDisplayName() {
        return "VICE";
    }

    @Override
    public boolean supports(@NotNull Ca65TargetSystem system) {
        return EXECUTABLES.containsKey(system.id);
    }

    @Override
    public boolean supportsDebugging(@NotNull Ca65TargetSystem system) {
        // The binary monitor is available in every VICE emulator since 3.5.
        return supports(system);
    }

    @Override
    public @NotNull String getDefaultExecutable(@NotNull Ca65TargetSystem system) {
        String executable = EXECUTABLES.get(system.id);
        return executable != null ? executable : "x64sc";
    }

    @Override
    public @NotNull GeneralCommandLine createCommandLine(@NotNull EmulatorLaunchRequest request)
            throws ExecutionException {
        String executable = request.executableOverride().isBlank()
                ? getDefaultExecutable(request.targetSystem())
                : request.executableOverride().trim();
        GeneralCommandLine commandLine = new GeneralCommandLine(executable);
        commandLine.addParameters(ParametersListUtil.parse(request.extraArgs()));
        // Symbols for VICE's built-in monitor, when the build produced them.
        if (request.labelFilePath() != null && new File(request.labelFilePath()).isFile()) {
            commandLine.addParameters("-moncommands", request.labelFilePath());
        }
        commandLine.addParameters("-autostart", request.binaryPath());
        return commandLine;
    }

    @Override
    public @NotNull GeneralCommandLine createDebugCommandLine(@NotNull EmulatorLaunchRequest request,
                                                              int debugPort) throws ExecutionException {
        String executable = request.executableOverride().isBlank()
                ? getDefaultExecutable(request.targetSystem())
                : request.executableOverride().trim();
        GeneralCommandLine commandLine = new GeneralCommandLine(executable);
        commandLine.addParameters(ParametersListUtil.parse(request.extraArgs()));
        if (request.labelFilePath() != null && new File(request.labelFilePath()).isFile()) {
            commandLine.addParameters("-moncommands", request.labelFilePath());
        }
        // Never the default port: parallel sessions and stray VICE instances.
        commandLine.addParameters("-binarymonitor",
                "-binarymonitoraddress", "ip4://127.0.0.1:" + debugPort);
        return commandLine;
    }

    @Override
    public @NotNull DebuggerBackend connectDebugger(@NotNull EmulatorLaunchRequest request, int debugPort,
                                                    @NotNull DebuggerBackend.Listener listener,
                                                    long timeoutMillis) throws IOException {
        return ViceDebuggerBackend.connect("127.0.0.1", debugPort, listener, timeoutMillis,
                request.binaryPath());
    }
}
