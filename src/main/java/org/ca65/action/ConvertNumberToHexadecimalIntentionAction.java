package org.ca65.action;

import com.intellij.codeInsight.intention.impl.BaseIntentionAction;
import com.intellij.codeInspection.util.IntentionFamilyName;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.util.IncorrectOperationException;
import org.ca65.Asm6502Bundle;
import org.ca65.helpers.NumericLiteralValue;
import org.ca65.psi.AsmElementFactory;
import org.ca65.psi.AsmFile;
import org.ca65.psi.AsmNumericLiteral;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import static org.ca65.action.IntentionActionUtil.getAsmNumericLiteral;

public class ConvertNumberToHexadecimalIntentionAction extends BaseIntentionAction {
    @Override
    public @NotNull @IntentionFamilyName String getFamilyName() {
        return Asm6502Bundle.message("INTN.NAME.convert.to.hex");
    }

    @Override
    public boolean isAvailable(@NotNull Project project, Editor editor, PsiFile file) {
        if (!(file instanceof AsmFile)) return false;
        AsmNumericLiteral literal = getAsmNumericLiteral(editor, file);
        if (literal == null) return false;
        NumericLiteralValue lit = NumericLiteralValue.parse(literal.getText());
        if (lit == null || lit.getRepresentation() == NumericLiteralValue.Representation.HEX) return false;
        setText(Asm6502Bundle.message("INTN.convert.to.hex", literal.getText(), lit.toHex()));
        return true;
    }

    @Override
    public void invoke(@NotNull Project project, Editor editor, PsiFile file) throws IncorrectOperationException {
        AsmNumericLiteral literal = getAsmNumericLiteral(editor, file);
        if (literal == null) return;
        @Nullable NumericLiteralValue lit = NumericLiteralValue.parse(literal.getText());
        if (lit == null || lit.getRepresentation() == NumericLiteralValue.Representation.HEX) return;
        PsiElement newLiteral = AsmElementFactory.createNumericLiteral(project, lit.toHex());
        literal.replace(newLiteral);
    }
}
