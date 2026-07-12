package org.ca65.buildcfg;

import com.intellij.openapi.actionSystem.ActionUpdateThread;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.Task;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.project.Project;
import org.ca65.Asm6502Bundle;
import org.jetbrains.annotations.NotNull;

/**
 * "Build 6502 Project": builds the active build configuration in the
 * background, reporting to the Build tool window.
 */
public class Ca65BuildAction extends AnAction implements DumbAware {

    @Override
    public @NotNull ActionUpdateThread getActionUpdateThread() {
        return ActionUpdateThread.BGT;
    }

    @Override
    public void update(@NotNull AnActionEvent e) {
        Project project = e.getProject();
        Ca65BuildConfiguration config = project == null ? null
                : Ca65ActiveConfiguration.getInstance(project).getActiveConfiguration();
        e.getPresentation().setEnabled(config != null);
        // On the main toolbar, only appear once the project actually has a
        // 6502 build configuration; in menus stay visible (but disabled) for
        // discoverability. This plugin loads in every JetBrains IDE, so an
        // always-on toolbar button would clutter unrelated projects.
        e.getPresentation().setVisible(config != null || !e.isFromActionToolbar());
        if (config != null) {
            e.getPresentation().setText(Asm6502Bundle.message("ACTION.build.name.config", config.name));
        } else {
            e.getPresentation().setText(Asm6502Bundle.message("ACTION.build.name"));
        }
    }

    @Override
    public void actionPerformed(@NotNull AnActionEvent e) {
        Project project = e.getProject();
        if (project == null) {
            return;
        }
        Ca65BuildConfiguration config = Ca65ActiveConfiguration.getInstance(project).getActiveConfiguration();
        if (config == null) {
            return;
        }
        new Task.Backgroundable(project, Asm6502Bundle.message("BUILD.title", config.name), true) {
            @Override
            public void run(@NotNull ProgressIndicator indicator) {
                Ca65BuildRunner.runBuild(project, config, indicator);
            }
        }.queue();
    }
}
