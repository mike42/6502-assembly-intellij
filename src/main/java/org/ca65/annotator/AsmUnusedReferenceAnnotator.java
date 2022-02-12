package org.ca65.annotator;

import com.intellij.codeInspection.ProblemHighlightType;
import com.intellij.lang.annotation.AnnotationHolder;
import com.intellij.lang.annotation.Annotator;
import com.intellij.lang.annotation.HighlightSeverity;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiNameIdentifierOwner;
import com.intellij.psi.PsiReference;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.search.searches.ReferencesSearch;
import com.intellij.util.Query;
import org.ca65.Asm6502Bundle;
import org.ca65.action.DisableProjectReferenceCheckingIntentionAction;
import org.ca65.config.AsmConfiguration;
import org.ca65.psi.AsmLabelDefinition;
import org.ca65.psi.impl.AsmIdentifierDefinitionImpl;
import org.ca65.psi.impl.AsmLabelDefinitionImpl;
import org.jetbrains.annotations.NotNull;

/**
 * Highlight unused definitions in file. Does not catch symbols defined in includes, and is not efficient at all.
 **/
public class AsmUnusedReferenceAnnotator implements Annotator {
    @Override
    public void annotate(@NotNull PsiElement element, @NotNull AnnotationHolder holder) {
        if (!AsmConfiguration.getInstance(element.getProject()).isReferenceCheckingEnabled()) {
            return; // Reference checking is disabled
        }
        if (!(element instanceof AsmLabelDefinitionImpl || element instanceof AsmIdentifierDefinitionImpl)) {
            return;
        }
        if (isReferenced((PsiNameIdentifierOwner) element)) {
            return;
        }
        if(element instanceof AsmLabelDefinitionImpl && ((AsmLabelDefinition) element).getNameIdentifier() == null) {
            return; // References to anonymous labels don't work
        }
        String elementName = ((PsiNameIdentifierOwner) element).getName();
        holder.newAnnotation(HighlightSeverity.WEAK_WARNING, Asm6502Bundle.message("INSPECT.unused.reference", elementName))
                .range(element.getTextRange())
                .highlightType(ProblemHighlightType.LIKE_UNUSED_SYMBOL)
                .withFix(new DisableProjectReferenceCheckingIntentionAction())
                .create();
    }

    private boolean isReferenced(PsiNameIdentifierOwner element) {
        final Query<PsiReference> refs = ReferencesSearch.search(element, GlobalSearchScope.fileScope(element.getContainingFile()), false);
        if (element instanceof AsmLabelDefinitionImpl) {
            // Simple case
            PsiReference firstReference = refs.findFirst();
            return firstReference != null;
        }
        // These turn up references to themselves, using text range to skip.
        for (PsiReference ref : refs) {
            if (!ref.getAbsoluteRange().equals(element.getTextRange())) {
                return true;
            }
        }
        return false;
    }
}
