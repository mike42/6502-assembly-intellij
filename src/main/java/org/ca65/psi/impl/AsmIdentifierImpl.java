package org.ca65.psi.impl;

import com.intellij.extapi.psi.ASTWrapperPsiElement;
import com.intellij.lang.ASTNode;
import com.intellij.openapi.util.NlsSafe;
import com.intellij.psi.PsiElement;
import com.intellij.util.IncorrectOperationException;
import org.ca65.psi.AsmIdentifier;
import org.ca65.psi.AsmIdentifierr;
import org.ca65.psi.AsmMarker;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class AsmIdentifierImpl extends ASTWrapperPsiElement implements AsmIdentifier {
    public AsmIdentifierImpl(@NotNull ASTNode node) {
        super(node);
    }

    @Override
    public @Nullable String getName() {
        return AsmPsiImplUtil.getName((AsmIdentifierr) this);
    }

    @Override
    public PsiElement setName(@NlsSafe @NotNull String name) throws IncorrectOperationException {
        return AsmPsiImplUtil.setName((AsmIdentifierr) this, name);
    }
}
