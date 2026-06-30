package org.ca65.psi.impl;

import com.intellij.extapi.psi.ASTWrapperPsiElement;
import com.intellij.lang.ASTNode;
import com.intellij.navigation.ItemPresentation;
import com.intellij.openapi.util.NlsSafe;
import com.intellij.psi.PsiElement;
import com.intellij.util.IncorrectOperationException;
import org.ca65.AsmIcons;
import org.ca65.psi.AsmIdentifierdef;
import org.ca65.psi.AsmLabelDefinition;
import org.ca65.psi.AsmMarker;
import org.ca65.psi.AsmTypes;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;

public class AsmProcDefMixin extends ASTWrapperPsiElement implements AsmLabelDefinition {
    public AsmProcDefMixin(@NotNull ASTNode node) {
        super(node);
    }

    /**
     * The element naming this proc: an {@code identifierdef} ({@code .proc foo}) or a marker
     * ({@code .proc foo: near}, where {@code foo:} lexes as a label). The name appears before the
     * first end-of-line, so we stop there to avoid picking up a label from the proc body.
     */
    public @Nullable PsiElement getNameElement() {
        for (PsiElement child = getFirstChild(); child != null; child = child.getNextSibling()) {
            if (child.getNode().getElementType() == AsmTypes.EOL_WS) {
                return null;
            }
            if (child instanceof AsmIdentifierdef || child instanceof AsmMarker) {
                return child;
            }
        }
        return null;
    }

    public String getLabelName() {
        return getName();
    }

    @Override
    public @Nullable String getName() {
        PsiElement name = getNameElement();
        if (name instanceof AsmIdentifierdef id) {
            return AsmPsiImplUtil.getLabelName(id);
        }
        if (name instanceof AsmMarker marker) {
            return AsmPsiImplUtil.getLabelName(marker);
        }
        return null;
    }

    @Override
    public @Nullable PsiElement getNameIdentifier() {
        PsiElement name = getNameElement();
        if (name instanceof AsmIdentifierdef id) {
            return AsmPsiImplUtil.getNameIdentifier(id);
        }
        if (name instanceof AsmMarker marker) {
            return AsmPsiImplUtil.getNameIdentifier(marker);
        }
        return null;
    }

    @Override
    public PsiElement setName(@NlsSafe @NotNull String newName) throws IncorrectOperationException {
        PsiElement name = getNameElement();
        if (name instanceof AsmIdentifierdef id) {
            AsmPsiImplUtil.setName(id, newName);
        } else if (name instanceof AsmMarker marker) {
            AsmPsiImplUtil.setName(marker, newName);
        }
        return this;
    }

    @Override
    public @Nullable Icon getIcon(int flags) {
        return AsmIcons.PROC;
    }

    public ItemPresentation getPresentation() {
        return new ItemPresentation() {
            @Override
            public @Nullable String getPresentableText() {
                return getName();
            }

            @Override
            public @Nullable String getLocationString() {
                return getContainingFile().getName();
            }

            @Override
            public @Nullable Icon getIcon(boolean unused) {
                return AsmIcons.PROC;
            }
        };
    }
}
