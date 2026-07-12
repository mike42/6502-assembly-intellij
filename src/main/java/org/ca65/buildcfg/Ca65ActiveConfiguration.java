package org.ca65.buildcfg;

import com.intellij.codeInsight.daemon.DaemonCodeAnalyzer;
import com.intellij.openapi.components.PersistentStateComponent;
import com.intellij.openapi.components.State;
import com.intellij.openapi.components.Storage;
import com.intellij.openapi.components.StoragePathMacros;
import com.intellij.openapi.project.Project;
import com.intellij.util.messages.Topic;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Objects;

/**
 * Which build configuration is currently active. Per-user state
 * (workspace.xml), like the platform's selected run configuration.
 */
@State(name = "Ca65ActiveConfiguration", storages = @Storage(StoragePathMacros.WORKSPACE_FILE))
public class Ca65ActiveConfiguration implements PersistentStateComponent<Ca65ActiveConfiguration.State> {
    /** Fired on the project message bus when the active configuration changes. */
    public interface Listener {
        Topic<Listener> TOPIC = Topic.create("ca65 active build configuration", Listener.class);

        void activeConfigurationChanged();
    }

    public static class State {
        public String activeConfigurationName = "";
    }

    private State myState = new State();
    private final Project myProject;

    public Ca65ActiveConfiguration(@NotNull Project project) {
        myProject = project;
    }

    public static Ca65ActiveConfiguration getInstance(@NotNull Project project) {
        return project.getService(Ca65ActiveConfiguration.class);
    }

    @Override
    public State getState() {
        return myState;
    }

    @Override
    public void loadState(@NotNull State state) {
        myState = state;
    }

    /**
     * The active configuration: the stored selection if it still exists,
     * otherwise the project's first configuration, otherwise null.
     */
    @Nullable
    public Ca65BuildConfiguration getActiveConfiguration() {
        Ca65BuildSettings settings = Ca65BuildSettings.getInstance(myProject);
        Ca65BuildConfiguration stored = settings.findByName(myState.activeConfigurationName);
        if (stored != null) {
            return stored;
        }
        List<Ca65BuildConfiguration> all = settings.getConfigurations();
        return all.isEmpty() ? null : all.get(0);
    }

    public void setActiveConfigurationName(@Nullable String name) {
        String newName = name == null ? "" : name;
        if (Objects.equals(myState.activeConfigurationName, newName)) {
            return;
        }
        myState.activeConfigurationName = newName;
        notifyChanged();
    }

    /**
     * Re-highlights open files and tells listeners (e.g. the status bar
     * widget) that the active configuration — and possibly the CPU the
     * editor should assume — changed. Also called by the settings UI after
     * editing configurations in place.
     */
    public void notifyChanged() {
        if (myProject.isDisposed()) {
            return;
        }
        myProject.getMessageBus().syncPublisher(Listener.TOPIC).activeConfigurationChanged();
        DaemonCodeAnalyzer.getInstance(myProject).restart();
    }
}
