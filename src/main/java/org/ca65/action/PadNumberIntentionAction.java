package org.ca65.action;

import com.intellij.codeInsight.intention.IntentionAction;
import com.intellij.codeInspection.util.IntentionFamilyName;
import com.intellij.codeInspection.util.IntentionName;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.util.IncorrectOperationException;
import org.ca65.Asm6502Bundle;
import org.ca65.psi.AsmElementFactory;
import org.jetbrains.annotations.NotNull;

public class PadNumberIntentionAction implements IntentionAction {
    private final PsiElement existingElement;
    private final String suggestedReplacement;

    public PadNumberIntentionAction(PsiElement existingElement, String suggestedReplacement) {
        this.existingElement = existingElement;
        this.suggestedReplacement = suggestedReplacement;
    }

    @Override
    public @IntentionName @NotNull String getText() {
        return Asm6502Bundle.message("INTN.pad.number", this.suggestedReplacement);
    }

    @Override
    public @NotNull @IntentionFamilyName String getFamilyName() {
        return Asm6502Bundle.message("INTN.NAME.pad.number");
    }

    @Override
    public boolean isAvailable(@NotNull Project project, Editor editor, PsiFile file) {
        return true;
    }

    @Override
    public void invoke(@NotNull Project project, Editor editor, PsiFile file) throws IncorrectOperationException {
        PsiElement newLiteral = AsmElementFactory.createNumericLiteral(project, suggestedReplacement);
        existingElement.replace(newLiteral);
    }

    @Override
    public boolean startInWriteAction() {
        return true;
    }
}
