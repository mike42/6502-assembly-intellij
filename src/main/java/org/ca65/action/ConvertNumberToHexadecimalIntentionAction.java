package org.ca65.action;

import com.intellij.codeInsight.TargetElementUtilBase;
import com.intellij.codeInsight.intention.impl.BaseIntentionAction;
import com.intellij.codeInspection.util.IntentionFamilyName;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.util.IncorrectOperationException;
import org.ca65.Asm6502Bundle;
import org.ca65.psi.AsmElementFactory;
import org.ca65.psi.AsmFile;
import org.ca65.psi.AsmNumericLiteral;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class ConvertNumberToHexadecimalIntentionAction extends BaseIntentionAction {
    @Override
    public @NotNull @IntentionFamilyName String getFamilyName() {
        return Asm6502Bundle.message("INTN.NAME.convert.to.hex");
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
        if (!canConvertToHex(text)) {
            return false;
        }
        setText(Asm6502Bundle.message("INTN.convert.to.hex", literal.getText()));
        return true;
    }

    @Nullable
    private AsmNumericLiteral getAsmNumericLiteral(Editor editor, PsiFile file) {
        final int offset = TargetElementUtilBase.adjustOffset(file, editor.getDocument(), editor.getCaretModel().getOffset());
        return PsiTreeUtil.getParentOfType(file.findElementAt(offset), AsmNumericLiteral.class);
    }

    private static boolean canConvertToHex(String str) {
        if(str.startsWith("$")) { // Already in hex
            return false;
        }
        if(str.startsWith("%")) {
            // In binary, just do length check for 24 bits max.
            return str.length() > 1 && str.length() <= 25;
        }
        // Decimal literal. Check length first to avoid overflow weirdness
        int maxValue = 16777215;
        if(str.length() > Integer.toString(maxValue).length()) {
            return false;
        }
        int parsedValue = Integer.parseInt(str);
        return parsedValue >= 0 && parsedValue <= maxValue;
    }

    private static String doConvertToHex(String str) {
        // Parse
        final int intValue;
        if(str.startsWith("%")) {
            intValue = Integer.parseInt(str.substring(1), 2);
        } else {
            intValue = Integer.parseInt(str, 10);
        }
        String rawHexString = Integer.toHexString(intValue);
        if(rawHexString.length() % 2 == 1) {    // Even number of digits. Expect 8 bit, 16 bit, 24-bit values.
            rawHexString = "0" + rawHexString;
        }
        return "$" + rawHexString; // Prefixed with $
    }

    @Override
    public void invoke(@NotNull Project project, Editor editor, PsiFile file) throws IncorrectOperationException {
        AsmNumericLiteral literal = getAsmNumericLiteral(editor, file);
        if(literal == null || !canConvertToHex(literal.getText())) {
            // Some weirdness if this happens
            return;
        }
        String replacement = doConvertToHex(literal.getText());
        PsiElement newLiteral = AsmElementFactory.createNumericLiteral(project, replacement);
        literal.replace(newLiteral);
    }
}
