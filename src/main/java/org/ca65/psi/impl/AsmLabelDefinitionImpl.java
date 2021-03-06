package org.ca65.psi.impl;

import com.intellij.extapi.psi.ASTWrapperPsiElement;
import com.intellij.icons.AllIcons;
import com.intellij.lang.ASTNode;
import com.intellij.openapi.util.NlsSafe;
import com.intellij.psi.PsiElement;
import com.intellij.util.IncorrectOperationException;
import org.ca65.AsmIcons;
import org.ca65.psi.AsmLabelDefinition;
import org.ca65.psi.AsmMarker;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;

public class AsmLabelDefinitionImpl extends ASTWrapperPsiElement implements AsmLabelDefinition {
    public AsmLabelDefinitionImpl(@NotNull ASTNode node) {
        super(node);
    }

    @Override
    public @Nullable String getName() {
        return AsmPsiImplUtil.getName((AsmMarker) this);
    }

    @Override
    public @Nullable PsiElement getNameIdentifier() {
        return AsmPsiImplUtil.getNameIdentifier((AsmMarker) this);
    }

    @Override
    public PsiElement setName(@NlsSafe @NotNull String name) throws IncorrectOperationException {
        return AsmPsiImplUtil.setName((AsmMarker) this, name);
    }

    @Override
    public @Nullable Icon getIcon(int flags) {
        return this.getText().startsWith("@") || this.getText().startsWith(":") ? AllIcons.Nodes.Annotationtype : AsmIcons.LABEL;
    }
}
