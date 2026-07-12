package org.ca65.buildcfg;

import com.intellij.icons.AllIcons;
import com.intellij.openapi.fileChooser.FileChooser;
import com.intellij.openapi.fileChooser.FileChooserDescriptor;
import com.intellij.openapi.fileChooser.FileChooserDescriptorFactory;
import com.intellij.openapi.options.Configurable;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.TextFieldWithBrowseButton;
import com.intellij.openapi.vfs.VfsUtilCore;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.ui.CollectionListModel;
import com.intellij.ui.ColoredListCellRenderer;
import com.intellij.ui.JBSplitter;
import com.intellij.ui.SimpleTextAttributes;
import com.intellij.ui.ToolbarDecorator;
import com.intellij.ui.components.JBLabel;
import com.intellij.ui.components.JBList;
import com.intellij.ui.components.JBTextField;
import com.intellij.util.ui.FormBuilder;
import com.intellij.util.ui.JBUI;
import com.intellij.util.ui.UIUtil;
import org.ca65.Asm6502Bundle;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * "Build, Execution, Deployment | 6502 Build Configurations": master list of
 * configurations with a detail form. Works on a copy; persists on apply.
 */
public class Ca65BuildConfigurable implements Configurable {
    /** Sentinel for "custom linker config" in the target combo. */
    private static final Object CUSTOM_TARGET = new Object() {
        @Override
        public String toString() {
            return Asm6502Bundle.message("SETTINGS.build.target.custom");
        }
    };
    /** Sentinel for "default CPU of the target" in the CPU combo. */
    private static final String DEFAULT_CPU = "";
    private static final String[] CPU_NAMES = {
            "6502", "6502X", "6502DTV", "65SC02", "65C02", "W65C02", "65CE02",
            "65816", "4510", "45GS02", "huc6280", "sweet16", "m740"};

    private final Project myProject;

    private List<Ca65BuildConfiguration> myConfigs = new ArrayList<>();
    private CollectionListModel<Ca65BuildConfiguration> myListModel;
    private JBList<Ca65BuildConfiguration> myList;
    private Ca65BuildConfiguration mySelected;

    private JPanel myPanel;
    private JPanel myDetailPanel;
    private JBTextField myNameField;
    private CollectionListModel<String> mySourcesModel;
    private JBTextField myDefinesField;
    private JComboBox<Object> myTargetCombo;
    private TextFieldWithBrowseButton myCustomConfigField;
    private JComboBox<String> myCpuCombo;
    private JBTextField myOutputDirField;
    private JCheckBox myAsmDebugCheck;
    private JCheckBox myDbgFileCheck;
    private JCheckBox myLabelFileCheck;
    private JCheckBox myMapFileCheck;
    private JBTextField myExtraAsmArgsField;
    private JBTextField myExtraLinkerArgsField;

    private boolean myLoadingDetail = false;

    public Ca65BuildConfigurable(@NotNull Project project) {
        myProject = project;
    }

    @Nls(capitalization = Nls.Capitalization.Title)
    @Override
    public String getDisplayName() {
        return Asm6502Bundle.message("SETTINGS.build.title");
    }

    @Override
    public @Nullable JComponent createComponent() {
        myListModel = new CollectionListModel<>();
        myList = new JBList<>(myListModel);
        myList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        myList.setCellRenderer(new ColoredListCellRenderer<>() {
            @Override
            protected void customizeCellRenderer(@NotNull JList<? extends Ca65BuildConfiguration> list,
                                                 Ca65BuildConfiguration value, int index,
                                                 boolean selected, boolean hasFocus) {
                append(value.name.isBlank()
                        ? Asm6502Bundle.message("SETTINGS.build.unnamed") : value.name);
                Ca65TargetSystem system = value.getTargetSystemEnum();
                append("  " + (system != null ? system.displayName
                                : Asm6502Bundle.message("SETTINGS.build.target.custom.short")),
                        SimpleTextAttributes.GRAYED_ATTRIBUTES);
            }
        });
        myList.addListSelectionListener(e -> {
            if (!e.getValueIsAdjusting()) {
                onSelectionChanged();
            }
        });

        JPanel listPanel = ToolbarDecorator.createDecorator(myList)
                .setAddAction(button -> addConfiguration())
                .setRemoveAction(button -> removeSelected())
                .addExtraAction(com.intellij.openapi.project.DumbAwareAction.create(
                        Asm6502Bundle.message("SETTINGS.build.duplicate"), AllIcons.Actions.Copy,
                        e -> duplicateSelected()))
                .disableUpDownActions()
                .createPanel();

        myDetailPanel = createDetailPanel();
        setDetailEnabled(false);

        JBSplitter splitter = new JBSplitter(false, 0.3f);
        splitter.setFirstComponent(listPanel);
        splitter.setSecondComponent(myDetailPanel);
        splitter.setBorder(JBUI.Borders.empty());
        myPanel = new JPanel(new BorderLayout());
        myPanel.add(splitter, BorderLayout.CENTER);
        return myPanel;
    }

    private JPanel createDetailPanel() {
        myNameField = new JBTextField();

        mySourcesModel = new CollectionListModel<>();
        JBList<String> sourcesList = new JBList<>(mySourcesModel);
        JPanel sourcesPanel = ToolbarDecorator.createDecorator(sourcesList)
                .setAddAction(button -> addSources())
                .setRemoveAction(button -> {
                    for (String value : sourcesList.getSelectedValuesList()) {
                        mySourcesModel.remove(value);
                    }
                    storeDetail();
                })
                .createPanel();
        sourcesPanel.setPreferredSize(JBUI.size(-1, 120));

        myDefinesField = new JBTextField();
        myDefinesField.getEmptyText().setText(Asm6502Bundle.message("SETTINGS.build.defines.hint"));

        myTargetCombo = new JComboBox<>();
        for (Ca65TargetSystem system : Ca65TargetSystem.values()) {
            myTargetCombo.addItem(system);
        }
        myTargetCombo.addItem(CUSTOM_TARGET);
        myTargetCombo.addActionListener(e -> {
            updateTargetDependentFields();
            storeDetail();
        });

        myCustomConfigField = new TextFieldWithBrowseButton();
        myCustomConfigField.addBrowseFolderListener(
                Asm6502Bundle.message("SETTINGS.build.customcfg.chooser.title"), null, myProject,
                FileChooserDescriptorFactory.createSingleFileDescriptor("cfg"));

        myCpuCombo = new JComboBox<>();
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

        myOutputDirField = new JBTextField();
        myOutputDirField.getEmptyText().setText(Asm6502Bundle.message("SETTINGS.build.outputdir.hint"));

        myAsmDebugCheck = new JCheckBox(Asm6502Bundle.message("SETTINGS.build.debug.asm"));
        myDbgFileCheck = new JCheckBox(Asm6502Bundle.message("SETTINGS.build.debug.dbgfile"));
        myLabelFileCheck = new JCheckBox(Asm6502Bundle.message("SETTINGS.build.debug.labelfile"));
        myMapFileCheck = new JCheckBox(Asm6502Bundle.message("SETTINGS.build.debug.mapfile"));

        myExtraAsmArgsField = new JBTextField();
        myExtraLinkerArgsField = new JBTextField();

        JPanel form = FormBuilder.createFormBuilder()
                .addLabeledComponent(Asm6502Bundle.message("SETTINGS.build.name.label"), myNameField)
                .addLabeledComponentFillVertically(Asm6502Bundle.message("SETTINGS.build.sources.label"), sourcesPanel)
                .addLabeledComponent(Asm6502Bundle.message("SETTINGS.build.defines.label"), myDefinesField)
                .addLabeledComponent(Asm6502Bundle.message("SETTINGS.build.target.label"), myTargetCombo)
                .addLabeledComponent(Asm6502Bundle.message("SETTINGS.build.customcfg.label"), myCustomConfigField)
                .addLabeledComponent(Asm6502Bundle.message("SETTINGS.build.cpu.label"), myCpuCombo)
                .addLabeledComponent(Asm6502Bundle.message("SETTINGS.build.outputdir.label"), myOutputDirField)
                .addComponent(myAsmDebugCheck)
                .addComponent(myDbgFileCheck)
                .addComponent(myLabelFileCheck)
                .addComponent(myMapFileCheck)
                .addLabeledComponent(Asm6502Bundle.message("SETTINGS.build.extra.asm.label"), myExtraAsmArgsField)
                .addLabeledComponent(Asm6502Bundle.message("SETTINGS.build.extra.link.label"), myExtraLinkerArgsField)
                .getPanel();
        form.setBorder(JBUI.Borders.emptyLeft(8));

        registerStoreOnChange(myNameField);
        registerStoreOnChange(myDefinesField);
        registerStoreOnChange(myCustomConfigField.getTextField());
        registerStoreOnChange(myOutputDirField);
        registerStoreOnChange(myExtraAsmArgsField);
        registerStoreOnChange(myExtraLinkerArgsField);
        myCpuCombo.addActionListener(e -> storeDetail());
        for (JCheckBox box : new JCheckBox[]{myAsmDebugCheck, myDbgFileCheck, myLabelFileCheck, myMapFileCheck}) {
            box.addActionListener(e -> storeDetail());
        }
        return form;
    }

    private void registerStoreOnChange(JTextField field) {
        field.getDocument().addDocumentListener(new com.intellij.ui.DocumentAdapter() {
            @Override
            protected void textChanged(javax.swing.event.@NotNull DocumentEvent e) {
                storeDetail();
            }
        });
    }

    private void addSources() {
        FileChooserDescriptor descriptor = FileChooserDescriptorFactory.createMultipleFilesNoJarsDescriptor()
                .withTitle(Asm6502Bundle.message("SETTINGS.build.sources.chooser.title"));
        VirtualFile baseDir = com.intellij.openapi.project.ProjectUtil.guessProjectDir(myProject);
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
        storeDetail();
    }

    private void addConfiguration() {
        Ca65BuildConfiguration config = new Ca65BuildConfiguration();
        config.name = uniqueName(Asm6502Bundle.message("SETTINGS.build.unnamed"));
        myConfigs.add(config);
        myListModel.add(config);
        myList.setSelectedValue(config, true);
    }

    private void duplicateSelected() {
        Ca65BuildConfiguration selected = myList.getSelectedValue();
        if (selected == null) {
            return;
        }
        Ca65BuildConfiguration copy = selected.copy();
        copy.name = uniqueName(selected.name);
        myConfigs.add(copy);
        myListModel.add(copy);
        myList.setSelectedValue(copy, true);
    }

    private void removeSelected() {
        Ca65BuildConfiguration selected = myList.getSelectedValue();
        if (selected == null) {
            return;
        }
        int index = myList.getSelectedIndex();
        mySelected = null;
        myConfigs.remove(selected);
        myListModel.remove(selected);
        if (!myConfigs.isEmpty()) {
            myList.setSelectedIndex(Math.min(index, myConfigs.size() - 1));
        } else {
            onSelectionChanged();
        }
    }

    private String uniqueName(String base) {
        String candidate = base;
        int counter = 2;
        while (nameExists(candidate)) {
            candidate = base + " " + counter++;
        }
        return candidate;
    }

    private boolean nameExists(String name) {
        for (Ca65BuildConfiguration config : myConfigs) {
            if (name.equals(config.name)) {
                return true;
            }
        }
        return false;
    }

    private void onSelectionChanged() {
        mySelected = myList.getSelectedValue();
        loadDetail();
    }

    private void loadDetail() {
        myLoadingDetail = true;
        try {
            Ca65BuildConfiguration config = mySelected;
            setDetailEnabled(config != null);
            if (config == null) {
                myNameField.setText("");
                mySourcesModel.replaceAll(List.of());
                myDefinesField.setText("");
                myCustomConfigField.setText("");
                myOutputDirField.setText("");
                myExtraAsmArgsField.setText("");
                myExtraLinkerArgsField.setText("");
                return;
            }
            myNameField.setText(config.name);
            mySourcesModel.replaceAll(config.sources);
            myDefinesField.setText(String.join(" ", config.defines));
            Ca65TargetSystem system = config.getTargetSystemEnum();
            myTargetCombo.setSelectedItem(system != null ? system : CUSTOM_TARGET);
            myCustomConfigField.setText(config.customConfigFile == null ? "" : config.customConfigFile);
            myCpuCombo.setSelectedItem(config.cpu == null ? DEFAULT_CPU : config.cpu);
            myOutputDirField.setText(config.outputDir == null ? "" : config.outputDir);
            myAsmDebugCheck.setSelected(config.assemblerDebugInfo);
            myDbgFileCheck.setSelected(config.generateDbgFile);
            myLabelFileCheck.setSelected(config.generateLabelFile);
            myMapFileCheck.setSelected(config.generateMapFile);
            myExtraAsmArgsField.setText(config.extraAssemblerArgs == null ? "" : config.extraAssemblerArgs);
            myExtraLinkerArgsField.setText(config.extraLinkerArgs == null ? "" : config.extraLinkerArgs);
            updateTargetDependentFields();
        } finally {
            myLoadingDetail = false;
        }
    }

    private void updateTargetDependentFields() {
        Object target = myTargetCombo.getSelectedItem();
        boolean custom = target == CUSTOM_TARGET;
        myCustomConfigField.setEnabled(custom && myTargetCombo.isEnabled());
        // Explain why the field is disabled for built-in targets: the
        // linker config is derived from the target system.
        if (myCustomConfigField.getTextField() instanceof com.intellij.ui.components.JBTextField field) {
            if (custom) {
                field.getEmptyText().setText(Asm6502Bundle.message("SETTINGS.build.customcfg.hint.custom"));
            } else if (target instanceof Ca65TargetSystem system) {
                String asmConfig = system.getAsmConfigName();
                field.getEmptyText().setText(asmConfig != null
                        ? Asm6502Bundle.message("SETTINGS.build.customcfg.hint.auto", asmConfig)
                        : Asm6502Bundle.message("SETTINGS.build.customcfg.hint.auto.t", system.id));
            }
        }
    }

    private void storeDetail() {
        if (myLoadingDetail || mySelected == null) {
            return;
        }
        Ca65BuildConfiguration config = mySelected;
        config.name = myNameField.getText().trim();
        config.sources = new ArrayList<>(mySourcesModel.getItems());
        config.defines = parseDefines(myDefinesField.getText());
        Object target = myTargetCombo.getSelectedItem();
        config.targetSystem = target instanceof Ca65TargetSystem system ? system.id : "";
        config.customConfigFile = myCustomConfigField.getText().trim();
        Object cpu = myCpuCombo.getSelectedItem();
        config.cpu = cpu == null ? "" : cpu.toString();
        config.outputDir = myOutputDirField.getText().trim();
        config.assemblerDebugInfo = myAsmDebugCheck.isSelected();
        config.generateDbgFile = myDbgFileCheck.isSelected();
        config.generateLabelFile = myLabelFileCheck.isSelected();
        config.generateMapFile = myMapFileCheck.isSelected();
        config.extraAssemblerArgs = myExtraAsmArgsField.getText().trim();
        config.extraLinkerArgs = myExtraLinkerArgsField.getText().trim();
        myList.repaint();
    }

    private static List<String> parseDefines(String text) {
        List<String> result = new ArrayList<>();
        for (String token : text.trim().split("\\s+")) {
            if (!token.isEmpty()) {
                result.add(token);
            }
        }
        return result;
    }

    private void setDetailEnabled(boolean enabled) {
        for (JComponent component : new JComponent[]{myNameField, myDefinesField, myTargetCombo,
                myCustomConfigField, myCpuCombo, myOutputDirField, myAsmDebugCheck, myDbgFileCheck,
                myLabelFileCheck, myMapFileCheck, myExtraAsmArgsField, myExtraLinkerArgsField}) {
            component.setEnabled(enabled);
        }
        if (enabled) {
            updateTargetDependentFields();
        }
    }

    @Override
    public boolean isModified() {
        return !Objects.equals(myConfigs, Ca65BuildSettings.getInstance(myProject).getConfigurations());
    }

    @Override
    public void apply() {
        List<Ca65BuildConfiguration> copies = new ArrayList<>();
        for (Ca65BuildConfiguration config : myConfigs) {
            copies.add(config.copy());
        }
        Ca65BuildSettings.getInstance(myProject).setConfigurations(copies);
        // CPU or defines may have changed for the active configuration.
        Ca65ActiveConfiguration.getInstance(myProject).notifyChanged();
    }

    @Override
    public void reset() {
        myConfigs = new ArrayList<>();
        for (Ca65BuildConfiguration config : Ca65BuildSettings.getInstance(myProject).getConfigurations()) {
            myConfigs.add(config.copy());
        }
        mySelected = null;
        myListModel.replaceAll(myConfigs);
        if (!myConfigs.isEmpty()) {
            myList.setSelectedIndex(0);
        } else {
            onSelectionChanged();
        }
    }

    @Override
    public void disposeUIResources() {
        myPanel = null;
    }
}
