package org.ca65.psi.impl;

import com.intellij.extapi.psi.ASTWrapperPsiElement;
import com.intellij.lang.ASTNode;
import com.intellij.openapi.util.NlsSafe;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiReference;
import com.intellij.util.IncorrectOperationException;
import org.ca65.AsmUtil;
import org.ca65.psi.*;
import org.ca65.psi.impl.AsmPsiImplUtil;
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
                String name = getName();
                if (name == null) {
                    return null;
                }
                AsmFile file = (AsmFile) parent.getContainingFile();

                // Scoped access: "Scope::member". Each element inside an expr is wrapped in an
                // `anything` node, so the "::" and the scope name are siblings of our enclosing
                // `anything`, not of the identifierr itself.
                PsiElement refNode = parent.getParent() instanceof AsmAnything ? parent.getParent() : parent;
                PsiElement prev = getPrevSignificantSibling(refNode);
                if (isScopeAccess(prev)) {
                    String scopeName = identifierNameOf(getPrevSignificantSibling(prev));
                    return scopeName == null ? null : AsmUtil.findScopedMember(file, scopeName, name);
                }

                // A bare reference inside an enum body resolves against peer members first
                // (e.g. `LIGHTRED = PINK`). Walk up to the enclosing enum, if any.
                PsiElement cur = parent.getParent();
                while (cur != null && !(cur instanceof PsiFile)) {
                    if (cur instanceof AsmEnumDef enumDef) {
                        for (AsmEnumMember member : enumDef.getEnumMemberList()) {
                            AsmIdentifierdef memberDef = member.getIdentifierdef();
                            if (name.equals(AsmPsiImplUtil.getName(memberDef))) {
                                return memberDef;
                            }
                        }
                        break;
                    }
                    cur = cur.getParent();
                }
                return AsmUtil.findDefinition(file, name);
            }

            private PsiElement getPrevSignificantSibling(PsiElement element) {
                PsiElement sib = element.getPrevSibling();
                while (sib != null && (sib.getNode().getElementType() == AsmTypes.LINE_WS
                        || sib.getNode().getElementType() == AsmTypes.EOL_WS)) {
                    sib = sib.getPrevSibling();
                }
                return sib;
            }

            /** True if {@code e} is a "::" scope-access token, whether bare or wrapped in an `anything` node. */
            private boolean isScopeAccess(PsiElement e) {
                if (e == null) {
                    return false;
                }
                if (e.getNode().getElementType() == AsmTypes.SCOPE_ACCESS) {
                    return true;
                }
                if (e instanceof AsmAnything) {
                    PsiElement child = e.getFirstChild();
                    return child != null && child.getNode().getElementType() == AsmTypes.SCOPE_ACCESS;
                }
                return false;
            }

            /** Name of an identifier, whether bare or wrapped in an `anything` node; null otherwise. */
            private String identifierNameOf(PsiElement e) {
                if (e instanceof AsmIdentifierr id) {
                    return AsmPsiImplUtil.getName(id);
                }
                if (e instanceof AsmAnything anything && anything.getIdentifierr() != null) {
                    return AsmPsiImplUtil.getName(anything.getIdentifierr());
                }
                return null;
            }

            @Override
            public @NotNull @NlsSafe String getCanonicalText() {
                String name = getName();
                return name != null ? name : "";
            }

            @Override
            public PsiElement handleElementRename(@NotNull String newElementName) throws IncorrectOperationException {
                ASTNode identifierNode = parent.getNode().findChildByType(AsmTypes.IDENTIFIER);
                if (identifierNode == null) {
                    return parent;
                }
                AsmIdentifier newIdentifier = AsmElementFactory.createIdentifier(parent.getProject(), newElementName);
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
                // Match by actually resolving (which honours the .include graph) rather than by
                // name alone, so identically-named symbols in unrelated files are not treated as
                // usages of each other.
                PsiElement resolved = resolve();
                if (resolved == null) {
                    return false;
                }
                return parent.getManager().areElementsEquivalent(canonicalDef(resolved), canonicalDef(element));
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

    /**
     * Normalise a definition element to the single PSI element used for identity comparison, so
     * that a wrapper (enum/struct/union definition or enum member) and the inner {@code identifierdef}
     * that names it compare equal. {@link #resolve()} returns the inner {@code identifierdef} (or a
     * struct member / marker), whereas Find Usages may target the wrapper.
     */
    private static PsiElement canonicalDef(PsiElement element) {
        if (element instanceof AsmEnumMember m) {
            return m.getIdentifierdef();
        }
        if (element instanceof AsmEnumDef d) {
            return d.getIdentifierdef();
        }
        if (element instanceof AsmStructDef d) {
            return d.getIdentifierdef();
        }
        if (element instanceof AsmUnionDef d) {
            return d.getIdentifierdef();
        }
        return element;
    }
}
