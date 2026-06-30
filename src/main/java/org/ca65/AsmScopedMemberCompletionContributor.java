package org.ca65;

import com.intellij.codeInsight.completion.CompletionContributor;
import com.intellij.codeInsight.completion.CompletionParameters;
import com.intellij.codeInsight.completion.CompletionProvider;
import com.intellij.codeInsight.completion.CompletionResultSet;
import com.intellij.codeInsight.completion.CompletionType;
import com.intellij.codeInsight.lookup.LookupElementBuilder;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiNamedElement;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.util.ProcessingContext;
import org.ca65.psi.AsmFile;
import org.ca65.psi.AsmTypes;
import org.jetbrains.annotations.NotNull;

import static com.intellij.patterns.PlatformPatterns.psiElement;

/**
 * Completes members of an enum/struct/union after {@code Scope::} — and only that scope's members,
 * so {@code SHIRT_SIZE::} offers {@code SMALL/MEDIUM/LARGE} rather than every label in the project.
 */
public class AsmScopedMemberCompletionContributor extends CompletionContributor {
    public AsmScopedMemberCompletionContributor() {
        extend(CompletionType.BASIC,
                psiElement().afterLeaf(psiElement(AsmTypes.SCOPE_ACCESS)),
                new ScopedMemberCompletionProvider());
    }
}

class ScopedMemberCompletionProvider extends CompletionProvider<CompletionParameters> {
    @Override
    public void addCompletions(@NotNull CompletionParameters parameters,
                               @NotNull ProcessingContext context,
                               @NotNull CompletionResultSet resultSet) {
        PsiElement scopeAccess = prevSignificantLeaf(parameters.getPosition());
        if (scopeAccess == null || scopeAccess.getNode().getElementType() != AsmTypes.SCOPE_ACCESS) {
            return;
        }
        PsiElement scopeName = prevSignificantLeaf(scopeAccess);
        if (scopeName == null) {
            return;
        }
        AsmFile file = (AsmFile) parameters.getOriginalFile();
        for (PsiNamedElement member : AsmUtil.findScopeMembers(file, scopeName.getText())) {
            if (member.getName() != null) {
                resultSet.addElement(LookupElementBuilder.createWithIcon(member));
            }
        }
        // Only members belong here; suppress the generic label/symbol suggestions.
        resultSet.stopHere();
    }

    private static PsiElement prevSignificantLeaf(PsiElement element) {
        PsiElement leaf = PsiTreeUtil.prevLeaf(element);
        while (leaf != null && (leaf.getNode().getElementType() == AsmTypes.LINE_WS
                || leaf.getNode().getElementType() == AsmTypes.EOL_WS)) {
            leaf = PsiTreeUtil.prevLeaf(leaf);
        }
        return leaf;
    }
}
