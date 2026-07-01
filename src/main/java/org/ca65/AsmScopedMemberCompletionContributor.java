package org.ca65;

import com.intellij.codeInsight.completion.CompletionContributor;
import com.intellij.codeInsight.completion.CompletionParameters;
import com.intellij.codeInsight.completion.CompletionProvider;
import com.intellij.codeInsight.completion.CompletionResultSet;
import com.intellij.codeInsight.completion.CompletionType;
import com.intellij.codeInsight.lookup.LookupElementBuilder;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiNamedElement;
import com.intellij.psi.tree.IElementType;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.util.ProcessingContext;
import org.ca65.psi.AsmTypes;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;

import static com.intellij.patterns.PlatformPatterns.psiElement;

/**
 * Completes members of the scope/struct/union/enum named by a {@code A::B::} prefix — and only that
 * container's members, so {@code SYSTEM::} offers its members rather than every label in the project.
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
        // Walk the "A::B::" prefix backwards from the caret into a list of scope names.
        List<String> scopes = new ArrayList<>();
        PsiElement leaf = prevSignificantLeaf(parameters.getPosition());
        while (leaf != null && leaf.getNode().getElementType() == AsmTypes.SCOPE_ACCESS) {
            PsiElement nameLeaf = prevSignificantLeaf(leaf);
            if (nameLeaf == null || !isIdentifier(nameLeaf.getNode().getElementType())) {
                break;
            }
            scopes.add(0, nameLeaf.getText());
            leaf = prevSignificantLeaf(nameLeaf);
        }
        if (scopes.isEmpty()) {
            return;
        }

        PsiFile original = parameters.getOriginalFile();
        PsiElement contextElement = original.findElementAt(Math.max(0, parameters.getOffset() - 1));
        if (contextElement == null) {
            contextElement = original;
        }
        for (PsiNamedElement member : AsmUtil.scopeMembersForCompletion(contextElement, scopes)) {
            if (member.getName() != null) {
                resultSet.addElement(LookupElementBuilder.createWithIcon(member));
            }
        }
        // Only members belong here; suppress the generic label/symbol suggestions.
        resultSet.stopHere();
    }

    private static boolean isIdentifier(IElementType type) {
        return type == AsmTypes.IDENTIFIER || type == AsmTypes.MNEMONIC;
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
