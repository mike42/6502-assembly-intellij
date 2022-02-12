package org.ca65.action;

import com.intellij.codeInsight.daemon.DaemonCodeAnalyzer;
import com.intellij.codeInsight.intention.IntentionAction;
import com.intellij.codeInspection.util.IntentionFamilyName;
import com.intellij.codeInspection.util.IntentionName;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiFile;
import com.intellij.util.IncorrectOperationException;
import org.ca65.Asm6502Bundle;
import org.ca65.config.AsmConfiguration;
import org.ca65.helpers.Cpu;
import org.jetbrains.annotations.NotNull;

public class DisableProjectReferenceCheckingIntentionAction implements IntentionAction {

    public DisableProjectReferenceCheckingIntentionAction() {
    }

    @Override
    public @IntentionName @NotNull String getText() {
        return Asm6502Bundle.message("INTN.NAME.disable.reference.checking");
    }

    @Override
    public @NotNull @IntentionFamilyName String getFamilyName() {
        return Asm6502Bundle.message("INTN.NAME.disable.reference.checking");
    }

    @Override
    public boolean isAvailable(@NotNull Project project, Editor editor, PsiFile file) {
        return true;
    }

    @Override
    public void invoke(@NotNull Project project, Editor editor, PsiFile file) throws IncorrectOperationException {
        AsmConfiguration.getInstance(project).setReferenceCheckingEnabled(false);
        DaemonCodeAnalyzer.getInstance(project).restart();
    }

    @Override
    public boolean startInWriteAction() {
        return false;
    }
}
