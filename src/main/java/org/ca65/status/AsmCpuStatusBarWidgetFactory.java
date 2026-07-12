package org.ca65.status;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.wm.StatusBarWidget;
import com.intellij.openapi.wm.StatusBarWidgetFactory;
import org.ca65.Asm6502Bundle;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;

public class AsmCpuStatusBarWidgetFactory implements StatusBarWidgetFactory {
    @Override
    public @NotNull String getId() {
        return AsmCpuStatusBarWidget.WIDGET_ID;
    }

    @Override
    public @Nls @NotNull String getDisplayName() {
        return Asm6502Bundle.message("WIDGET.cpu.display.name");
    }

    @Override
    public boolean isAvailable(@NotNull Project project) {
        return true;
    }

    @Override
    public @NotNull StatusBarWidget createWidget(@NotNull Project project) {
        return new AsmCpuStatusBarWidget(project);
    }
}
