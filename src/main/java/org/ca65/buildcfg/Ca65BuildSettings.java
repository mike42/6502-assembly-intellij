package org.ca65.buildcfg;

import com.intellij.openapi.components.PersistentStateComponent;
import com.intellij.openapi.components.State;
import com.intellij.openapi.components.Storage;
import com.intellij.openapi.project.Project;
import com.intellij.util.xmlb.annotations.XCollection;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * The project's build configurations (shared, checked into VCS with the
 * rest of .idea). Which one is <em>active</em> is per-user state and lives
 * in {@link Ca65ActiveConfiguration}.
 */
@State(name = "Ca65BuildSettings", storages = @Storage("ca65-build.xml"))
public class Ca65BuildSettings implements PersistentStateComponent<Ca65BuildSettings.State> {
    public static class State {
        @XCollection(propertyElementName = "configurations")
        public List<Ca65BuildConfiguration> configurations = new ArrayList<>();
    }

    private State myState = new State();

    public static Ca65BuildSettings getInstance(@NotNull Project project) {
        return project.getService(Ca65BuildSettings.class);
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
    public List<Ca65BuildConfiguration> getConfigurations() {
        return myState.configurations;
    }

    public void setConfigurations(@NotNull List<Ca65BuildConfiguration> configurations) {
        myState.configurations = new ArrayList<>(configurations);
    }

    @Nullable
    public Ca65BuildConfiguration findByName(@Nullable String name) {
        if (name == null || name.isEmpty()) {
            return null;
        }
        for (Ca65BuildConfiguration configuration : myState.configurations) {
            if (name.equals(configuration.name)) {
                return configuration;
            }
        }
        return null;
    }
}
