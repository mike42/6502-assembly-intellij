package org.ca65.buildcfg;

import com.intellij.execution.RunManager;
import com.intellij.execution.RunnerAndConfigurationSettings;
import com.intellij.testFramework.fixtures.BasePlatformTestCase;
import org.ca65.config.AsmConfiguration;
import org.ca65.helpers.Cpu;
import org.ca65.run.Ca65RunConfiguration;
import org.ca65.run.Ca65RunConfigurationType;

import java.util.ArrayList;
import java.util.List;

/**
 * The editor CPU is layered: the selected 6502 run configuration first,
 * the manual project setting when none is selected.
 */
public class Ca65CpuResolutionTest extends BasePlatformTestCase {
    private final List<RunnerAndConfigurationSettings> myAdded = new ArrayList<>();

    @Override
    protected void tearDown() throws Exception {
        try {
            RunManager runManager = RunManager.getInstance(getProject());
            runManager.setSelectedConfiguration(null);
            for (RunnerAndConfigurationSettings settings : myAdded) {
                runManager.removeConfiguration(settings);
            }
            myAdded.clear();
        } finally {
            super.tearDown();
        }
    }

    private RunnerAndConfigurationSettings addConfiguration(String name, String targetSystem, String cpu) {
        RunManager runManager = RunManager.getInstance(getProject());
        RunnerAndConfigurationSettings settings = runManager.createConfiguration(name,
                Ca65RunConfigurationType.getInstance().getConfigurationFactories()[0]);
        Ca65BuildConfiguration spec = ((Ca65RunConfiguration) settings.getConfiguration()).getBuildSpec();
        spec.targetSystem = targetSystem;
        spec.cpu = cpu;
        runManager.addConfiguration(settings);
        myAdded.add(settings);
        return settings;
    }

    public void testManualCpuUsedWithoutSelectedConfiguration() {
        AsmConfiguration configuration = AsmConfiguration.getInstance(getProject());
        configuration.setCpu(Cpu.CPU_65C816);
        assertEquals(Cpu.CPU_65C816, configuration.getCpu());
    }

    public void testSelectedRunConfigurationOverridesManualCpu() {
        AsmConfiguration configuration = AsmConfiguration.getInstance(getProject());
        configuration.setCpu(Cpu.CPU_6502);

        RunnerAndConfigurationSettings mega65 =
                addConfiguration("mega65", Ca65TargetSystem.MEGA65.id, "");
        RunManager.getInstance(getProject()).setSelectedConfiguration(mega65);

        // 45GS02 (MEGA65 default) maps onto the editor's 65C02 model.
        assertEquals(Cpu.CPU_65C02, configuration.getCpu());
        assertEquals(Cpu.CPU_6502, configuration.getManualCpu());
    }

    public void testExplicitCpuOnSelectedConfigurationWins() {
        RunnerAndConfigurationSettings custom = addConfiguration("custom", "", "65816");
        RunManager.getInstance(getProject()).setSelectedConfiguration(custom);
        assertEquals(Cpu.CPU_65C816, AsmConfiguration.getInstance(getProject()).getCpu());
    }
}
