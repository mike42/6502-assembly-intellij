package org.ca65.run;

import com.intellij.openapi.fileChooser.FileChooserDescriptorFactory;
import com.intellij.openapi.options.SettingsEditor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.ComboBox;
import com.intellij.openapi.ui.TextFieldWithBrowseButton;
import com.intellij.ui.SimpleListCellRenderer;
import com.intellij.ui.components.JBTextField;
import com.intellij.util.ui.FormBuilder;
import org.ca65.Asm6502Bundle;
import org.ca65.buildcfg.Ca65BuildConfiguration;
import org.ca65.buildcfg.Ca65BuildSettings;
import org.ca65.buildcfg.Ca65TargetSystem;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;

/**
 * Editor for {@link Ca65RunConfiguration}: build configuration, then an
 * emulator filtered to the ones supporting that configuration's target.
 */
public class Ca65RunConfigurationEditor extends SettingsEditor<Ca65RunConfiguration> {
    private final Project myProject;
    private final ComboBox<String> myBuildConfigCombo = new ComboBox<>();
    private final ComboBox<EmulatorProvider> myEmulatorCombo = new ComboBox<>();
    private final TextFieldWithBrowseButton myExecutableField = new TextFieldWithBrowseButton();
    private final JBTextField myExtraArgsField = new JBTextField();

    public Ca65RunConfigurationEditor(@NotNull Project project) {
        myProject = project;
        myBuildConfigCombo.addActionListener(e -> updateEmulators());
        myEmulatorCombo.setRenderer(SimpleListCellRenderer.create("", EmulatorProvider::getDisplayName));
        myEmulatorCombo.addActionListener(e -> updateExecutableHint());
        myExecutableField.addBrowseFolderListener(
                Asm6502Bundle.message("RUN.editor.executable.chooser.title"), null, project,
                FileChooserDescriptorFactory.createSingleFileNoJarsDescriptor());
    }

    private void updateEmulators() {
        EmulatorProvider previous = myEmulatorCombo.getItem();
        myEmulatorCombo.removeAllItems();
        for (EmulatorProvider provider : EmulatorProvider.forSystem(getSelectedTargetSystem())) {
            myEmulatorCombo.addItem(provider);
        }
        if (previous != null) {
            myEmulatorCombo.setSelectedItem(previous);
        }
        if (myEmulatorCombo.getSelectedIndex() < 0 && myEmulatorCombo.getItemCount() > 0) {
            myEmulatorCombo.setSelectedIndex(0);
        }
        updateExecutableHint();
    }

    private Ca65TargetSystem getSelectedTargetSystem() {
        String name = myBuildConfigCombo.getItem();
        Ca65BuildConfiguration buildConfig = Ca65BuildSettings.getInstance(myProject).findByName(name);
        return buildConfig == null ? null : buildConfig.getTargetSystemEnum();
    }

    private void updateExecutableHint() {
        EmulatorProvider provider = myEmulatorCombo.getItem();
        Ca65TargetSystem system = getSelectedTargetSystem();
        String hint = provider != null && system != null
                ? Asm6502Bundle.message("RUN.editor.executable.hint", provider.getDefaultExecutable(system))
                : "";
        ((JBTextField) myExecutableField.getTextField()).getEmptyText().setText(hint);
    }

    @Override
    protected void resetEditorFrom(@NotNull Ca65RunConfiguration configuration) {
        myBuildConfigCombo.removeAllItems();
        for (Ca65BuildConfiguration buildConfig : Ca65BuildSettings.getInstance(myProject).getConfigurations()) {
            myBuildConfigCombo.addItem(buildConfig.name);
        }
        myBuildConfigCombo.setSelectedItem(configuration.getBuildConfigurationName());
        updateEmulators();
        EmulatorProvider provider = EmulatorProvider.findById(configuration.getEmulatorId());
        if (provider != null) {
            myEmulatorCombo.setSelectedItem(provider);
        }
        myExecutableField.setText(configuration.getEmulatorExecutable());
        myExtraArgsField.setText(configuration.getEmulatorExtraArgs());
    }

    @Override
    protected void applyEditorTo(@NotNull Ca65RunConfiguration configuration) {
        String buildConfig = myBuildConfigCombo.getItem();
        configuration.setBuildConfigurationName(buildConfig == null ? "" : buildConfig);
        EmulatorProvider provider = myEmulatorCombo.getItem();
        configuration.setEmulatorId(provider == null ? "" : provider.getId());
        configuration.setEmulatorExecutable(myExecutableField.getText().trim());
        configuration.setEmulatorExtraArgs(myExtraArgsField.getText().trim());
    }

    @Override
    protected @NotNull JComponent createEditor() {
        return FormBuilder.createFormBuilder()
                .addLabeledComponent(Asm6502Bundle.message("RUN.editor.buildconfig.label"), myBuildConfigCombo)
                .addLabeledComponent(Asm6502Bundle.message("RUN.editor.emulator.label"), myEmulatorCombo)
                .addLabeledComponent(Asm6502Bundle.message("RUN.editor.executable.label"), myExecutableField)
                .addLabeledComponent(Asm6502Bundle.message("RUN.editor.extraargs.label"), myExtraArgsField)
                .getPanel();
    }
}
