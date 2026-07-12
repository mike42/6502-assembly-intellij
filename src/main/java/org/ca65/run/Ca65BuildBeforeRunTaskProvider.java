package org.ca65.run;

import com.intellij.execution.BeforeRunTask;
import com.intellij.execution.BeforeRunTaskProvider;
import com.intellij.execution.configurations.RunConfiguration;
import com.intellij.execution.runners.ExecutionEnvironment;
import com.intellij.icons.AllIcons;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.progress.EmptyProgressIndicator;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.util.Key;
import org.ca65.Asm6502Bundle;
import org.ca65.buildcfg.Ca65BuildConfiguration;
import org.ca65.buildcfg.Ca65BuildRunner;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;

/**
 * The default before-run step of {@link Ca65RunConfiguration}: build the
 * referenced build configuration and abort the launch if it fails.
 */
public class Ca65BuildBeforeRunTaskProvider
        extends BeforeRunTaskProvider<Ca65BuildBeforeRunTaskProvider.Task> {
    public static final Key<Task> ID = Key.create("Ca65.BuildBeforeRun");

    public static class Task extends BeforeRunTask<Task> {
        Task() {
            super(ID);
            setEnabled(true);
        }
    }

    @Override
    public Key<Task> getId() {
        return ID;
    }

    @Override
    public String getName() {
        return Asm6502Bundle.message("BEFORERUN.build.name");
    }

    @Override
    public @Nullable Icon getIcon() {
        return AllIcons.Actions.Compile;
    }

    @Override
    public @Nullable Task createTask(@NotNull RunConfiguration runConfiguration) {
        return runConfiguration instanceof Ca65RunConfiguration ? new Task() : null;
    }

    @Override
    public boolean executeTask(@NotNull DataContext context, @NotNull RunConfiguration configuration,
                               @NotNull ExecutionEnvironment environment, @NotNull Task task) {
        if (!(configuration instanceof Ca65RunConfiguration runConfiguration)) {
            return true;
        }
        Ca65BuildConfiguration buildConfig = runConfiguration.findBuildConfiguration();
        if (buildConfig == null) {
            return false;
        }
        ProgressIndicator indicator = ProgressManager.getInstance().getProgressIndicator();
        if (indicator == null) {
            indicator = new EmptyProgressIndicator();
        }
        return Ca65BuildRunner.runBuild(environment.getProject(), buildConfig, indicator);
    }
}
