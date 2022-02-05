package org.ca65.config;

import com.intellij.openapi.components.PersistentStateComponent;
import com.intellij.openapi.components.State;
import com.intellij.openapi.components.Storage;
import com.intellij.openapi.project.Project;
import com.intellij.util.xmlb.annotations.XMap;
import org.ca65.helpers.Cpu;
import org.jetbrains.annotations.NotNull;

@State(name = "AsmConfiguration",
        storages = @Storage("asm_6502.xml"))
public class AsmConfiguration implements PersistentStateComponent<AsmConfiguration.State> {
    public static class State {
        @XMap(entryTagName = "cpu")
        public String cpu;
    }

    private AsmConfiguration.State myState = new State();

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

    public Cpu getCpu() {
        for(Cpu item : Cpu.values()) {
            if(item.toString().equals(this.myState.cpu)) {
                return item;
            }
        }
        // Default to classic 6502
        return Cpu.CPU_6502;
    }

    public void setCpu(Cpu newCpu) {
        this.myState.cpu = newCpu.toString();
    }
}
