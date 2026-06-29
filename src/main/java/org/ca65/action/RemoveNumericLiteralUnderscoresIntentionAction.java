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
import org.ca65.psi.AsmNumericLiteral;
import org.jetbrains.annotations.NotNull;

public class RemoveNumericLiteralUnderscoresIntentionAction implements IntentionAction {
    private final AsmNumericLiteral element;
    private final String replacement;

    public RemoveNumericLiteralUnderscoresIntentionAction(@NotNull AsmNumericLiteral element,
                                                          @NotNull String replacement) {
        this.element = element;
        this.replacement = replacement;
    }

    @Override
    public @IntentionName @NotNull String getText() {
        return Asm6502Bundle.message("INTN.remove.underscores", replacement);
    }

    @Override
    public @NotNull @IntentionFamilyName String getFamilyName() {
        return Asm6502Bundle.message("INTN.NAME.remove.underscores");
    }

    @Override
    public boolean isAvailable(@NotNull Project project, Editor editor, PsiFile file) {
        return element.isValid();
    }

    @Override
    public void invoke(@NotNull Project project, Editor editor, PsiFile file) throws IncorrectOperationException {
        PsiElement newLiteral = AsmElementFactory.createNumericLiteral(project, replacement);
        element.replace(newLiteral);
    }

    @Override
    public boolean startInWriteAction() {
        return true;
    }
}
