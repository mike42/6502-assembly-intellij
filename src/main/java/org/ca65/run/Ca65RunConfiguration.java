package org.ca65.run;

import com.intellij.execution.ExecutionException;
import com.intellij.execution.Executor;
import com.intellij.execution.configurations.CommandLineState;
import com.intellij.execution.configurations.ConfigurationFactory;
import com.intellij.execution.configurations.GeneralCommandLine;
import com.intellij.execution.configurations.RunConfigurationBase;
import com.intellij.execution.configurations.RunProfileState;
import com.intellij.execution.configurations.RuntimeConfigurationError;
import com.intellij.execution.configurations.RuntimeConfigurationException;
import com.intellij.execution.process.KillableProcessHandler;
import com.intellij.execution.process.ProcessHandler;
import com.intellij.execution.process.ProcessTerminatedListener;
import com.intellij.execution.runners.ExecutionEnvironment;
import com.intellij.openapi.options.SettingsEditor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.InvalidDataException;
import com.intellij.openapi.util.JDOMExternalizerUtil;
import com.intellij.openapi.util.WriteExternalException;
import org.ca65.Asm6502Bundle;
import org.ca65.buildcfg.Ca65BuildConfiguration;
import org.ca65.buildcfg.Ca65BuildPaths;
import org.ca65.buildcfg.Ca65BuildSettings;
import org.ca65.buildcfg.Ca65TargetSystem;
import org.jdom.Element;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;

/**
 * Runs the binary produced by a build configuration on an emulator chosen
 * from the providers that support the build's target system.
 */
public class Ca65RunConfiguration extends RunConfigurationBase<Object> {
    private static final String BUILD_CONFIGURATION = "buildConfiguration";
    private static final String EMULATOR = "emulator";
    private static final String EMULATOR_EXECUTABLE = "emulatorExecutable";
    private static final String EMULATOR_ARGS = "emulatorArgs";

    private String myBuildConfigurationName = "";
    private String myEmulatorId = ViceEmulatorProvider.ID;
    private String myEmulatorExecutable = "";
    private String myEmulatorExtraArgs = "";

    protected Ca65RunConfiguration(@NotNull Project project, @NotNull ConfigurationFactory factory) {
        super(project, factory, "");
    }

    @NotNull
    public String getBuildConfigurationName() {
        return myBuildConfigurationName;
    }

    public void setBuildConfigurationName(@NotNull String name) {
        myBuildConfigurationName = name;
    }

    @NotNull
    public String getEmulatorId() {
        return myEmulatorId;
    }

    public void setEmulatorId(@NotNull String emulatorId) {
        myEmulatorId = emulatorId;
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

    @Nullable
    public Ca65BuildConfiguration findBuildConfiguration() {
        return Ca65BuildSettings.getInstance(getProject()).findByName(myBuildConfigurationName);
    }

    /**
     * Whether the Debug executor applies: the emulator must speak a debug
     * protocol for this target and the build must produce the debug info
     * file that maps addresses to source lines.
     */
    public boolean isDebuggable() {
        Ca65BuildConfiguration buildConfig = findBuildConfiguration();
        if (buildConfig == null || !buildConfig.generateDbgFile) {
            return false;
        }
        Ca65TargetSystem system = buildConfig.getTargetSystemEnum();
        EmulatorProvider provider = EmulatorProvider.findById(myEmulatorId);
        return system != null && provider != null && provider.supportsDebugging(system);
    }

    @Override
    public @NotNull SettingsEditor<? extends RunConfigurationBase<?>> getConfigurationEditor() {
        return new Ca65RunConfigurationEditor(getProject());
    }

    @Override
    public void checkConfiguration() throws RuntimeConfigurationException {
        Ca65BuildConfiguration buildConfig = findBuildConfiguration();
        if (buildConfig == null) {
            throw new RuntimeConfigurationError(Asm6502Bundle.message("RUN.error.no.build.config"));
        }
        Ca65TargetSystem system = buildConfig.getTargetSystemEnum();
        if (system == null) {
            throw new RuntimeConfigurationError(Asm6502Bundle.message("RUN.error.custom.target"));
        }
        EmulatorProvider provider = EmulatorProvider.findById(myEmulatorId);
        if (provider == null) {
            throw new RuntimeConfigurationError(Asm6502Bundle.message("RUN.error.no.emulator"));
        }
        if (!provider.supports(system)) {
            throw new RuntimeConfigurationError(Asm6502Bundle.message("RUN.error.emulator.unsupported",
                    provider.getDisplayName(), system.displayName));
        }
    }

    @Override
    public @Nullable RunProfileState getState(@NotNull Executor executor,
                                              @NotNull ExecutionEnvironment environment) {
        return new CommandLineState(environment) {
            @Override
            protected @NotNull ProcessHandler startProcess() throws ExecutionException {
                GeneralCommandLine commandLine = createEmulatorCommandLine();
                KillableProcessHandler handler = new KillableProcessHandler(commandLine);
                ProcessTerminatedListener.attach(handler);
                return handler;
            }
        };
    }

    @NotNull
    GeneralCommandLine createEmulatorCommandLine() throws ExecutionException {
        Ca65BuildConfiguration buildConfig = findBuildConfiguration();
        if (buildConfig == null) {
            throw new ExecutionException(Asm6502Bundle.message("RUN.error.no.build.config"));
        }
        Ca65TargetSystem system = buildConfig.getTargetSystemEnum();
        if (system == null) {
            throw new ExecutionException(Asm6502Bundle.message("RUN.error.custom.target"));
        }
        EmulatorProvider provider = EmulatorProvider.findById(myEmulatorId);
        if (provider == null || !provider.supports(system)) {
            throw new ExecutionException(Asm6502Bundle.message("RUN.error.no.emulator"));
        }
        String basePath = getProject().getBasePath();
        if (basePath == null) {
            throw new ExecutionException(Asm6502Bundle.message("RUN.error.no.build.config"));
        }
        File binary = Ca65BuildPaths.binaryFile(basePath, buildConfig);
        if (!binary.isFile()) {
            throw new ExecutionException(Asm6502Bundle.message("RUN.error.binary.missing", binary.getPath()));
        }
        File labelFile = Ca65BuildPaths.labelFile(basePath, buildConfig);
        EmulatorLaunchRequest request = new EmulatorLaunchRequest(system, binary.getAbsolutePath(),
                labelFile.isFile() ? labelFile.getAbsolutePath() : null,
                myEmulatorExecutable, myEmulatorExtraArgs);
        return provider.createCommandLine(request).withWorkDirectory(basePath);
    }

    @Override
    public void readExternal(@NotNull Element element) throws InvalidDataException {
        super.readExternal(element);
        myBuildConfigurationName = JDOMExternalizerUtil.readField(element, BUILD_CONFIGURATION, "");
        myEmulatorId = JDOMExternalizerUtil.readField(element, EMULATOR, ViceEmulatorProvider.ID);
        myEmulatorExecutable = JDOMExternalizerUtil.readField(element, EMULATOR_EXECUTABLE, "");
        myEmulatorExtraArgs = JDOMExternalizerUtil.readField(element, EMULATOR_ARGS, "");
    }

    @Override
    public void writeExternal(@NotNull Element element) throws WriteExternalException {
        super.writeExternal(element);
        JDOMExternalizerUtil.writeField(element, BUILD_CONFIGURATION, myBuildConfigurationName);
        JDOMExternalizerUtil.writeField(element, EMULATOR, myEmulatorId);
        JDOMExternalizerUtil.writeField(element, EMULATOR_EXECUTABLE, myEmulatorExecutable);
        JDOMExternalizerUtil.writeField(element, EMULATOR_ARGS, myEmulatorExtraArgs);
    }
}
