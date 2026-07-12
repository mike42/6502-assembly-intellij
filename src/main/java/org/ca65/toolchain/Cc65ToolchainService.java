package org.ca65.toolchain;

import com.intellij.execution.ExecutionException;
import com.intellij.execution.configurations.GeneralCommandLine;
import com.intellij.execution.util.ExecUtil;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.components.PersistentStateComponent;
import com.intellij.openapi.components.RoamingType;
import com.intellij.openapi.components.State;
import com.intellij.openapi.components.Storage;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;

/**
 * Machine-level location of the cc65 toolchain. An empty bin directory means
 * the tools are expected on $PATH.
 */
@State(name = "Cc65Toolchain",
        storages = @Storage(value = "asm_6502_toolchains.xml", roamingType = RoamingType.DISABLED))
public class Cc65ToolchainService implements PersistentStateComponent<Cc65ToolchainService.State> {
    public static class State {
        public String cc65BinDir = "";
    }

    private State myState = new State();

    public static Cc65ToolchainService getInstance() {
        return ApplicationManager.getApplication().getService(Cc65ToolchainService.class);
    }

    @Override
    public State getState() {
        return myState;
    }

    @Override
    public void loadState(@NotNull State state) {
        myState = state;
    }

    @NotNull
    public String getCc65BinDir() {
        return myState.cc65BinDir == null ? "" : myState.cc65BinDir;
    }

    public void setCc65BinDir(@NotNull String binDir) {
        myState.cc65BinDir = binDir.trim();
    }

    /** Full path to a cc65 tool, or the bare name when relying on $PATH. */
    @NotNull
    public String getToolPath(@NotNull String toolName) {
        return getToolPath(getCc65BinDir(), toolName);
    }

    @NotNull
    public static String getToolPath(@NotNull String binDir, @NotNull String toolName) {
        String name = File.separatorChar == '\\' ? toolName + ".exe" : toolName;
        if (binDir.isBlank()) {
            return name;
        }
        return new File(binDir.trim(), name).getPath();
    }

    /**
     * Runs {@code <tool> --version} and returns the first output line
     * (e.g. "ca65 V2.18 - Debian 2.19-2"), or null if the tool could not be
     * run. Blocking; call from a pooled thread.
     */
    @Nullable
    public static String detectVersion(@NotNull String binDir, @NotNull String toolName) {
        GeneralCommandLine commandLine = new GeneralCommandLine(getToolPath(binDir, toolName), "--version");
        try {
            // ca65 writes its version banner to stderr on some builds, stdout on others.
            var output = ExecUtil.execAndGetOutput(commandLine, 5000);
            String text = (output.getStdout() + "\n" + output.getStderr()).trim();
            if (text.isEmpty()) {
                return null;
            }
            return text.lines().findFirst().orElse(null);
        } catch (ExecutionException e) {
            return null;
        }
    }
}
