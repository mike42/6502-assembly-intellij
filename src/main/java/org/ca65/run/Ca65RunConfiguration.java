package org.ca65.run;

import com.intellij.execution.ExecutionException;
import com.intellij.execution.Executor;
import com.intellij.execution.configurations.CommandLineState;
import com.intellij.execution.configurations.ConfigurationFactory;
import com.intellij.execution.configurations.GeneralCommandLine;
import com.intellij.execution.configurations.RunConfiguration;
import com.intellij.execution.configurations.RunConfigurationBase;
import com.intellij.execution.configurations.RunProfileState;
import com.intellij.execution.configurations.RuntimeConfigurationError;
import com.intellij.execution.configurations.RuntimeConfigurationException;
import com.intellij.execution.process.KillableProcessHandler;
import com.intellij.execution.process.NopProcessHandler;
import com.intellij.execution.process.ProcessHandler;
import com.intellij.execution.process.ProcessTerminatedListener;
import com.intellij.execution.runners.ExecutionEnvironment;
import com.intellij.openapi.options.SettingsEditor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.InvalidDataException;
import com.intellij.openapi.util.JDOMExternalizerUtil;
import com.intellij.openapi.util.WriteExternalException;
import com.intellij.util.xmlb.XmlSerializer;
import org.ca65.Asm6502Bundle;
import org.ca65.buildcfg.Ca65BuildConfiguration;
import org.ca65.buildcfg.Ca65BuildPaths;
import org.ca65.buildcfg.Ca65TargetSystem;
import org.jdom.Element;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;

/**
 * A 6502 program: how to build it (ca65/ld65, embedded {@link
 * Ca65BuildConfiguration}) and optionally which emulator to run the result
 * on. With no emulator selected the configuration is build-only — the
 * before-launch build step does the work and the run finishes immediately.
 */
public class Ca65RunConfiguration extends RunConfigurationBase<Object> {
    private static final String EMULATOR = "emulator";
    private static final String EMULATOR_EXECUTABLE = "emulatorExecutable";
    private static final String EMULATOR_ARGS = "emulatorArgs";

    /** Emulator id meaning "do not run, build only". */
    public static final String EMULATOR_NONE = "";

    private Ca65BuildConfiguration myBuildSpec = new Ca65BuildConfiguration();
    private String myEmulatorId = ViceEmulatorProvider.ID;
    private String myEmulatorExecutable = "";
    private String myEmulatorExtraArgs = "";

    protected Ca65RunConfiguration(@NotNull Project project, @NotNull ConfigurationFactory factory) {
        super(project, factory, "");
    }

    /** The embedded build settings; its name always follows this configuration's name. */
    @NotNull
    public Ca65BuildConfiguration getBuildSpec() {
        myBuildSpec.name = getName();
        return myBuildSpec;
    }

    @NotNull
    public String getEmulatorId() {
        return myEmulatorId;
    }

    public void setEmulatorId(@NotNull String emulatorId) {
        myEmulatorId = emulatorId;
    }

    public boolean isBuildOnly() {
        return myEmulatorId.isEmpty();
    }

    @NotNull
    public String getEmulatorExecutable() {
        return myEmulatorExecutable;
    }

    public void setEmulatorExecutable(@NotNull String executable) {
        myEmulatorExecutable = executable;
    }

    @NotNull
    public String getEmulatorExtraArgs() {
        return myEmulatorExtraArgs;
    }

    public void setEmulatorExtraArgs(@NotNull String args) {
        myEmulatorExtraArgs = args;
    }

    /**
     * Whether the Debug executor applies: the emulator must speak a debug
     * protocol for this target and the build must produce the debug info
     * file that maps addresses to source lines.
     */
    public boolean isDebuggable() {
        Ca65BuildConfiguration spec = getBuildSpec();
        if (!spec.generateDbgFile) {
            return false;
        }
        Ca65TargetSystem system = spec.getTargetSystemEnum();
        EmulatorProvider provider = EmulatorProvider.findById(myEmulatorId);
        return system != null && provider != null && provider.supportsDebugging(system);
    }

    @Override
    public @NotNull SettingsEditor<? extends RunConfigurationBase<?>> getConfigurationEditor() {
        return new Ca65RunConfigurationEditor(getProject());
    }

    @Override
    public void checkConfiguration() throws RuntimeConfigurationException {
        Ca65BuildConfiguration spec = getBuildSpec();
        if (spec.sources.isEmpty()) {
            throw new RuntimeConfigurationError(Asm6502Bundle.message("RUN.error.no.sources"));
        }
        Ca65TargetSystem system = spec.getTargetSystemEnum();
        if (spec.isCustomTarget()
                && (spec.customConfigFile == null || spec.customConfigFile.isBlank())) {
            throw new RuntimeConfigurationError(Asm6502Bundle.message("RUN.error.no.customcfg"));
        }
        if (isBuildOnly()) {
            return;
        }
        EmulatorProvider provider = EmulatorProvider.findById(myEmulatorId);
        if (provider == null) {
            throw new RuntimeConfigurationError(Asm6502Bundle.message("RUN.error.no.emulator"));
        }
        if (system == null) {
            throw new RuntimeConfigurationError(Asm6502Bundle.message("RUN.error.custom.target"));
        }
        if (!provider.supports(system)) {
            throw new RuntimeConfigurationError(Asm6502Bundle.message("RUN.error.emulator.unsupported",
                    provider.getDisplayName(), system.displayName));
        }
    }

    @Override
    public @Nullable RunProfileState getState(@NotNull Executor executor,
                                              @NotNull ExecutionEnvironment environment) {
        if (isBuildOnly()) {
            // The before-launch build step did the actual work; the "run"
            // has no process, so finish as soon as the console attaches.
            return new CommandLineState(environment) {
                @Override
                protected @NotNull ProcessHandler startProcess() {
                    NopProcessHandler handler = new NopProcessHandler();
                    handler.destroyProcess();
                    return handler;
                }
            };
        }
        return new CommandLineState(environment) {
            @Override
            protected @NotNull ProcessHandler startProcess() throws ExecutionException {
                GeneralCommandLine commandLine = createEmulatorCommandLine();
                KillableProcessHandler handler = new KillableProcessHandler(commandLine);
                EmulatorProvider provider = EmulatorProvider.findById(myEmulatorId);
                if (provider != null && !provider.supportsSoftKill()) {
                    handler.setShouldKillProcessSoftly(false);
                }
                ProcessTerminatedListener.attach(handler);
                return handler;
            }
        };
    }

    @NotNull
    GeneralCommandLine createEmulatorCommandLine() throws ExecutionException {
        Ca65BuildConfiguration spec = getBuildSpec();
        Ca65TargetSystem system = spec.getTargetSystemEnum();
        if (system == null) {
            throw new ExecutionException(Asm6502Bundle.message("RUN.error.custom.target"));
        }
        EmulatorProvider provider = EmulatorProvider.findById(myEmulatorId);
        if (provider == null || !provider.supports(system)) {
            throw new ExecutionException(Asm6502Bundle.message("RUN.error.no.emulator"));
        }
        String basePath = getProject().getBasePath();
        if (basePath == null) {
            throw new ExecutionException(Asm6502Bundle.message("RUN.error.no.project.path"));
        }
        File binary = Ca65BuildPaths.binaryFile(basePath, spec);
        if (!binary.isFile()) {
            throw new ExecutionException(Asm6502Bundle.message("RUN.error.binary.missing", binary.getPath()));
        }
        File labelFile = Ca65BuildPaths.labelFile(basePath, spec);
        EmulatorLaunchRequest request = new EmulatorLaunchRequest(system, binary.getAbsolutePath(),
                labelFile.isFile() ? labelFile.getAbsolutePath() : null,
                myEmulatorExecutable, myEmulatorExtraArgs);
        return provider.createCommandLine(request).withWorkDirectory(basePath);
    }

    @Override
    public RunConfiguration clone() {
        Ca65RunConfiguration clone = (Ca65RunConfiguration) super.clone();
        clone.myBuildSpec = myBuildSpec.copy();
        return clone;
    }

    @Override
    public void readExternal(@NotNull Element element) throws InvalidDataException {
        super.readExternal(element);
        XmlSerializer.deserializeInto(myBuildSpec, element);
        String emulator = JDOMExternalizerUtil.readField(element, EMULATOR);
        if (emulator != null) {
            myEmulatorId = emulator;
        }
        myEmulatorExecutable = JDOMExternalizerUtil.readField(element, EMULATOR_EXECUTABLE, "");
        myEmulatorExtraArgs = JDOMExternalizerUtil.readField(element, EMULATOR_ARGS, "");
    }

    @Override
    public void writeExternal(@NotNull Element element) throws WriteExternalException {
        super.writeExternal(element);
        XmlSerializer.serializeInto(myBuildSpec, element);
        JDOMExternalizerUtil.writeField(element, EMULATOR, myEmulatorId);
        JDOMExternalizerUtil.writeField(element, EMULATOR_EXECUTABLE, myEmulatorExecutable);
        JDOMExternalizerUtil.writeField(element, EMULATOR_ARGS, myEmulatorExtraArgs);
    }
}
