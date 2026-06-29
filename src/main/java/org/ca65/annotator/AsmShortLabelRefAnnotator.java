package org.ca65.annotator;

import com.intellij.codeInspection.ProblemHighlightType;
import com.intellij.lang.annotation.AnnotationHolder;
import com.intellij.lang.annotation.Annotator;
import com.intellij.lang.annotation.HighlightSeverity;
import com.intellij.psi.PsiElement;
import com.intellij.psi.impl.source.tree.LeafPsiElement;
import org.ca65.Asm6502Bundle;
import org.ca65.action.NormalizeShortLabelRefIntentionAction;
import org.ca65.psi.AsmTypes;
import org.jetbrains.annotations.NotNull;

/**
 * Flags unnamed label references that use angle brackets (:>, :<) and offers
 * to normalise them to the more common :+ / :- form.
 */
public class AsmShortLabelRefAnnotator implements Annotator {
    @Override
    public void annotate(@NotNull PsiElement element, @NotNull AnnotationHolder holder) {
        if (!(element instanceof LeafPsiElement leaf)) return;
        if (leaf.getElementType() != AsmTypes.SHORTLABEL_REF) return;
        String text = leaf.getText();
        if (text.indexOf('<') < 0 && text.indexOf('>') < 0) return;
        holder.newAnnotation(HighlightSeverity.WEAK_WARNING,
                        Asm6502Bundle.message("INSPECT.shortlabel.angle", text))
                .range(leaf.getTextRange())
                .highlightType(ProblemHighlightType.WEAK_WARNING)
                .withFix(new NormalizeShortLabelRefIntentionAction())
                .create();
    }
}
