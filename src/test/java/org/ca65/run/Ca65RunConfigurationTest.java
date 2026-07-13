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

    public void testCustomLinkerConfigOnBuiltinTargetCanRunOnEmulator() throws Exception {
        Ca65RunConfiguration configuration = newC64Configuration();
        configuration.getBuildSpec().customConfigFile = "conf/game.cfg";
        configuration.checkConfiguration();
    }

    private Ca65RunConfiguration newNesConfiguration() {
        Ca65RunConfiguration configuration = newC64Configuration();
        Ca65BuildConfiguration spec = configuration.getBuildSpec();
        spec.targetSystem = Ca65TargetSystem.NES.id;
        spec.customConfigFile = "table_tennis.cfg";
        configuration.setEmulatorId(MameEmulatorProvider.ID);
        return configuration;
    }

    public void testMameCommandLineLoadsCartridge() throws Exception {
        Ca65RunConfiguration configuration = newNesConfiguration();
        String basePath = getProject().getBasePath();
        assertNotNull(basePath);
        File binary = Ca65BuildPaths.binaryFile(basePath, configuration.getBuildSpec());
        assertTrue(binary.getParentFile().isDirectory() || binary.getParentFile().mkdirs());
        Files.write(binary.toPath(), new byte[]{0x4e, 0x45, 0x53, 0x1a});

        GeneralCommandLine commandLine = configuration.createEmulatorCommandLine();

        assertEquals("mame", commandLine.getExePath());
        assertEquals(List.of("famicomo", "-window", "-nomouse",
                "-cart", binary.getAbsolutePath()), commandLine.getParametersList().getList());
    }

    public void testMameDebugCommandLineEnablesGdbStub() throws Exception {
        EmulatorProvider provider = EmulatorProvider.findById(MameEmulatorProvider.ID);
        assertNotNull(provider);
        EmulatorLaunchRequest request = new EmulatorLaunchRequest(Ca65TargetSystem.NES,
                "/tmp/game.nes", null, "", "");
        GeneralCommandLine commandLine = provider.createDebugCommandLine(request, 2159);
        assertEquals(List.of("famicomo", "-window", "-nomouse",
                "-debug", "-debugger", "gdbstub",
                "-debugger_port", "2159", "-debugger_host", "127.0.0.1",
                "-cart", "/tmp/game.nes"), commandLine.getParametersList().getList());
    }

    public void testMameOnNesIsDebuggable() {
        Ca65RunConfiguration configuration = newNesConfiguration();
        assertTrue(configuration.isDebuggable());
    }

    public void testMameSkipsSoftKillOnStop() {
        EmulatorProvider mame = EmulatorProvider.findById(MameEmulatorProvider.ID);
        assertNotNull(mame);
        assertFalse("MAME ignores SIGINT; stop must not wait on a soft kill", mame.supportsSoftKill());
        EmulatorProvider vice = EmulatorProvider.findById(ViceEmulatorProvider.ID);
        assertNotNull(vice);
        assertTrue(vice.supportsSoftKill());
    }

    public void testTargetSystemNarrowsEmulators() {
        List<String> forNes = EmulatorProvider.forSystem(Ca65TargetSystem.NES)
                .stream().map(EmulatorProvider::getId).toList();
        assertEquals(List.of(MameEmulatorProvider.ID), forNes);
        List<String> forC64 = EmulatorProvider.forSystem(Ca65TargetSystem.C64)
                .stream().map(EmulatorProvider::getId).toList();
        assertEquals(List.of(ViceEmulatorProvider.ID), forC64);
    }

    public void testCloneDoesNotShareBuildSpec() {
        Ca65RunConfiguration configuration = newC64Configuration();
        Ca65RunConfiguration clone = (Ca65RunConfiguration) configuration.clone();
        clone.getBuildSpec().targetSystem = Ca65TargetSystem.NES.id;
        assertEquals(Ca65TargetSystem.C64.id, configuration.getBuildSpec().targetSystem);
    }
}
