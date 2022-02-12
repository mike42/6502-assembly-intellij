package org.ca65.annotator;

import com.intellij.codeInspection.LocalQuickFix;
import com.intellij.codeInspection.ProblemHighlightType;
import com.intellij.lang.annotation.AnnotationHolder;
import com.intellij.lang.annotation.Annotator;
import com.intellij.lang.annotation.HighlightSeverity;
import com.intellij.psi.PsiElement;
import org.apache.commons.lang.StringUtils;
import org.ca65.Asm6502Bundle;
import org.ca65.action.IntentionActionUtil;
import org.ca65.action.PadNumberIntentionAction;
import org.ca65.psi.AsmNumericLiteral;
import org.jetbrains.annotations.NotNull;

/**
 * Provide weak warnings for hex and binary literals which are not whole bytes, eg. 7-digit binary numbers.
 */
public class AsmNumericLiteralAnnotator implements Annotator {
    @Override
    public void annotate(@NotNull PsiElement element, @NotNull AnnotationHolder holder) {
        if(!(element instanceof AsmNumericLiteral)) {
            return;
        }
        // Separate checks for hex vs binary literals
        String elementText = element.getText();
        if(elementText.startsWith("$") &&elementText.length() > 1) {
            annotateHex(element, holder, elementText.substring(1));
        }
        if(elementText.startsWith("%") &&elementText.length() > 1) {
            annotateBinary(element, holder, elementText.substring(1));
        }
    }

    private void annotateBinary(PsiElement element, AnnotationHolder holder, String binString) {
        int len = binString.length();
        int remainder = binString.length() % 8;
        if(remainder == 0) {
            return;
        }
        String suggestedReplacement = "%" + StringUtils.leftPad(binString, (len + 8) - remainder, "0");
        holder.newAnnotation(HighlightSeverity.WEAK_WARNING, Asm6502Bundle.message("INSPECT.binary.literal.length", element.getText()))
                .range(element.getTextRange())
                .highlightType(ProblemHighlightType.WEAK_WARNING)
                .withFix(new PadNumberIntentionAction(element, suggestedReplacement))
                .create();
    }

    private void annotateHex(PsiElement element, AnnotationHolder holder, String hexString) {
        if(hexString.length() % 2 == 0) {
            return;
        }
        String suggestedReplacement = "$0" + hexString;
        holder.newAnnotation(HighlightSeverity.WEAK_WARNING, Asm6502Bundle.message("INSPECT.hex.literal.length", element.getText()))
                .range(element.getTextRange())
                .highlightType(ProblemHighlightType.WEAK_WARNING)
                .withFix(new PadNumberIntentionAction(element, suggestedReplacement))
                .create();
    }
}
