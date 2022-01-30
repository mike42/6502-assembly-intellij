package org.ca65.action;

import com.intellij.codeInsight.intention.impl.BaseIntentionAction;
import com.intellij.codeInspection.util.IntentionFamilyName;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.util.IncorrectOperationException;
import org.apache.commons.lang.StringUtils;
import org.ca65.Asm6502Bundle;
import org.ca65.psi.AsmElementFactory;
import org.ca65.psi.AsmFile;
import org.ca65.psi.AsmNumericLiteral;
import org.jetbrains.annotations.NotNull;

import static org.ca65.action.IntentionActionUtil.*;

public class ConvertNumberToBinaryIntentionAction extends BaseIntentionAction {
    @Override
    public @NotNull @IntentionFamilyName String getFamilyName() {
        return Asm6502Bundle.message("INTN.NAME.convert.to.bin");
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
        if (!canConvertToBinary(text)) {
            return false;
        }
        setText(Asm6502Bundle.message("INTN.convert.to.bin", literal.getText()));
        return true;
    }

    private static boolean canConvertToBinary(String text) {
        return isConvertibleDec(text) || isConvertibleHex(text);
    }

    private static String doConvertToBinary(String str) {
        // Parse
        final int intValue;
        if(str.startsWith("$")) {
            intValue = Integer.parseInt(str.substring(1), 16); // From hex eg. "$ff"
        } else {
            intValue = Integer.parseInt(str, 10);  // From dec eg. "42"
        }
        String binString = Integer.toBinaryString(intValue);
        // Pad to 8 bit, 16 bit, 24-bit values.
        int currentLen = binString.length();
        int remainder = currentLen % 8;
        if(remainder != 0) {
            // Pad to multiple of 8 bits
            binString = StringUtils.leftPad(binString, (currentLen + 8) - remainder, "0");
        }
        return "%" + binString; // Prefixed with %
    }

    @Override
    public void invoke(@NotNull Project project, Editor editor, PsiFile file) throws IncorrectOperationException {
        AsmNumericLiteral literal = getAsmNumericLiteral(editor, file);
        if(literal == null || !canConvertToBinary(literal.getText())) {
            // Some weirdness if this happens
            return;
        }
        String replacement = doConvertToBinary(literal.getText());
        PsiElement newLiteral = AsmElementFactory.createNumericLiteral(project, replacement);
        literal.replace(newLiteral);
    }
}
