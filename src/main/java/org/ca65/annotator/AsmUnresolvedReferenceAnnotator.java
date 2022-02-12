package org.ca65.annotator;

import com.intellij.codeInspection.ProblemHighlightType;
import com.intellij.lang.annotation.AnnotationHolder;
import com.intellij.lang.annotation.Annotator;
import com.intellij.lang.annotation.HighlightSeverity;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiReference;
import org.ca65.Asm6502Bundle;
import org.ca65.action.DisableProjectReferenceCheckingIntentionAction;
import org.ca65.config.AsmConfiguration;
import org.ca65.psi.AsmDotexpr;
import org.ca65.psi.impl.AsmIdentifierrImpl;
import org.jetbrains.annotations.NotNull;

/**
 * Highlight references to symbols which are not defined in current file. Does not work with includes.
 **/
public class AsmUnresolvedReferenceAnnotator implements Annotator {
    @Override
    public void annotate(@NotNull PsiElement element, @NotNull AnnotationHolder holder) {
        if (!AsmConfiguration.getInstance(element.getProject()).isReferenceCheckingEnabled()) {
            return; // Reference checking is disabled
        }
        if (!(element instanceof AsmIdentifierrImpl)) {
            return;
        }
        if (isInMacroDef(element)) {
            // Identifiers used in macros are not correct
            return;
        }
        PsiReference reference = element.getReference();
        if (reference != null && reference.resolve() != null) {
            return; // definition exists
        }
        String elementName = ((AsmIdentifierrImpl) element).getName();
        holder.newAnnotation(HighlightSeverity.ERROR, Asm6502Bundle.message("INSPECT.unresolved.reference", elementName))
                .range(element.getTextRange())
                .highlightType(ProblemHighlightType.LIKE_UNKNOWN_SYMBOL)
                .withFix(new DisableProjectReferenceCheckingIntentionAction())
                .create();
    }

    private boolean isInMacroDef(PsiElement element) {
        // Go up until element parent is file
        while (element != null && !(element.getParent() instanceof PsiFile)) {
            element = element.getParent();
        }
        if (element == null) {
            return false;
        }
        // Make a guess for performance: most macros are quite short. Assume we are *not* in a macro if we don't
        // find a '.macro' statement after some reasonable number of elements.
        int iterMax = 250;
        int i = 0;
        // Not a nested structure (really should be..), so we scan backwards from here.
        // if we hit a .endmacro or start of file, we are not in a macro.
        // if we hit a .macro, we are.
        while (element != null && i < iterMax) {
            if (element instanceof AsmDotexpr) {
                String elementText = element.getFirstChild() == null ? element.getText() : element.getFirstChild().getText();
                if (".macro".equalsIgnoreCase(elementText) || ".mac".equalsIgnoreCase(elementText)) {
                    return true;
                } else if (".endmacro".equalsIgnoreCase(elementText) || ".endmac".equalsIgnoreCase(elementText)) {
                    return false;
                }
            }
            element = element.getPrevSibling();
            i++;
        }
        return false;
    }
}
