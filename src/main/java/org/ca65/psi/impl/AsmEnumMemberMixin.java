package org.ca65.psi.impl;

import com.intellij.extapi.psi.ASTWrapperPsiElement;
import com.intellij.lang.ASTNode;
import com.intellij.navigation.ItemPresentation;
import com.intellij.openapi.util.NlsSafe;
import com.intellij.psi.PsiElement;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.util.IncorrectOperationException;
import org.ca65.AsmIcons;
import org.ca65.helpers.NumericLiteralValue;
import org.ca65.psi.*;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class AsmEnumMemberMixin extends ASTWrapperPsiElement implements AsmLabelDefinition {
    public AsmEnumMemberMixin(@NotNull ASTNode node) {
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
        return AsmIcons.ENUM_MEMBER;
    }

    public ItemPresentation getPresentation() {
        return new ItemPresentation() {
            @Override
            public @Nullable String getPresentableText() {
                String name = getName();
                String value = getDisplayValue();
                if (name != null && value != null) {
                    return name + " = " + value;
                }
                return name;
            }

            @Override
            public @Nullable String getLocationString() {
                return getContainingFile().getName();
            }

            @Override
            public @Nullable Icon getIcon(boolean unused) {
                return AsmIcons.ENUM_MEMBER;
            }
        };
    }

    /**
     * This member's value formatted for display, in the representation the enum uses (lowercase
     * padded hex by default, decimal if the enum's literals are written in decimal). Null when the
     * value cannot be resolved.
     */
    @Nullable
    public String getDisplayValue() {
        Long value = getComputedValue();
        if (value == null) return null;
        return NumericLiteralValue.format(value.intValue(), enumRepresentation());
    }

    /** The numeric representation the enclosing enum is written in, taken from its first explicit literal. */
    @NotNull
    private NumericLiteralValue.Representation enumRepresentation() {
        PsiElement parent = getParent();
        if (parent instanceof AsmEnumDef enumDef) {
            for (AsmEnumMember member : enumDef.getEnumMemberList()) {
                AsmExpr expr = member.getExpr();
                if (expr == null) continue;
                AsmNumericLiteral lit = PsiTreeUtil.findChildOfType(expr, AsmNumericLiteral.class);
                if (lit != null) {
                    NumericLiteralValue parsed = NumericLiteralValue.parse(lit.getText());
                    if (parsed != null) return parsed.getRepresentation();
                }
            }
        }
        return NumericLiteralValue.Representation.HEX;
    }

    /** Computes this member's integer value by walking the preceding siblings. Returns null for unresolvable expressions. */
    @Nullable
    public Long getComputedValue() {
        PsiElement parent = getParent();
        if (!(parent instanceof AsmEnumDef)) return null;
        List<AsmEnumMember> members = ((AsmEnumDef) parent).getEnumMemberList();
        Map<String, Long> nameToValue = new HashMap<>();
        long current = 0;
        boolean unknown = false;
        for (AsmEnumMember member : members) {
            AsmExpr expr = member.getExpr();
            if (expr != null) {
                Long parsed = parseSimpleExpr(expr);
                if (parsed == null) parsed = resolveIdentifierExpr(expr, nameToValue);
                if (parsed != null) {
                    current = parsed;
                    unknown = false;
                } else {
                    unknown = true;
                }
            }
            if (member == this) {
                return unknown ? null : current;
            }
            if (!unknown) {
                String memberName = AsmPsiImplUtil.getName(((AsmEnumMember) member).getIdentifierdef());
                if (memberName != null) nameToValue.put(memberName, current);
                current++;
            }
        }
        return null;
    }

    @Nullable
    private static Long parseSimpleExpr(AsmExpr expr) {
        // Each token inside an expr is wrapped in an `anything` node, so the numeric literal is a
        // grandchild rather than a direct child — search the whole subtree.
        AsmNumericLiteral numLit = PsiTreeUtil.findChildOfType(expr, AsmNumericLiteral.class);
        if (numLit == null) return null;
        // Only handle single-token expressions (no arithmetic)
        String exprText = expr.getText().trim();
        if (!exprText.equals(numLit.getText().trim())) return null;
        NumericLiteralValue val = NumericLiteralValue.parse(numLit.getText());
        return val != null ? (long) val.getValue() : null;
    }

    @Nullable
    private static Long resolveIdentifierExpr(AsmExpr expr, Map<String, Long> nameToValue) {
        // Handle a single bare identifier like `= PINK` that refers to a peer member
        String exprText = expr.getText().trim();
        if (exprText.isEmpty() || !exprText.matches("[A-Za-z_][A-Za-z0-9_]*")) return null;
        return nameToValue.get(exprText);
    }
}
