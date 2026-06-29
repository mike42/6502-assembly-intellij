package org.ca65.psi.impl;

import com.intellij.extapi.psi.ASTWrapperPsiElement;
import com.intellij.lang.ASTNode;
import com.intellij.psi.ContributedReferenceHost;
import com.intellij.psi.PsiReference;
import com.intellij.psi.impl.source.resolve.reference.ReferenceProvidersRegistry;
import org.jetbrains.annotations.NotNull;

/**
 * Mixin for {@code dotexpr}. Directives such as {@code .include "x16.inc"} carry a file reference on
 * their path argument; declaring this element a {@link ContributedReferenceHost} lets the platform
 * route reference queries to {@link com.intellij.psi.PsiReferenceContributor}s (here
 * {@link org.ca65.AsmIncludeReferenceContributor}), keeping reference-building logic out of the PSI.
 */
public class AsmDotexprMixin extends ASTWrapperPsiElement implements ContributedReferenceHost {
    public AsmDotexprMixin(@NotNull ASTNode node) {
        super(node);
    }

    @Override
    public PsiReference @NotNull [] getReferences() {
        return ReferenceProvidersRegistry.getReferencesFromProviders(this);
    }
}
