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
}
