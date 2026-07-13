package org.ca65.run;

import com.intellij.execution.ExecutionException;
import com.intellij.execution.configurations.GeneralCommandLine;
import com.intellij.util.execution.ParametersListUtil;
import org.ca65.buildcfg.Ca65TargetSystem;
import org.ca65.debugger.backend.DebuggerBackend;
import org.ca65.debugger.gdb.GdbDebuggerBackend;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.util.Map;

/**
 * MAME (<a href="https://www.mamedev.org/">mamedev</a>) covers hundreds of
 * systems with one executable; the machine is the first command-line
 * argument. Debugging uses MAME's gdbstub debugger module, which serves
 * the GDB remote serial protocol on a TCP port.
 */
public class MameEmulatorProvider implements EmulatorProvider {
    public static final String ID = "mame";

    /**
     * cc65 target system id → MAME machine name. Only machines whose CPU
     * is in the gdbstub's register map can debug: the NES/Famicom machines
     * use the rp2a03g CPU device, which is missing from that map and makes
     * MAME fatalerror on startup, while famicomo (the early Famicom) uses
     * the plain rp2a03, which works. Cartridge-wise they are equivalent;
     * switch to "nes" once MAME's table gains rp2a03g.
     */
    private static final Map<String, String> MACHINES = Map.of(
            Ca65TargetSystem.NES.id, "famicomo");

    /**
     * MAME starts the emulation stopped in its debugger and only opens the
     * gdbstub's listening socket once driver init and ROM load complete,
     * which can take several seconds.
     */
    private static final long MIN_CONNECT_TIMEOUT_MILLIS = 30_000;

    @Override
    public @NotNull String getId() {
        return ID;
    }

    @Override
    public @NotNull String getDisplayName() {
        return "MAME";
    }

    @Override
    public boolean supports(@NotNull Ca65TargetSystem system) {
        return MACHINES.containsKey(system.id);
    }

    @Override
    public boolean supportsDebugging(@NotNull Ca65TargetSystem system) {
        return supports(system);
    }

    @Override
    public @NotNull String getDefaultExecutable(@NotNull Ca65TargetSystem system) {
        return "mame";
    }

    @Override
    public boolean supportsSoftKill() {
        // MAME ignores SIGINT but exits cleanly on SIGTERM (verified with
        // MAME 0.288), so stop must go straight to process termination.
        return false;
    }

    @Override
    public @NotNull GeneralCommandLine createCommandLine(@NotNull EmulatorLaunchRequest request)
            throws ExecutionException {
        GeneralCommandLine commandLine = baseCommandLine(request);
        commandLine.addParameters("-cart", request.binaryPath());
        return commandLine;
    }

    @Override
    public @NotNull GeneralCommandLine createDebugCommandLine(@NotNull EmulatorLaunchRequest request,
                                                              int debugPort) throws ExecutionException {
        GeneralCommandLine commandLine = baseCommandLine(request);
        commandLine.addParameters("-debug", "-debugger", "gdbstub",
                "-debugger_port", String.valueOf(debugPort),
                "-debugger_host", "127.0.0.1");
        commandLine.addParameters("-cart", request.binaryPath());
        return commandLine;
    }

    @NotNull
    private GeneralCommandLine baseCommandLine(@NotNull EmulatorLaunchRequest request)
            throws ExecutionException {
        String machine = MACHINES.get(request.targetSystem().id);
        if (machine == null) {
            throw new ExecutionException(getDisplayName() + " does not support the "
                    + request.targetSystem().displayName + " target");
        }
        String executable = request.executableOverride().isBlank()
                ? getDefaultExecutable(request.targetSystem())
                : request.executableOverride().trim();
        GeneralCommandLine commandLine = new GeneralCommandLine(executable);
        commandLine.addParameter(machine);
        commandLine.addParameters("-window", "-nomouse");
        // Extra args after the defaults so users can override them; the
        // label file is not passed — MAME cannot consume VICE-style labels.
        commandLine.addParameters(ParametersListUtil.parse(request.extraArgs()));
        return commandLine;
    }

    @Override
    public @NotNull DebuggerBackend connectDebugger(@NotNull EmulatorLaunchRequest request, int debugPort,
                                                    @NotNull DebuggerBackend.Listener listener,
                                                    long timeoutMillis) throws IOException {
        return GdbDebuggerBackend.connect("127.0.0.1", debugPort, listener,
                Math.max(timeoutMillis, MIN_CONNECT_TIMEOUT_MILLIS));
    }
}
