package org.ca65.run;

import com.intellij.openapi.fileChooser.FileChooser;
import com.intellij.openapi.fileChooser.FileChooserDescriptor;
import com.intellij.openapi.fileChooser.FileChooserDescriptorFactory;
import com.intellij.openapi.options.SettingsEditor;
import com.intellij.openapi.project.ProjectUtil;
import com.intellij.openapi.ui.ComboBox;
import com.intellij.openapi.ui.TextFieldWithBrowseButton;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VfsUtilCore;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.ui.CollectionListModel;
import com.intellij.ui.ColoredListCellRenderer;
import com.intellij.ui.SimpleTextAttributes;
import com.intellij.ui.TitledSeparator;
import com.intellij.ui.ToolbarDecorator;
import com.intellij.ui.components.JBList;
import com.intellij.ui.components.JBTextField;
import com.intellij.util.ui.FormBuilder;
import com.intellij.util.ui.JBUI;
import org.ca65.Asm6502Bundle;
import org.ca65.buildcfg.Ca65BuildConfiguration;
import org.ca65.buildcfg.Ca65TargetSystem;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.util.ArrayList;

/**
 * Editor for {@link Ca65RunConfiguration}: the build settings (sources,
 * target system, flags) followed by the optional emulator, filtered to the
 * providers supporting the chosen target.
 */
public class Ca65RunConfigurationEditor extends SettingsEditor<Ca65RunConfiguration> {
    /** Sentinel for "custom linker config" in the target combo. */
    private static final Object CUSTOM_TARGET = new Object() {
        @Override
        public String toString() {
            return Asm6502Bundle.message("SETTINGS.build.target.custom");
        }
    };
    /** Sentinel for "do not run" in the emulator combo. */
    private static final Object NO_EMULATOR = new Object() {
        @Override
        public String toString() {
            return Asm6502Bundle.message("RUN.editor.emulator.none");
        }
    };
    /** Sentinel for "default CPU of the target" in the CPU combo. */
    private static final String DEFAULT_CPU = "";
    private static final String[] CPU_NAMES = {
            "6502", "6502X", "6502DTV", "65SC02", "65C02", "W65C02", "65CE02",
            "65816", "4510", "45GS02", "huc6280", "sweet16", "m740"};

    private final Project myProject;

    private final CollectionListModel<String> mySourcesModel = new CollectionListModel<>();
    private final JBTextField myDefinesField = new JBTextField();
    private final ComboBox<Object> myTargetCombo = new ComboBox<>();
    private final TextFieldWithBrowseButton myCustomConfigField = new TextFieldWithBrowseButton();
    private final ComboBox<String> myCpuCombo = new ComboBox<>();
    private final JBTextField myOutputDirField = new JBTextField();
    private final JCheckBox myAsmDebugCheck = new JCheckBox(Asm6502Bundle.message("SETTINGS.build.debug.asm"));
    private final JCheckBox myDbgFileCheck = new JCheckBox(Asm6502Bundle.message("SETTINGS.build.debug.dbgfile"));
    private final JCheckBox myLabelFileCheck = new JCheckBox(Asm6502Bundle.message("SETTINGS.build.debug.labelfile"));
    private final JCheckBox myMapFileCheck = new JCheckBox(Asm6502Bundle.message("SETTINGS.build.debug.mapfile"));
    private final JBTextField myExtraAsmArgsField = new JBTextField();
    private final JBTextField myExtraLinkerArgsField = new JBTextField();

    private final ComboBox<Object> myEmulatorCombo = new ComboBox<>();
    private final TextFieldWithBrowseButton myExecutableField = new TextFieldWithBrowseButton();
    private final JBTextField myEmulatorArgsField = new JBTextField();

    public Ca65RunConfigurationEditor(@NotNull Project project) {
        myProject = project;

        for (Ca65TargetSystem system : Ca65TargetSystem.values()) {
            myTargetCombo.addItem(system);
        }
        myTargetCombo.addItem(CUSTOM_TARGET);
        myTargetCombo.addActionListener(e -> updateTargetDependentFields());

        myCustomConfigField.addBrowseFolderListener(
                Asm6502Bundle.message("SETTINGS.build.customcfg.chooser.title"), null, project,
                FileChooserDescriptorFactory.createSingleFileDescriptor("cfg"));

        myCpuCombo.addItem(DEFAULT_CPU);
        for (String cpu : CPU_NAMES) {
            myCpuCombo.addItem(cpu);
        }
        myCpuCombo.setRenderer(new ColoredListCellRenderer<>() {
            @Override
            protected void customizeCellRenderer(@NotNull JList<? extends String> list, String value,
                                                 int index, boolean selected, boolean hasFocus) {
                if (value == null || value.isEmpty()) {
                    append(Asm6502Bundle.message("SETTINGS.build.cpu.default"),
                            SimpleTextAttributes.GRAYED_ATTRIBUTES);
                } else {
                    append(value);
                }
            }
        });

        myDefinesField.getEmptyText().setText(Asm6502Bundle.message("SETTINGS.build.defines.hint"));
        myOutputDirField.getEmptyText().setText(Asm6502Bundle.message("SETTINGS.build.outputdir.hint"));

        myEmulatorCombo.setRenderer(com.intellij.ui.SimpleListCellRenderer.create("", item ->
                item instanceof EmulatorProvider provider ? provider.getDisplayName() : String.valueOf(item)));
        myEmulatorCombo.addActionListener(e -> updateEmulatorDependentFields());
        myExecutableField.addBrowseFolderListener(
                Asm6502Bundle.message("RUN.editor.executable.chooser.title"), null, project,
                FileChooserDescriptorFactory.createSingleFileNoJarsDescriptor());
    }

    private void updateTargetDependentFields() {
        Object target = myTargetCombo.getSelectedItem();
        boolean custom = target == CUSTOM_TARGET;
        myCustomConfigField.setEnabled(custom);
        if (myCustomConfigField.getTextField() instanceof JBTextField field) {
            if (custom) {
                field.getEmptyText().setText(Asm6502Bundle.message("SETTINGS.build.customcfg.hint.custom"));
            } else if (target instanceof Ca65TargetSystem system) {
                String asmConfig = system.getAsmConfigName();
                field.getEmptyText().setText(asmConfig != null
                        ? Asm6502Bundle.message("SETTINGS.build.customcfg.hint.auto", asmConfig)
                        : Asm6502Bundle.message("SETTINGS.build.customcfg.hint.auto.t", system.id));
            }
        }
        updateEmulators();
    }

    /** Refills the emulator combo with the providers for the current target. */
    private void updateEmulators() {
        Object previous = myEmulatorCombo.getSelectedItem();
        myEmulatorCombo.removeAllItems();
        myEmulatorCombo.addItem(NO_EMULATOR);
        for (EmulatorProvider provider : EmulatorProvider.forSystem(getSelectedTargetSystem())) {
            myEmulatorCombo.addItem(provider);
        }
        if (previous != null) {
            myEmulatorCombo.setSelectedItem(previous);
        }
        if (myEmulatorCombo.getSelectedIndex() < 0) {
            myEmulatorCombo.setSelectedIndex(0);
        }
        updateEmulatorDependentFields();
    }

    private Ca65TargetSystem getSelectedTargetSystem() {
        return myTargetCombo.getSelectedItem() instanceof Ca65TargetSystem system ? system : null;
    }

    private EmulatorProvider getSelectedEmulator() {
        return myEmulatorCombo.getSelectedItem() instanceof EmulatorProvider provider ? provider : null;
    }

    private void updateEmulatorDependentFields() {
        EmulatorProvider provider = getSelectedEmulator();
        Ca65TargetSystem system = getSelectedTargetSystem();
        myExecutableField.setEnabled(provider != null);
        myEmulatorArgsField.setEnabled(provider != null);
        String hint = provider != null && system != null
                ? Asm6502Bundle.message("RUN.editor.executable.hint", provider.getDefaultExecutable(system))
                : "";
        ((JBTextField) myExecutableField.getTextField()).getEmptyText().setText(hint);
    }

    private void addSources(JBList<String> list) {
        FileChooserDescriptor descriptor = FileChooserDescriptorFactory.createMultipleFilesNoJarsDescriptor()
                .withTitle(Asm6502Bundle.message("SETTINGS.build.sources.chooser.title"));
        VirtualFile baseDir = ProjectUtil.guessProjectDir(myProject);
        VirtualFile[] chosen = FileChooser.chooseFiles(descriptor, myProject, baseDir);
        for (VirtualFile file : chosen) {
            String path = baseDir != null ? VfsUtilCore.getRelativePath(file, baseDir) : null;
            if (path == null) {
                path = file.getPath();
            }
            if (!mySourcesModel.getItems().contains(path)) {
                mySourcesModel.add(path);
            }
        }
    }

    @Override
    protected void resetEditorFrom(@NotNull Ca65RunConfiguration configuration) {
        Ca65BuildConfiguration spec = configuration.getBuildSpec();
        mySourcesModel.replaceAll(spec.sources);
        myDefinesField.setText(String.join(" ", spec.defines));
        Ca65TargetSystem system = spec.getTargetSystemEnum();
        myTargetCombo.setSelectedItem(system != null ? system : CUSTOM_TARGET);
        myCustomConfigField.setText(spec.customConfigFile == null ? "" : spec.customConfigFile);
        myCpuCombo.setSelectedItem(spec.cpu == null ? DEFAULT_CPU : spec.cpu);
        myOutputDirField.setText(spec.outputDir == null ? "" : spec.outputDir);
        myAsmDebugCheck.setSelected(spec.assemblerDebugInfo);
        myDbgFileCheck.setSelected(spec.generateDbgFile);
        myLabelFileCheck.setSelected(spec.generateLabelFile);
        myMapFileCheck.setSelected(spec.generateMapFile);
        myExtraAsmArgsField.setText(spec.extraAssemblerArgs == null ? "" : spec.extraAssemblerArgs);
        myExtraLinkerArgsField.setText(spec.extraLinkerArgs == null ? "" : spec.extraLinkerArgs);
        updateTargetDependentFields();

        if (configuration.isBuildOnly()) {
            myEmulatorCombo.setSelectedItem(NO_EMULATOR);
        } else {
            EmulatorProvider provider = EmulatorProvider.findById(configuration.getEmulatorId());
            myEmulatorCombo.setSelectedItem(provider != null ? provider : NO_EMULATOR);
        }
        myExecutableField.setText(configuration.getEmulatorExecutable());
        myEmulatorArgsField.setText(configuration.getEmulatorExtraArgs());
        updateEmulatorDependentFields();
    }

    @Override
    protected void applyEditorTo(@NotNull Ca65RunConfiguration configuration) {
        Ca65BuildConfiguration spec = configuration.getBuildSpec();
        spec.sources = new ArrayList<>(mySourcesModel.getItems());
        spec.defines = parseDefines(myDefinesField.getText());
        Object target = myTargetCombo.getSelectedItem();
        spec.targetSystem = target instanceof Ca65TargetSystem system ? system.id : "";
        spec.customConfigFile = myCustomConfigField.getText().trim();
        Object cpu = myCpuCombo.getSelectedItem();
        spec.cpu = cpu == null ? "" : cpu.toString();
        spec.outputDir = myOutputDirField.getText().trim();
        spec.assemblerDebugInfo = myAsmDebugCheck.isSelected();
        spec.generateDbgFile = myDbgFileCheck.isSelected();
        spec.generateLabelFile = myLabelFileCheck.isSelected();
        spec.generateMapFile = myMapFileCheck.isSelected();
        spec.extraAssemblerArgs = myExtraAsmArgsField.getText().trim();
        spec.extraLinkerArgs = myExtraLinkerArgsField.getText().trim();

        EmulatorProvider provider = getSelectedEmulator();
        configuration.setEmulatorId(provider == null ? Ca65RunConfiguration.EMULATOR_NONE : provider.getId());
        configuration.setEmulatorExecutable(myExecutableField.getText().trim());
        configuration.setEmulatorExtraArgs(myEmulatorArgsField.getText().trim());
    }

    private static java.util.List<String> parseDefines(String text) {
        java.util.List<String> result = new ArrayList<>();
        for (String token : text.trim().split("\\s+")) {
            if (!token.isEmpty()) {
                result.add(token);
            }
        }
        return result;
    }

    @Override
    protected @NotNull JComponent createEditor() {
        JBList<String> sourcesList = new JBList<>(mySourcesModel);
        JPanel sourcesPanel = ToolbarDecorator.createDecorator(sourcesList)
                .setAddAction(button -> addSources(sourcesList))
                .setRemoveAction(button -> {
                    for (String value : sourcesList.getSelectedValuesList()) {
                        mySourcesModel.remove(value);
                    }
                })
                .disableUpDownActions()
                .createPanel();
        sourcesPanel.setPreferredSize(JBUI.size(-1, 100));

        return FormBuilder.createFormBuilder()
                .addComponent(new TitledSeparator(Asm6502Bundle.message("RUN.editor.section.build")))
                .addLabeledComponent(Asm6502Bundle.message("SETTINGS.build.sources.label"), sourcesPanel)
                .addLabeledComponent(Asm6502Bundle.message("SETTINGS.build.target.label"), myTargetCombo)
                .addLabeledComponent(Asm6502Bundle.message("SETTINGS.build.customcfg.label"), myCustomConfigField)
                .addLabeledComponent(Asm6502Bundle.message("SETTINGS.build.cpu.label"), myCpuCombo)
                .addLabeledComponent(Asm6502Bundle.message("SETTINGS.build.defines.label"), myDefinesField)
                .addLabeledComponent(Asm6502Bundle.message("SETTINGS.build.outputdir.label"), myOutputDirField)
                .addComponent(myAsmDebugCheck)
                .addComponent(myDbgFileCheck)
                .addComponent(myLabelFileCheck)
                .addComponent(myMapFileCheck)
                .addLabeledComponent(Asm6502Bundle.message("SETTINGS.build.extra.asm.label"), myExtraAsmArgsField)
                .addLabeledComponent(Asm6502Bundle.message("SETTINGS.build.extra.link.label"), myExtraLinkerArgsField)
                .addComponent(new TitledSeparator(Asm6502Bundle.message("RUN.editor.section.run")))
                .addLabeledComponent(Asm6502Bundle.message("RUN.editor.emulator.label"), myEmulatorCombo)
                .addLabeledComponent(Asm6502Bundle.message("RUN.editor.executable.label"), myExecutableField)
                .addLabeledComponent(Asm6502Bundle.message("RUN.editor.extraargs.label"), myEmulatorArgsField)
                .getPanel();
    }
}
