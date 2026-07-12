package org.ca65.run;

import com.intellij.execution.configurations.GeneralCommandLine;
import com.intellij.execution.configurations.RuntimeConfigurationException;
import com.intellij.testFramework.fixtures.BasePlatformTestCase;
import org.ca65.buildcfg.Ca65BuildConfiguration;
import org.ca65.buildcfg.Ca65BuildPaths;
import org.ca65.buildcfg.Ca65BuildSettings;
import org.ca65.buildcfg.Ca65TargetSystem;

import java.io.File;
import java.nio.file.Files;
import java.util.List;

public class Ca65RunConfigurationTest extends BasePlatformTestCase {

    @Override
    protected void tearDown() throws Exception {
        try {
            Ca65BuildSettings.getInstance(getProject()).setConfigurations(List.of());
        } finally {
            super.tearDown();
        }
    }

    private Ca65BuildConfiguration installC64BuildConfig() {
        Ca65BuildConfiguration buildConfig = new Ca65BuildConfiguration();
        buildConfig.name = "hello";
        buildConfig.sources = List.of("hello.s");
        buildConfig.targetSystem = Ca65TargetSystem.C64.id;
        Ca65BuildSettings.getInstance(getProject()).setConfigurations(List.of(buildConfig));
        return buildConfig;
    }

    private Ca65RunConfiguration newRunConfiguration() {
        return new Ca65RunConfiguration(getProject(),
                Ca65RunConfigurationType.getInstance().getConfigurationFactories()[0]);
    }

    public void testViceCommandLineIncludesAutostartAndLabels() throws Exception {
        Ca65BuildConfiguration buildConfig = installC64BuildConfig();
        String basePath = getProject().getBasePath();
        assertNotNull(basePath);
        File binary = Ca65BuildPaths.binaryFile(basePath, buildConfig);
        File labels = Ca65BuildPaths.labelFile(basePath, buildConfig);
        assertTrue(binary.getParentFile().isDirectory() || binary.getParentFile().mkdirs());
        Files.write(binary.toPath(), new byte[]{0x01, 0x08});
        Files.write(labels.toPath(), "al C:080e .start\n".getBytes());

        Ca65RunConfiguration runConfiguration = newRunConfiguration();
        runConfiguration.setBuildConfigurationName("hello");
        runConfiguration.setEmulatorExtraArgs("-sounddev dummy");
        GeneralCommandLine commandLine = runConfiguration.createEmulatorCommandLine();

        assertEquals("x64sc", commandLine.getExePath());
        List<String> parameters = commandLine.getParametersList().getList();
        assertEquals(List.of("-sounddev", "dummy",
                "-moncommands", labels.getAbsolutePath(),
                "-autostart", binary.getAbsolutePath()), parameters);
    }

    public void testValidationRequiresBuildConfiguration() {
        Ca65RunConfiguration runConfiguration = newRunConfiguration();
        try {
            runConfiguration.checkConfiguration();
            fail("expected validation error");
        } catch (RuntimeConfigurationException expected) {
            // no build configuration selected
        }
    }

    public void testValidationRejectsUnsupportedTarget() {
        Ca65BuildConfiguration buildConfig = installC64BuildConfig();
        buildConfig.targetSystem = Ca65TargetSystem.NES.id;
        Ca65RunConfiguration runConfiguration = newRunConfiguration();
        runConfiguration.setBuildConfigurationName("hello");
        try {
            runConfiguration.checkConfiguration();
            fail("expected validation error: VICE does not support the NES");
        } catch (RuntimeConfigurationException expected) {
            // unsupported target
        }
    }

    public void testValidationAcceptsViceOnC64() throws Exception {
        installC64BuildConfig();
        Ca65RunConfiguration runConfiguration = newRunConfiguration();
        runConfiguration.setBuildConfigurationName("hello");
        runConfiguration.checkConfiguration();
    }
}
