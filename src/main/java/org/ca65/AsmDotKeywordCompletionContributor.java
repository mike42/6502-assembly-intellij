package org.ca65;

import com.intellij.codeInsight.completion.CompletionContributor;
import com.intellij.codeInsight.completion.CompletionParameters;
import com.intellij.codeInsight.completion.CompletionProvider;
import com.intellij.codeInsight.completion.CompletionResultSet;
import com.intellij.codeInsight.completion.CompletionType;
import com.intellij.codeInsight.lookup.LookupElementBuilder;
import com.intellij.openapi.editor.Document;
import com.intellij.util.ProcessingContext;
import org.ca65.helpers.AsmDotKeywordInfo;
import org.ca65.helpers.AsmDotKeywords;
import org.jetbrains.annotations.NotNull;

import static com.intellij.patterns.PlatformPatterns.psiElement;

/**
 * Completes control commands after a {@code .} (e.g. {@code .pr<caret>} → {@code .proc}).
 */
public class AsmDotKeywordCompletionContributor extends CompletionContributor {
    public AsmDotKeywordCompletionContributor() {
        extend(CompletionType.BASIC, psiElement(), new DotKeywordCompletionProvider());
    }
}

class DotKeywordCompletionProvider extends CompletionProvider<CompletionParameters> {
    @Override
    public void addCompletions(@NotNull CompletionParameters parameters,
                               @NotNull ProcessingContext context,
                               @NotNull CompletionResultSet resultSet) {
        Document document = parameters.getEditor().getDocument();
        CharSequence text = document.getImmutableCharSequence();
        int caret = parameters.getOffset();

        // Walk back over the partial dotKeyword name to the dot that introduces it.
        int start = caret;
        while (start > 0 && isIdentifierChar(text.charAt(start - 1))) {
            start--;
        }
        if (start == 0 || text.charAt(start - 1) != '.') {
            return;
        }
        // The dot must begin a fresh token (not e.g. trail an identifier).
        int dotPos = start - 1;
        if (dotPos > 0 && isIdentifierChar(text.charAt(dotPos - 1))) {
            return;
        }

        String prefix = text.subSequence(dotPos, caret).toString(); // includes the leading dot
        CompletionResultSet prefixed = resultSet.withPrefixMatcher(prefix);
        for (AsmDotKeywordInfo dotKeyword : AsmDotKeywords.allKeywords) {
            prefixed.addElement(LookupElementBuilder.create("." + dotKeyword.name)
                    .withIcon(AsmIcons.ASSEMBLY_FILE));
        }
        prefixed.stopHere();
    }

    private static boolean isIdentifierChar(char c) {
        return Character.isLetterOrDigit(c) || c == '_';
    }
}
