package org.ca65.run;

import com.intellij.codeInsight.daemon.DaemonCodeAnalyzer;
import com.intellij.execution.RunManagerListener;
import com.intellij.execution.RunnerAndConfigurationSettings;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * The editor derives the assumed CPU (and thus highlighting of
 * CPU-specific mnemonics) from the selected run configuration, so
 * re-analyze open files whenever that selection or its settings change.
 */
public class Ca65RunConfigurationListener implements RunManagerListener {
    private final Project myProject;

    public Ca65RunConfigurationListener(@NotNull Project project) {
        myProject = project;
    }

    @Override
    public void runConfigurationSelected(@Nullable RunnerAndConfigurationSettings settings) {
        restartAnalyzer();
    }

    @Override
    public void runConfigurationChanged(@NotNull RunnerAndConfigurationSettings settings) {
        if (settings.getConfiguration() instanceof Ca65RunConfiguration) {
            restartAnalyzer();
        }
    }

    @Override
    public void runConfigurationRemoved(@NotNull RunnerAndConfigurationSettings settings) {
        if (settings.getConfiguration() instanceof Ca65RunConfiguration) {
            restartAnalyzer();
        }
    }

    private void restartAnalyzer() {
        if (!myProject.isDisposed()) {
            DaemonCodeAnalyzer.getInstance(myProject).restart();
        }
    }
}
