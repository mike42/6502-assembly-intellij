package org.ca65.config;

import com.intellij.execution.RunManager;
import com.intellij.execution.RunnerAndConfigurationSettings;
import com.intellij.openapi.components.PersistentStateComponent;
import com.intellij.openapi.components.State;
import com.intellij.openapi.components.Storage;
import com.intellij.openapi.project.Project;
import com.intellij.util.xmlb.annotations.XMap;
import org.ca65.buildcfg.Ca65TargetSystem;
import org.ca65.helpers.Cpu;
import org.ca65.run.Ca65RunConfiguration;
import org.jetbrains.annotations.NotNull;

@State(name = "AsmConfiguration",
        storages = @Storage("asm_6502.xml"))
public class AsmConfiguration implements PersistentStateComponent<AsmConfiguration.State> {
    public static class State {
        @XMap(entryTagName = "cpu")
        public String cpu;

        @XMap(entryTagName = "referenceChecking")
        public boolean referenceChecking = true;
    }

    private AsmConfiguration.State myState = new State();
    private final Project myProject;

    public AsmConfiguration(Project project) {
        myProject = project;
    }

    public static AsmConfiguration getInstance(Project project) {
        return project.getService(AsmConfiguration.class);
    }

    @Override
    public AsmConfiguration.State getState() {
        return myState;
    }

    @Override
    public void loadState(@NotNull AsmConfiguration.State state) {
        myState = state;
    }

    /**
     * The CPU the editor should assume, layered: the selected run
     * configuration's CPU when it is a 6502 one, else the manual value.
     */
    public Cpu getCpu() {
        if (myProject != null && !myProject.isDisposed()) {
            RunnerAndConfigurationSettings selected =
                    RunManager.getInstance(myProject).getSelectedConfiguration();
            if (selected != null && selected.getConfiguration() instanceof Ca65RunConfiguration runConfig) {
                return Ca65TargetSystem.editorCpuFor(runConfig.getBuildSpec().getEffectiveCpuName());
            }
        }
        return getManualCpu();
    }

    /** The manually chosen CPU, ignoring build configurations. */
    public Cpu getManualCpu() {
        for (Cpu item : Cpu.values()) {
            if (item.toString().equals(this.myState.cpu)) {
                return item;
            }
        }
        // Default to classic 6502
        return Cpu.CPU_6502;
    }

    public void setCpu(Cpu newCpu) {
        this.myState.cpu = newCpu.toString();
    }

    public boolean isReferenceCheckingEnabled() {
        return this.myState.referenceChecking;
    }

    public void setReferenceCheckingEnabled(boolean enabled) {
        this.myState.referenceChecking = enabled;
    }
}
