package org.ca65.annotator;

import com.intellij.lang.annotation.AnnotationHolder;
import com.intellij.lang.annotation.Annotator;
import com.intellij.lang.annotation.HighlightSeverity;
import com.intellij.psi.PsiElement;
import org.ca65.Asm6502Bundle;
import org.ca65.action.ConvertStarAssignToOrgIntentionAction;
import org.ca65.psi.AsmStarAssign;
import org.jetbrains.annotations.NotNull;

public class AsmStarAssignAnnotator implements Annotator {
    @Override
    public void annotate(@NotNull PsiElement element, @NotNull AnnotationHolder holder) {
        if (!(element instanceof AsmStarAssign)) return;
        holder.newAnnotation(HighlightSeverity.WARNING, Asm6502Bundle.message("INSPECT.star.assign"))
                .range(element.getTextRange())
                .withFix(new ConvertStarAssignToOrgIntentionAction((AsmStarAssign) element))
                .create();
    }
}
