package org.ca65.annotator;

import com.intellij.codeInspection.ProblemHighlightType;
import com.intellij.lang.annotation.AnnotationHolder;
import com.intellij.lang.annotation.Annotator;
import com.intellij.lang.annotation.HighlightSeverity;
import com.intellij.psi.PsiElement;
import org.ca65.Asm6502Bundle;
import org.ca65.action.PadNumberIntentionAction;
import org.ca65.action.RemoveNumericLiteralUnderscoresIntentionAction;
import org.ca65.helpers.NumericLiteralValue;
import org.ca65.psi.AsmNumericLiteral;
import org.jetbrains.annotations.NotNull;

/**
 * Weak warnings for numeric literals with formatting issues:
 *
 *   Underscores present — requires .FEATURE UNDERLINE_IN_NUMBERS (non-default).
 *     The fix produces a fully normalised replacement (lowercase, correct padding) so
 *     the user does not need further quick-fixes after applying it.
 *     When underscores are present the length warning is suppressed; one fix covers both.
 *
 *   Odd hex digit count ($F → $0f) or binary digit count not a multiple of 8.
 *     Only raised when no underscores are present.
 *
 * Zilog-style hex literals are handled entirely by AsmZilogHexAnnotator.
 */
public class AsmNumericLiteralAnnotator implements Annotator {
    @Override
    public void annotate(@NotNull PsiElement element, @NotNull AnnotationHolder holder) {
        if (!(element instanceof AsmNumericLiteral literal)) return;
        NumericLiteralValue lit = NumericLiteralValue.parse(element.getText());
        if (lit == null) return;
        boolean hasUnderscores = element.getText().contains("_");
        switch (lit.getRepresentation()) {
            case HEX -> {
                if (hasUnderscores) annotateUnderscores(literal, holder, lit.toHex());
                else annotateHex(element, holder);
            }
            case BINARY -> {
                if (hasUnderscores) annotateUnderscores(literal, holder, lit.toBinary());
                else annotateBinary(element, holder);
            }
            case DECIMAL -> {
                if (hasUnderscores) annotateUnderscores(literal, holder, lit.toDecimal());
            }
            case ZILOG_HEX -> { /* handled by AsmZilogHexAnnotator */ }
        }
    }

    private void annotateUnderscores(@NotNull AsmNumericLiteral element,
                                     @NotNull AnnotationHolder holder,
                                     @NotNull String replacement) {
        holder.newAnnotation(HighlightSeverity.WEAK_WARNING,
                        Asm6502Bundle.message("INSPECT.underscores.in.literal", element.getText()))
                .range(element.getTextRange())
                .highlightType(ProblemHighlightType.WEAK_WARNING)
                .withFix(new RemoveNumericLiteralUnderscoresIntentionAction(element, replacement))
                .create();
    }

    private void annotateHex(@NotNull PsiElement element, @NotNull AnnotationHolder holder) {
        String digits = element.getText().substring(1); // strip $
        if (digits.length() % 2 == 0) return;
        String suggested = "$0" + digits.toLowerCase();
        holder.newAnnotation(HighlightSeverity.WEAK_WARNING,
                        Asm6502Bundle.message("INSPECT.hex.literal.length", element.getText()))
                .range(element.getTextRange())
                .highlightType(ProblemHighlightType.WEAK_WARNING)
                .withFix(new PadNumberIntentionAction(element, suggested))
                .create();
    }

    private void annotateBinary(@NotNull PsiElement element, @NotNull AnnotationHolder holder) {
        String digits = element.getText().substring(1); // strip %
        int remainder = digits.length() % 8;
        if (remainder == 0) return;
        String suggested = "%" + "0".repeat(8 - remainder) + digits;
        holder.newAnnotation(HighlightSeverity.WEAK_WARNING,
                        Asm6502Bundle.message("INSPECT.binary.literal.length", element.getText()))
                .range(element.getTextRange())
                .highlightType(ProblemHighlightType.WEAK_WARNING)
                .withFix(new PadNumberIntentionAction(element, suggested))
                .create();
    }
}
