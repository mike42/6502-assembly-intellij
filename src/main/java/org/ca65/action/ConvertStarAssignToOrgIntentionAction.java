package org.ca65.action;

import com.intellij.codeInsight.intention.IntentionAction;
import com.intellij.codeInspection.util.IntentionFamilyName;
import com.intellij.codeInspection.util.IntentionName;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiFile;
import com.intellij.util.IncorrectOperationException;
import org.ca65.Asm6502Bundle;
import org.ca65.psi.AsmStarAssign;
import org.jetbrains.annotations.NotNull;

public class ConvertStarAssignToOrgIntentionAction implements IntentionAction {
    private final AsmStarAssign element;

    public ConvertStarAssignToOrgIntentionAction(@NotNull AsmStarAssign element) {
        this.element = element;
    }

    @Override
    public @IntentionName @NotNull String getText() {
        return Asm6502Bundle.message("INTN.convert.star.assign.to.org", element.getExpr().getText());
    }

    @Override
    public @NotNull @IntentionFamilyName String getFamilyName() {
        return Asm6502Bundle.message("INTN.NAME.convert.star.assign.to.org");
    }

    @Override
    public boolean isAvailable(@NotNull Project project, Editor editor, PsiFile file) {
        return element.isValid();
    }

    @Override
    public void invoke(@NotNull Project project, Editor editor, PsiFile file) throws IncorrectOperationException {
        TextRange range = element.getTextRange();
        editor.getDocument().replaceString(range.getStartOffset(), range.getEndOffset(),
                ".org " + element.getExpr().getText());
    }

    @Override
    public boolean startInWriteAction() {
        return true;
    }
}
