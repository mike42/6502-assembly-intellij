package org.ca65;

import com.intellij.lang.cacheBuilder.DefaultWordsScanner;
import com.intellij.lang.cacheBuilder.WordsScanner;
import com.intellij.lang.findUsages.FindUsagesProvider;
import com.intellij.psi.PsiElement;
import com.intellij.psi.tree.TokenSet;
import org.ca65.psi.AsmMarker;
import org.ca65.psi.AsmTypes;
import org.ca65.psi.impl.AsmPsiImplUtil;
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
        if(psiElement instanceof AsmMarker) {
            return true;
        }
        return false;
    }

    @Override
    public @Nullable
    @NonNls String getHelpId(@NotNull PsiElement psiElement) {
        return null;
    }

    @Override
    public @Nls @NotNull String getType(@NotNull PsiElement element) {
        if(element instanceof AsmMarker) {
            return "label";
        }
        return "identifier";
    }

    @Override
    public @Nls @NotNull String getDescriptiveName(@NotNull PsiElement element) {
        if(element instanceof AsmMarker) {
            return AsmPsiImplUtil.getName((AsmMarker) element);
        }
        return element.getText();
    }

    @Override
    public @Nls @NotNull String getNodeText(@NotNull PsiElement element, boolean useFullName) {
        return "Nothing";
    }
}
