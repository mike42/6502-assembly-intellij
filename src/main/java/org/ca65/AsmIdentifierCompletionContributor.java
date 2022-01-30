package org.ca65;

import com.intellij.codeInsight.completion.*;
import com.intellij.codeInsight.lookup.LookupElementBuilder;
import com.intellij.psi.PsiNamedElement;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.util.ProcessingContext;
import org.ca65.psi.AsmTypes;
import org.jetbrains.annotations.NotNull;

import static com.intellij.patterns.PlatformPatterns.psiElement;

public class AsmIdentifierCompletionContributor extends CompletionContributor {
    public AsmIdentifierCompletionContributor() {
        // Offer to auto-complete identifiers after a mnemonic
        extend(CompletionType.BASIC, psiElement().afterLeaf(psiElement(AsmTypes.MNEMONIC)), new LabelCompletionProvider());
    }
}

class LabelCompletionProvider extends CompletionProvider<CompletionParameters> {
    public void addCompletions(@NotNull CompletionParameters parameters,
                               @NotNull ProcessingContext context,
                               @NotNull CompletionResultSet resultSet) {
        PsiNamedElement[] namedElements = PsiTreeUtil.getChildrenOfType(parameters.getOriginalFile(), PsiNamedElement.class);
        if (namedElements != null) {
            for (PsiNamedElement namedElement : namedElements) {
                String elementName = namedElement.getName();
                if (elementName != null) {
                    if (!namedElement.getText().startsWith("@")) {
                        resultSet.addElement(LookupElementBuilder.createWithIcon(namedElement));
                    } else {
                        // Present the label name, but complete with '@' prefix.
                        resultSet.addElement(LookupElementBuilder.create(
                                "@" + namedElement.getName())
                                .withPsiElement(namedElement)
                                .withPresentableText(namedElement.getName())
                                .withIcon(namedElement.getIcon(0))
                        );
                    }
                }
            }
        }
    }
}
