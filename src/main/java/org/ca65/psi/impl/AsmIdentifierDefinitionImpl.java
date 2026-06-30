package org.ca65.psi.impl;

import com.intellij.extapi.psi.ASTWrapperPsiElement;
import com.intellij.lang.ASTNode;
import com.intellij.openapi.util.NlsSafe;
import com.intellij.psi.PsiElement;
import com.intellij.util.IncorrectOperationException;
import org.ca65.AsmIcons;
import org.ca65.psi.AsmDefineConstantLabel;
import org.ca65.psi.AsmEnumMember;
import org.ca65.psi.AsmIdentifierdef;
import org.ca65.psi.AsmImports;
import org.ca65.psi.AsmLabelDefinition;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;

public class AsmIdentifierDefinitionImpl extends ASTWrapperPsiElement implements AsmLabelDefinition {
    public AsmIdentifierDefinitionImpl(@NotNull ASTNode node) {
        super(node);
    }

    @Override
    public @Nullable Icon getIcon(int flags) {
        // Icon reflects what this name defines, so completion / structure view / find-usages match.
        PsiElement parent = getParent();
        if (parent instanceof AsmEnumMember) {
            return AsmIcons.ENUM_MEMBER;
        }
        if (parent instanceof AsmDefineConstantLabel) {
            return AsmIcons.LABEL;
        }
        if (parent instanceof AsmImports) {
            return AsmIcons.LABEL;
        }
        // Numeric constants (`FOO = $10`) and anything else that resolves to a value.
        return AsmIcons.NUMERIC_CONST;
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