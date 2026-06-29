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
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;

public class AsmEnumDefMixin extends ASTWrapperPsiElement implements AsmLabelDefinition {
    public AsmEnumDefMixin(@NotNull ASTNode node) {
        super(node);
    }

    @Override
    public @Nullable String getName() {
        AsmIdentifierdef id = findChildByClass(AsmIdentifierdef.class);
        return id != null ? AsmPsiImplUtil.getName(id) : null;
    }

    @Override
    public @Nullable PsiElement getNameIdentifier() {
        AsmIdentifierdef id = findChildByClass(AsmIdentifierdef.class);
        return id != null ? AsmPsiImplUtil.getNameIdentifier(id) : null;
    }

    @Override
    public PsiElement setName(@NlsSafe @NotNull String name) throws IncorrectOperationException {
        AsmIdentifierdef id = findChildByClass(AsmIdentifierdef.class);
        if (id != null) {
            AsmPsiImplUtil.setName(id, name);
        }
        return this;
    }

    @Override
    public @Nullable Icon getIcon(int flags) {
        return AsmIcons.ENUM;
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
                return AsmIcons.ENUM;
            }
        };
    }
}
