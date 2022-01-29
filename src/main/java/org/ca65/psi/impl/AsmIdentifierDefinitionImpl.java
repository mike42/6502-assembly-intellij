package org.ca65.psi.impl;

import com.intellij.extapi.psi.ASTWrapperPsiElement;
import com.intellij.lang.ASTNode;
import com.intellij.openapi.util.NlsSafe;
import com.intellij.psi.PsiElement;
import com.intellij.util.IncorrectOperationException;
import org.ca65.psi.AsmIdentifierdef;
import org.ca65.psi.AsmLabelDefinition;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class AsmIdentifierDefinitionImpl extends ASTWrapperPsiElement implements AsmLabelDefinition {
    public AsmIdentifierDefinitionImpl(@NotNull ASTNode node) {
        super(node);
    }

    @Override
    public @Nullable String getName() {
        return AsmPsiImplUtil.getName((AsmIdentifierdef) this);
    }

    @Override
    public @Nullable PsiElement getNameIdentifier() {
        return AsmPsiImplUtil.getNameIdentifier((AsmIdentifierdef) this);
    }

    @Override
    public PsiElement setName(@NlsSafe @NotNull String name) throws IncorrectOperationException {
        return AsmPsiImplUtil.setName((AsmIdentifierdef) this, name);
    }
}