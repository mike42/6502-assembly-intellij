package org.ca65.psi.impl;

import com.intellij.extapi.psi.ASTWrapperPsiElement;
import com.intellij.lang.ASTNode;
import com.intellij.openapi.util.NlsSafe;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiReference;
import com.intellij.util.IncorrectOperationException;
import org.ca65.AsmUtil;
import org.ca65.psi.AsmFile;
import org.ca65.psi.AsmIdentifier;
import org.ca65.psi.AsmIdentifierr;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class AsmIdentifierImpl extends ASTWrapperPsiElement implements AsmIdentifier {
    public AsmIdentifierImpl(@NotNull ASTNode node) {
        super(node);
    }

    public PsiReference getReference() {
        AsmIdentifierr parent = (AsmIdentifierr)this;
        return new PsiReference() {
            @Override
            public @NotNull PsiElement getElement() {
                return parent;
            }

            @Override
            public @NotNull TextRange getRangeInElement() {
                String name = getName();
                if(name != null) {
                    return TextRange.allOf(name);
                } else {
                    return TextRange.EMPTY_RANGE;
                }
            }

            @Override
            public @Nullable PsiElement resolve() {
                return AsmUtil.findDefinition((AsmFile)getContainingFile().getContainingFile(), getName());
            }

            @Override
            public @NotNull @NlsSafe String getCanonicalText() {
                return "Beans";
            }

            @Override
            public PsiElement handleElementRename(@NotNull String newElementName) throws IncorrectOperationException {
                return null;
            }

            @Override
            public PsiElement bindToElement(@NotNull PsiElement element) throws IncorrectOperationException {
                return null;
            }

            @Override
            public boolean isReferenceTo(@NotNull PsiElement element) {
                return false;
            }

            @Override
            public boolean isSoft() {
                return false;
            }
        };
    }

    @Override
    public @Nullable String getName() {
        return AsmPsiImplUtil.getName((AsmIdentifierr) this);
    }
}
