package org.ca65.psi.impl;

import com.intellij.extapi.psi.ASTWrapperPsiElement;
import com.intellij.lang.ASTNode;
import com.intellij.openapi.util.NlsSafe;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiReference;
import com.intellij.util.IncorrectOperationException;
import org.ca65.AsmUtil;
import org.ca65.psi.*;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class AsmLocalLabelRefImpl extends ASTWrapperPsiElement implements AsmLocalLabelRef {
    public AsmLocalLabelRefImpl(@NotNull ASTNode node) {
        super(node);
    }

    public PsiReference getReference() {
        AsmLocalLabelRrefImpl parent = (AsmLocalLabelRrefImpl)this;
        return new PsiReference() {
            @Override
            public @NotNull PsiElement getElement() {
                return parent;
            }

            @Override
            public @NotNull TextRange getRangeInElement() {
                // TODO check this is correct, might need to remove @.
                String name = getName();
                if(name != null) {
                    return new TextRange(1, name.length() + 1);
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
                // TODO what is this?
                return "Beans";
            }

            @Override
            public PsiElement handleElementRename(@NotNull String newElementName) throws IncorrectOperationException {
                ASTNode identifierNode = parent.getNode().findChildByType(AsmTypes.LOCAL_LABEL_REF);
                if (identifierNode == null) {
                    return parent;
                }
                AsmLocalLabelRef newIdentifier = AsmElementFactory.createLocalLabelRef(parent.getProject(), newElementName);
                ASTNode newIdentifierNode = newIdentifier.getFirstChild().getNode();
                parent.getNode().replaceChild(identifierNode, newIdentifierNode);
                return parent;
            }

            @Override
            public PsiElement bindToElement(@NotNull PsiElement element) throws IncorrectOperationException {
                return null;
            }

            @Override
            public boolean isReferenceTo(@NotNull PsiElement element) {
                if(element instanceof AsmMarkerImpl) {
                    String myName = getName();
                    String theirName = ((AsmMarkerImpl) element).getName();
                    return myName != null && myName.equals(theirName);
                }
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
        return AsmPsiImplUtil.getName((AsmLocalLabelRrefImpl) this);
    }
}
