package org.ca65;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.*;
import org.ca65.psi.AsmMarker;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

public class AsmLabelReference extends PsiReferenceBase<PsiElement> implements PsiPolyVariantReference {
    private final String label;

    public AsmLabelReference(@NotNull PsiElement element, TextRange textRange) {
        super(element, textRange);
        label = element.getText().substring(textRange.getStartOffset(), textRange.getEndOffset());
        // TODO check this
        System.out.println("LABEL" + label);
    }

    @Override
    public ResolveResult @NotNull [] multiResolve(boolean incompleteCode) {
        Project project = myElement.getProject();
        final List<AsmMarker> markers = AsmUtil.findLabels(project, label);
        List<ResolveResult> results = new ArrayList<>();
        for (AsmMarker marker : markers) {
            results.add(new PsiElementResolveResult(marker));
        }
        return results.toArray(new ResolveResult[results.size()]);
    }

    @Override
    public @Nullable PsiElement resolve() {
        ResolveResult[] resolveResults = multiResolve(false);
        return resolveResults.length == 1 ? resolveResults[0].getElement() : null;
    }

    @Override
    public Object @NotNull [] getVariants() {
        return super.getVariants();
    }
}
