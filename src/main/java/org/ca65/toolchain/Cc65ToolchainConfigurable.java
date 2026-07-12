package org.ca65.toolchain;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.fileChooser.FileChooserDescriptorFactory;
import com.intellij.openapi.options.Configurable;
import com.intellij.openapi.ui.TextFieldWithBrowseButton;
import com.intellij.ui.DocumentAdapter;
import com.intellij.ui.JBColor;
import com.intellij.ui.components.JBLabel;
import com.intellij.util.Alarm;
import com.intellij.util.ui.FormBuilder;
import com.intellij.util.ui.UIUtil;
import org.ca65.Asm6502Bundle;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.event.DocumentEvent;

/**
 * "Build, Execution, Deployment | 6502 Toolchains": location of the cc65
 * binaries with inline version detection.
 */
public class Cc65ToolchainConfigurable implements Configurable {
    private TextFieldWithBrowseButton myBinDirField;
    private JBLabel myVersionLabel;
    private Alarm myAlarm;
    private JPanel myPanel;

    // Guards against stale async probe results overwriting newer ones.
    private int myProbeGeneration = 0;

    @Nls(capitalization = Nls.Capitalization.Title)
    @Override
    public String getDisplayName() {
        return Asm6502Bundle.message("SETTINGS.toolchains.title");
    }

    @Override
    public @Nullable JComponent createComponent() {
        myBinDirField = new TextFieldWithBrowseButton();
        myBinDirField.addBrowseFolderListener(
                Asm6502Bundle.message("SETTINGS.toolchains.bindir.chooser.title"), null, null,
                FileChooserDescriptorFactory.createSingleFolderDescriptor());
        myVersionLabel = new JBLabel();
        myVersionLabel.setForeground(UIUtil.getContextHelpForeground());
        myAlarm = new Alarm(Alarm.ThreadToUse.SWING_THREAD, null);
        myBinDirField.getTextField().getDocument().addDocumentListener(new DocumentAdapter() {
            @Override
            protected void textChanged(@NotNull DocumentEvent e) {
                scheduleVersionProbe();
            }
        });

        JBLabel hint = new JBLabel(Asm6502Bundle.message("SETTINGS.toolchains.bindir.hint"));
        hint.setForeground(UIUtil.getContextHelpForeground());
        hint.setFont(UIUtil.getFont(UIUtil.FontSize.SMALL, hint.getFont()));

        myPanel = FormBuilder.createFormBuilder()
                .addLabeledComponent(Asm6502Bundle.message("SETTINGS.toolchains.bindir.label"), myBinDirField)
                .addComponentToRightColumn(hint)
                .addLabeledComponent(Asm6502Bundle.message("SETTINGS.toolchains.version.label"), myVersionLabel)
                .addComponentFillVertically(new JPanel(), 0)
                .getPanel();
        return myPanel;
    }

    private void scheduleVersionProbe() {
        myAlarm.cancelAllRequests();
        myVersionLabel.setText(Asm6502Bundle.message("SETTINGS.toolchains.version.checking"));
        myVersionLabel.setForeground(UIUtil.getContextHelpForeground());
        myAlarm.addRequest(this::probeVersion, 400);
    }

    private void probeVersion() {
        final String binDir = myBinDirField.getText().trim();
        final int generation = ++myProbeGeneration;
        ApplicationManager.getApplication().executeOnPooledThread(() -> {
            String ca65 = Cc65ToolchainService.detectVersion(binDir, "ca65");
            String ld65 = Cc65ToolchainService.detectVersion(binDir, "ld65");
            ApplicationManager.getApplication().invokeLater(() -> {
                if (generation != myProbeGeneration || myPanel == null) {
                    return;
                }
                if (ca65 == null || ld65 == null) {
                    myVersionLabel.setText(Asm6502Bundle.message("SETTINGS.toolchains.version.notfound",
                            ca65 == null ? "ca65" : "ld65"));
                    myVersionLabel.setForeground(JBColor.RED);
                } else {
                    myVersionLabel.setText(ca65);
                    myVersionLabel.setForeground(UIUtil.getContextHelpForeground());
                }
            }, ModalityState.any());
        });
    }

    @Override
    public boolean isModified() {
        return !myBinDirField.getText().trim().equals(Cc65ToolchainService.getInstance().getCc65BinDir());
    }

    @Override
    public void apply() {
        Cc65ToolchainService.getInstance().setCc65BinDir(myBinDirField.getText());
    }

    @Override
    public void reset() {
        myBinDirField.setText(Cc65ToolchainService.getInstance().getCc65BinDir());
        scheduleVersionProbe();
    }

    @Override
    public void disposeUIResources() {
        if (myAlarm != null) {
            myAlarm.cancelAllRequests();
        }
        myPanel = null;
        myBinDirField = null;
        myVersionLabel = null;
    }
}
