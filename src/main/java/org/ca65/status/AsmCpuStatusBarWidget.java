package org.ca65.status;

import com.intellij.codeInsight.daemon.DaemonCodeAnalyzer;
import com.intellij.execution.RunManager;
import com.intellij.execution.RunManagerListener;
import com.intellij.execution.RunnerAndConfigurationSettings;
import com.intellij.execution.impl.EditConfigurationsDialog;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.fileTypes.FileTypeRegistry;
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
import org.ca65.config.AsmConfiguration;
import org.ca65.helpers.Cpu;
import org.ca65.run.Ca65RunConfiguration;
import org.ca65.run.Ca65RunConfigurationType;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * Status bar widget showing the CPU the editor currently assumes for
 * assembly files. The popup switches the selected run configuration when
 * 6502 configurations exist, or the manual project CPU when none do.
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
        Ca65RunConfiguration selected = selectedCa65Configuration(project);
        String cpuText;
        String tooltip;
        if (selected != null) {
            cpuText = selected.getBuildSpec().getEffectiveCpuName();
            tooltip = Asm6502Bundle.message("WIDGET.cpu.tooltip.config", cpuText, selected.getName());
        } else {
            cpuText = AsmConfiguration.getInstance(project).getCpu().name;
            tooltip = Asm6502Bundle.message("WIDGET.cpu.tooltip.manual", cpuText);
        }
        return new WidgetState(tooltip, cpuText, true);
    }

    @Nullable
    private static Ca65RunConfiguration selectedCa65Configuration(@NotNull Project project) {
        RunnerAndConfigurationSettings selected = RunManager.getInstance(project).getSelectedConfiguration();
        return selected != null && selected.getConfiguration() instanceof Ca65RunConfiguration configuration
                ? configuration : null;
    }

    @Override
    protected @Nullable ListPopup createPopup(@NotNull DataContext context) {
        Project project = getProject();
        RunManager runManager = RunManager.getInstance(project);
        List<RunnerAndConfigurationSettings> configurations =
                runManager.getConfigurationSettingsList(Ca65RunConfigurationType.getInstance());
        if (!configurations.isEmpty()) {
            return createConfigPopup(project, runManager, configurations);
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

    private ListPopup createConfigPopup(Project project, RunManager runManager,
                                        List<RunnerAndConfigurationSettings> configurations) {
        List<Item> items = new ArrayList<>();
        for (RunnerAndConfigurationSettings settings : configurations) {
            if (!(settings.getConfiguration() instanceof Ca65RunConfiguration configuration)) {
                continue;
            }
            String cpu = configuration.getBuildSpec().getEffectiveCpuName();
            items.add(new Item(settings.getName() + " (" + cpu + ")",
                    () -> runManager.setSelectedConfiguration(settings)));
        }
        items.add(new Item(Asm6502Bundle.message("WIDGET.cpu.edit.configurations"),
                () -> new EditConfigurationsDialog(project).show()));
        return createPopup(Asm6502Bundle.message("WIDGET.cpu.popup.title.configs"), items);
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
                () -> new EditConfigurationsDialog(project).show()));
        return createPopup(Asm6502Bundle.message("WIDGET.cpu.popup.title.manual"), items);
    }

    private ListPopup createPopup(String title, List<Item> items) {
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
        connection.subscribe(RunManagerListener.TOPIC, new RunManagerListener() {
            @Override
            public void runConfigurationSelected(@Nullable RunnerAndConfigurationSettings settings) {
                update();
            }

            @Override
            public void runConfigurationChanged(@NotNull RunnerAndConfigurationSettings settings) {
                update();
            }

            @Override
            public void runConfigurationAdded(@NotNull RunnerAndConfigurationSettings settings) {
                update();
            }

            @Override
            public void runConfigurationRemoved(@NotNull RunnerAndConfigurationSettings settings) {
                update();
            }
        });
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
