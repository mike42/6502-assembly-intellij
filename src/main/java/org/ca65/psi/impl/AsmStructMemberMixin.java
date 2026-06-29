package org.ca65.psi.impl;

import com.intellij.extapi.psi.ASTWrapperPsiElement;
import com.intellij.lang.ASTNode;
import com.intellij.navigation.ItemPresentation;
import com.intellij.openapi.util.NlsSafe;
import com.intellij.psi.PsiElement;
import com.intellij.psi.impl.source.tree.LeafPsiElement;
import com.intellij.util.IncorrectOperationException;
import org.ca65.AsmIcons;
import org.ca65.psi.AsmLabelDefinition;
import org.ca65.psi.AsmTypes;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;

public class AsmStructMemberMixin extends ASTWrapperPsiElement implements AsmLabelDefinition {
    public AsmStructMemberMixin(@NotNull ASTNode node) {
        super(node);
    }

    @Override
    public @Nullable String getName() {
        ASTNode id = getNode().findChildByType(AsmTypes.IDENTIFIER);
        return id != null ? id.getText() : null;
    }

    @Override
    public @Nullable PsiElement getNameIdentifier() {
        ASTNode id = getNode().findChildByType(AsmTypes.IDENTIFIER);
        return id != null ? id.getPsi() : null;
    }

    @Override
    public PsiElement setName(@NlsSafe @NotNull String newName) throws IncorrectOperationException {
        ASTNode id = getNode().findChildByType(AsmTypes.IDENTIFIER);
        if (id != null && id.getPsi() instanceof LeafPsiElement leaf) {
            leaf.replaceWithText(newName);
        }
        return this;
    }

    @Override
    public @Nullable Icon getIcon(int flags) {
        return AsmIcons.STRUCT_MEMBER;
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
                return AsmIcons.STRUCT_MEMBER;
            }
        };
    }
}
