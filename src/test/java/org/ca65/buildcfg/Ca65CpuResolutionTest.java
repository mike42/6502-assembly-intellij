package org.ca65.buildcfg;

import com.intellij.testFramework.fixtures.BasePlatformTestCase;
import org.ca65.config.AsmConfiguration;
import org.ca65.helpers.Cpu;

import java.util.List;

/**
 * The editor CPU is layered: active build configuration first, the manual
 * project setting when no configurations exist.
 */
public class Ca65CpuResolutionTest extends BasePlatformTestCase {

    @Override
    protected void tearDown() throws Exception {
        try {
            Ca65BuildSettings.getInstance(getProject()).setConfigurations(List.of());
            Ca65ActiveConfiguration.getInstance(getProject()).setActiveConfigurationName("");
        } finally {
            super.tearDown();
        }
    }

    public void testManualCpuUsedWithoutBuildConfigurations() {
        AsmConfiguration configuration = AsmConfiguration.getInstance(getProject());
        configuration.setCpu(Cpu.CPU_65C816);
        assertEquals(Cpu.CPU_65C816, configuration.getCpu());
    }

    public void testActiveBuildConfigurationOverridesManualCpu() {
        AsmConfiguration configuration = AsmConfiguration.getInstance(getProject());
        configuration.setCpu(Cpu.CPU_6502);

        Ca65BuildConfiguration mega65 = new Ca65BuildConfiguration();
        mega65.name = "mega65";
        mega65.targetSystem = Ca65TargetSystem.MEGA65.id;
        Ca65BuildSettings.getInstance(getProject()).setConfigurations(List.of(mega65));

        // 45GS02 (MEGA65 default) maps onto the editor's 65C02 model.
        assertEquals(Cpu.CPU_65C02, configuration.getCpu());
        assertEquals(Cpu.CPU_6502, configuration.getManualCpu());
    }

    public void testFallsBackToFirstConfigurationWhenStoredNameIsStale() {
        Ca65BuildConfiguration first = new Ca65BuildConfiguration();
        first.name = "first";
        first.targetSystem = "";
        first.customConfigFile = "x.cfg";
        first.cpu = "65816";
        Ca65BuildSettings.getInstance(getProject()).setConfigurations(List.of(first));
        Ca65ActiveConfiguration.getInstance(getProject()).setActiveConfigurationName("deleted-config");

        Ca65BuildConfiguration active =
                Ca65ActiveConfiguration.getInstance(getProject()).getActiveConfiguration();
        assertNotNull(active);
        assertEquals("first", active.name);
        assertEquals(Cpu.CPU_65C816, AsmConfiguration.getInstance(getProject()).getCpu());
    }
}
