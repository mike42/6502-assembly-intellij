package org.ca65.action;

import com.intellij.codeInsight.TargetElementUtilBase;
import com.intellij.openapi.editor.Editor;
import com.intellij.psi.PsiFile;
import com.intellij.psi.util.PsiTreeUtil;
import org.ca65.psi.AsmNumericLiteral;
import org.jetbrains.annotations.Nullable;

public class IntentionActionUtil {
    @Nullable
    public static AsmNumericLiteral getAsmNumericLiteral(Editor editor, PsiFile file) {
        final int offset = TargetElementUtilBase.adjustOffset(file, editor.getDocument(), editor.getCaretModel().getOffset());
        return PsiTreeUtil.getParentOfType(file.findElementAt(offset), AsmNumericLiteral.class);
    }

    public static boolean isConvertibleHex(String text) {
        if(!text.startsWith("$")) { // Already in hex
            return false;
        }
        return text.length() > 1 && text.length() <= 7;
    }

    public static boolean isConvertibleBin(String text) {
        if(!text.startsWith("%")) {
            return false;
        }
        // In binary, just do length check for 24 bits max.
        return text.length() > 1 && text.length() <= 25;
    }

    public static boolean isConvertibleDec(String text) {
        if(text.startsWith("%") || text.startsWith("$")) {
            return false;
        }
        // Decimal literal. Check length first to avoid overflow weirdness
        int maxValue = 16777215;
        if(text.length() > Integer.toString(maxValue).length()) {
            return false;
        }
        int parsedValue = Integer.parseInt(text);
        return parsedValue >= 0 && parsedValue <= maxValue;
    }
}
