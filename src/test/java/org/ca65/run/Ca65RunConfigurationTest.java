package org.ca65.run;

import com.intellij.execution.configurations.GeneralCommandLine;
import com.intellij.execution.configurations.RuntimeConfigurationException;
import com.intellij.testFramework.fixtures.BasePlatformTestCase;
import org.ca65.buildcfg.Ca65BuildConfiguration;
import org.ca65.buildcfg.Ca65BuildPaths;
import org.ca65.buildcfg.Ca65TargetSystem;

import java.io.File;
import java.nio.file.Files;
import java.util.List;

public class Ca65RunConfigurationTest extends BasePlatformTestCase {

    private Ca65RunConfiguration newC64Configuration() {
        Ca65RunConfiguration configuration = new Ca65RunConfiguration(getProject(),
                Ca65RunConfigurationType.getInstance().getConfigurationFactories()[0]);
        configuration.setName("hello");
        Ca65BuildConfiguration spec = configuration.getBuildSpec();
        spec.sources = List.of("hello.s");
        spec.targetSystem = Ca65TargetSystem.C64.id;
        return configuration;
    }

    public void testViceCommandLineIncludesAutostartAndLabels() throws Exception {
        Ca65RunConfiguration configuration = newC64Configuration();
        configuration.setEmulatorExtraArgs("-sounddev dummy");
        String basePath = getProject().getBasePath();
        assertNotNull(basePath);
        File binary = Ca65BuildPaths.binaryFile(basePath, configuration.getBuildSpec());
        File labels = Ca65BuildPaths.labelFile(basePath, configuration.getBuildSpec());
        assertTrue(binary.getParentFile().isDirectory() || binary.getParentFile().mkdirs());
        Files.write(binary.toPath(), new byte[]{0x01, 0x08});
        Files.write(labels.toPath(), "al C:080e .start\n".getBytes());

        GeneralCommandLine commandLine = configuration.createEmulatorCommandLine();

        assertEquals("x64sc", commandLine.getExePath());
        List<String> parameters = commandLine.getParametersList().getList();
        assertEquals(List.of("-sounddev", "dummy",
                "-moncommands", labels.getAbsolutePath(),
                "-autostart", binary.getAbsolutePath()), parameters);
    }

    public void testValidationRequiresSources() {
        Ca65RunConfiguration configuration = newC64Configuration();
        configuration.getBuildSpec().sources = List.of();
        try {
            configuration.checkConfiguration();
            fail("expected validation error");
        } catch (RuntimeConfigurationException expected) {
            // no sources configured
        }
    }

    public void testValidationRejectsUnsupportedTarget() {
        Ca65RunConfiguration configuration = newC64Configuration();
        configuration.getBuildSpec().targetSystem = Ca65TargetSystem.NES.id;
        try {
            configuration.checkConfiguration();
            fail("expected validation error: VICE does not support the NES");
        } catch (RuntimeConfigurationException expected) {
            // unsupported target
        }
    }

    public void testValidationAcceptsViceOnC64() throws Exception {
        newC64Configuration().checkConfiguration();
    }

    public void testBuildOnlyAllowsCustomLinkerConfig() throws Exception {
        Ca65RunConfiguration configuration = newC64Configuration();
        configuration.setEmulatorId(Ca65RunConfiguration.EMULATOR_NONE);
        Ca65BuildConfiguration spec = configuration.getBuildSpec();
        spec.targetSystem = "";
        spec.customConfigFile = "conf/game.cfg";
        configuration.checkConfiguration();
        assertTrue(configuration.isBuildOnly());
        assertFalse(configuration.isDebuggable());
    }

    public void testCustomLinkerConfigCannotRunOnEmulator() {
        Ca65RunConfiguration configuration = newC64Configuration();
        Ca65BuildConfiguration spec = configuration.getBuildSpec();
        spec.targetSystem = "";
        spec.customConfigFile = "conf/game.cfg";
        try {
            configuration.checkConfiguration();
            fail("expected validation error: custom target on an emulator");
        } catch (RuntimeConfigurationException expected) {
            // custom linker config cannot be emulated
        }
    }

    public void testCloneDoesNotShareBuildSpec() {
        Ca65RunConfiguration configuration = newC64Configuration();
        Ca65RunConfiguration clone = (Ca65RunConfiguration) configuration.clone();
        clone.getBuildSpec().targetSystem = Ca65TargetSystem.NES.id;
        assertEquals(Ca65TargetSystem.C64.id, configuration.getBuildSpec().targetSystem);
    }
}
