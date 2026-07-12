package org.ca65.status;

import com.intellij.codeInsight.daemon.DaemonCodeAnalyzer;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.fileTypes.FileTypeRegistry;
import com.intellij.openapi.options.ShowSettingsUtil;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.popup.JBPopupFactory;
import com.intellij.openapi.ui.popup.ListPopup;
import com.intellij.openapi.ui.popup.PopupStep;
import com.intellij.openapi.ui.popup.util.BaseListPopupStep;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.wm.StatusBarWidget;
import com.intellij.openapi.wm.impl.status.EditorBasedStatusBarPopup;
import com.intellij.util.messages.MessageBusConnection;
import org.ca65.Asm6502Bundle;
import org.ca65.AsmFileType;
import org.ca65.buildcfg.Ca65ActiveConfiguration;
import org.ca65.buildcfg.Ca65BuildConfigurable;
import org.ca65.buildcfg.Ca65BuildConfiguration;
import org.ca65.buildcfg.Ca65BuildSettings;
import org.ca65.buildcfg.Ca65TargetSystem;
import org.ca65.config.AsmConfiguration;
import org.ca65.helpers.Cpu;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * Status bar widget showing the CPU the editor currently assumes for
 * assembly files. The popup switches the active build configuration when
 * configurations exist, or the manual project CPU when none do.
 */
public class AsmCpuStatusBarWidget extends EditorBasedStatusBarPopup {
    public static final String WIDGET_ID = "Asm6502Cpu";

    public AsmCpuStatusBarWidget(@NotNull Project project) {
        super(project, false);
    }

    @Override
    protected @NotNull WidgetState getWidgetState(@Nullable VirtualFile file) {
        if (file == null || !FileTypeRegistry.getInstance().isFileOfType(file, AsmFileType.INSTANCE)) {
            return WidgetState.HIDDEN;
        }
        Project project = getProject();
        Ca65BuildConfiguration active = Ca65ActiveConfiguration.getInstance(project).getActiveConfiguration();
        String cpuText;
        String tooltip;
        if (active != null) {
            cpuText = active.getEffectiveCpuName();
            tooltip = Asm6502Bundle.message("WIDGET.cpu.tooltip.config", cpuText, active.name);
        } else {
            cpuText = AsmConfiguration.getInstance(project).getCpu().name;
            tooltip = Asm6502Bundle.message("WIDGET.cpu.tooltip.manual", cpuText);
        }
        return new WidgetState(tooltip, cpuText, true);
    }

    @Override
    protected @Nullable ListPopup createPopup(@NotNull DataContext context) {
        Project project = getProject();
        List<Ca65BuildConfiguration> configs = Ca65BuildSettings.getInstance(project).getConfigurations();
        if (!configs.isEmpty()) {
            return createConfigPopup(project, configs);
        }
        return createManualCpuPopup(project);
    }

    /** One popup entry: a label plus what selecting it does. */
    private record Item(@NotNull String label, @NotNull Runnable action) {
        @Override
        public String toString() {
            return label;
        }
    }

    private ListPopup createConfigPopup(Project project, List<Ca65BuildConfiguration> configs) {
        List<Item> items = new ArrayList<>();
        for (Ca65BuildConfiguration config : configs) {
            items.add(new Item(config.name + " (" + config.getEffectiveCpuName() + ")",
                    () -> Ca65ActiveConfiguration.getInstance(project).setActiveConfigurationName(config.name)));
        }
        items.add(new Item(Asm6502Bundle.message("WIDGET.cpu.edit.configurations"),
                () -> ShowSettingsUtil.getInstance().showSettingsDialog(project, Ca65BuildConfigurable.class)));
        return createPopup(project, Asm6502Bundle.message("WIDGET.cpu.popup.title.configs"), items);
    }

    private ListPopup createManualCpuPopup(Project project) {
        List<Item> items = new ArrayList<>();
        for (Cpu cpu : Cpu.values()) {
            items.add(new Item(cpu.name, () -> {
                AsmConfiguration.getInstance(project).setCpu(cpu);
                DaemonCodeAnalyzer.getInstance(project).restart();
            }));
        }
        items.add(new Item(Asm6502Bundle.message("WIDGET.cpu.edit.configurations"),
                () -> ShowSettingsUtil.getInstance().showSettingsDialog(project, Ca65BuildConfigurable.class)));
        return createPopup(project, Asm6502Bundle.message("WIDGET.cpu.popup.title.manual"), items);
    }

    private ListPopup createPopup(Project project, String title, List<Item> items) {
        BaseListPopupStep<Item> step = new BaseListPopupStep<>(title, items) {
            @Override
            public @Nullable PopupStep<?> onChosen(Item selectedValue, boolean finalChoice) {
                return doFinalStep(() -> {
                    selectedValue.action().run();
                    update();
                });
            }
        };
        return JBPopupFactory.getInstance().createListPopup(step);
    }

    @Override
    protected void registerCustomListeners(@NotNull MessageBusConnection connection) {
        connection.subscribe(Ca65ActiveConfiguration.Listener.TOPIC,
                (Ca65ActiveConfiguration.Listener) () -> update());
    }

    @Override
    protected @NotNull StatusBarWidget createInstance(@NotNull Project project) {
        return new AsmCpuStatusBarWidget(project);
    }

    @Override
    public @NotNull String ID() {
        return WIDGET_ID;
    }
}
