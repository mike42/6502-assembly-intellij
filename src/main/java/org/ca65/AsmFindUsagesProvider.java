package org.ca65;

import com.intellij.lang.cacheBuilder.DefaultWordsScanner;
import com.intellij.lang.cacheBuilder.WordsScanner;
import com.intellij.lang.findUsages.FindUsagesProvider;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiNamedElement;
import com.intellij.psi.tree.TokenSet;
import org.ca65.psi.*;
import org.ca65.psi.impl.AsmPsiImplUtil;
import org.ca65.psi.impl.AsmStructMemberMixin;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class AsmFindUsagesProvider implements FindUsagesProvider {
    @Override
    public @Nullable WordsScanner getWordsScanner() {
        return new DefaultWordsScanner(new AsmLexerAdapter(),
                TokenSet.create(AsmTypes.LABEL, AsmTypes.IDENTIFIER, AsmTypes.LOCAL_LABEL_REF),
                TokenSet.create(AsmTypes.COMMENT),
                TokenSet.create(AsmTypes.INT_LITERAL, AsmTypes.CHAR_LITERAL, AsmTypes.STRING_LITERAL));
    }

    @Override
    public boolean canFindUsagesFor(@NotNull PsiElement psiElement) {
        return psiElement instanceof AsmMarker
                || psiElement instanceof AsmIdentifierdef
                || psiElement instanceof AsmEnumDef
                || psiElement instanceof AsmEnumMember
                || psiElement instanceof AsmStructDef
                || psiElement instanceof AsmUnionDef
                || psiElement instanceof AsmStructMember;
    }

    @Override
    public @Nullable
    @NonNls String getHelpId(@NotNull PsiElement psiElement) {
        return null;
    }

    @Override
    public @Nls @NotNull String getType(@NotNull PsiElement element) {
        if (element instanceof AsmMarker) return "label";
        if (element instanceof AsmEnumDef) return "enum";
        if (element instanceof AsmEnumMember) return "enum member";
        if (element instanceof AsmStructDef) return "struct";
        if (element instanceof AsmUnionDef) return "union";
        if (element instanceof AsmStructMember) return "struct member";
        return "identifier";
    }

    @Override
    public @Nls @NotNull String getDescriptiveName(@NotNull PsiElement element) {
        if (element instanceof AsmMarker m) return AsmPsiImplUtil.getName(m);
        if (element instanceof AsmIdentifierdef id) return AsmPsiImplUtil.getName(id);
        if (element instanceof AsmEnumDef || element instanceof AsmEnumMember
                || element instanceof AsmStructDef || element instanceof AsmUnionDef
                || element instanceof AsmStructMember) {
            String name = ((PsiNamedElement) element).getName();
            return name != null ? name : "(anonymous)";
        }
        return element.getText();
    }

    @Override
    public @Nls @NotNull String getNodeText(@NotNull PsiElement element, boolean useFullName) {
        return "Nothing";
    }
}
