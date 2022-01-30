package org.ca65.action;

import com.intellij.codeInsight.intention.impl.BaseIntentionAction;
import com.intellij.codeInspection.util.IntentionFamilyName;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.util.IncorrectOperationException;
import org.ca65.Asm6502Bundle;
import org.ca65.psi.AsmElementFactory;
import org.ca65.psi.AsmFile;
import org.ca65.psi.AsmNumericLiteral;
import org.jetbrains.annotations.NotNull;

import static org.ca65.action.IntentionActionUtil.*;

public class ConvertNumberToDecimalIntentionAction extends BaseIntentionAction {
    @Override
    public @NotNull @IntentionFamilyName String getFamilyName() {
        return Asm6502Bundle.message("INTN.NAME.convert.to.dec");
    }

    @Override
    public boolean isAvailable(@NotNull Project project, Editor editor, PsiFile file) {
        if (!(file instanceof AsmFile)) {
            return false;
        }
        AsmNumericLiteral literal = getAsmNumericLiteral(editor, file);
        if (literal == null) { // Caret is not over a numeric literal
            return false;
        }
        String text = literal.getText();
        if (!canConvertToDecimal(text)) {
            return false;
        }
        setText(Asm6502Bundle.message("INTN.convert.to.dec", literal.getText()));
        return true;
    }

    private static boolean canConvertToDecimal(String text) {
        return isConvertibleBin(text) || isConvertibleHex(text);
    }

    private static String doConvertToDecimal(String str) {
        // Parse
        final int intValue;
        if(str.startsWith("%")) {
            intValue = Integer.parseInt(str.substring(1), 2); // From bin eg. "%01010"
        } else {
            intValue = Integer.parseInt(str.substring(1), 16); // From hex eg. "$ff"
        }
        return Integer.toString(intValue);
    }

    @Override
    public void invoke(@NotNull Project project, Editor editor, PsiFile file) throws IncorrectOperationException {
        AsmNumericLiteral literal = getAsmNumericLiteral(editor, file);
        if(literal == null || !canConvertToDecimal(literal.getText())) {
            // Some weirdness if this happens
            return;
        }
        String replacement = doConvertToDecimal(literal.getText());
        PsiElement newLiteral = AsmElementFactory.createNumericLiteral(project, replacement);
        literal.replace(newLiteral);
    }
}
