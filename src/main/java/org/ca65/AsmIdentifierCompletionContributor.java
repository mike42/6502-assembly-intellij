package org.ca65;

import com.intellij.codeInsight.AutoPopupController;
import com.intellij.codeInsight.completion.*;
import com.intellij.codeInsight.lookup.LookupElement;
import com.intellij.codeInsight.lookup.LookupElementBuilder;
import com.intellij.psi.PsiNamedElement;
import com.intellij.util.ProcessingContext;
import org.ca65.psi.AsmEnumDef;
import org.ca65.psi.AsmFile;
import org.ca65.psi.AsmStructDef;
import org.ca65.psi.AsmTypes;
import org.ca65.psi.AsmUnionDef;
import org.jetbrains.annotations.NotNull;

import static com.intellij.patterns.PlatformPatterns.psiElement;
import static com.intellij.patterns.StandardPatterns.or;

public class AsmIdentifierCompletionContributor extends CompletionContributor {
    public AsmIdentifierCompletionContributor() {
        // Offer symbols in operand position: after a mnemonic (`lda FOO`) or after `#` (`lda #FOO`).
        extend(CompletionType.BASIC,
                or(psiElement().afterLeaf(psiElement(AsmTypes.MNEMONIC)),
                        psiElement().afterLeaf(psiElement(AsmTypes.CONSTEXPR))),
                new LabelCompletionProvider());
    }

    /** Appends {@code ::} after a completed scope name and reopens the popup to pick a member. */
    static void appendScopeAccess(@NotNull InsertionContext context, @NotNull LookupElement item) {
        int tail = context.getTailOffset();
        context.getDocument().insertString(tail, "::");
        context.commitDocument();
        context.getEditor().getCaretModel().moveToOffset(tail + 2);
        AutoPopupController.getInstance(context.getProject()).scheduleAutoPopup(context.getEditor());
    }
}

class LabelCompletionProvider extends CompletionProvider<CompletionParameters> {
    public void addCompletions(@NotNull CompletionParameters parameters,
                               @NotNull ProcessingContext context,
                               @NotNull CompletionResultSet resultSet) {
        AsmFile file = (AsmFile) parameters.getOriginalFile();
        for (PsiNamedElement namedElement : AsmUtil.collectSymbols(file)) {
            String elementName = namedElement.getName();
            if (elementName == null) {
                continue;
            }
            if (namedElement.getText().startsWith("@")) {
                // Present the local label name, but complete with the '@' prefix.
                resultSet.addElement(LookupElementBuilder.create("@" + elementName)
                        .withPsiElement(namedElement)
                        .withPresentableText(elementName)
                        .withIcon(namedElement.getIcon(0)));
            } else if (isScope(namedElement)) {
                // Enum/struct/union name: append '::' and reopen the popup to pick a member.
                resultSet.addElement(LookupElementBuilder.createWithIcon(namedElement)
                        .withInsertHandler(AsmIdentifierCompletionContributor::appendScopeAccess));
            } else {
                resultSet.addElement(LookupElementBuilder.createWithIcon(namedElement));
            }
        }
    }

    private static boolean isScope(PsiNamedElement element) {
        return element instanceof AsmEnumDef || element instanceof AsmStructDef || element instanceof AsmUnionDef;
    }
}
