package org.ca65.annotator;

import com.intellij.lang.annotation.AnnotationHolder;
import com.intellij.lang.annotation.Annotator;
import com.intellij.lang.annotation.HighlightSeverity;
import com.intellij.psi.PsiElement;
import org.ca65.Asm6502Bundle;
import org.ca65.action.ConvertNumberToHexadecimalIntentionAction;
import org.ca65.helpers.NumericLiteralValue;
import org.ca65.psi.AsmNumericLiteral;
import org.jetbrains.annotations.NotNull;

public class AsmZilogHexAnnotator implements Annotator {
    @Override
    public void annotate(@NotNull PsiElement element, @NotNull AnnotationHolder holder) {
        if (!(element instanceof AsmNumericLiteral)) return;
        NumericLiteralValue lit = NumericLiteralValue.parse(element.getText());
        if (lit == null || lit.getRepresentation() != NumericLiteralValue.Representation.ZILOG_HEX) return;
        holder.newAnnotation(HighlightSeverity.WEAK_WARNING,
                        Asm6502Bundle.message("INSPECT.zilog.hex", element.getText()))
                .range(element.getTextRange())
                .withFix(new ConvertNumberToHexadecimalIntentionAction())
                .create();
    }
}
