package org.ca65;

import com.intellij.codeInsight.completion.*;
import com.intellij.codeInsight.lookup.LookupElementBuilder;
import com.intellij.util.ProcessingContext;
import org.ca65.config.AsmConfiguration;
import org.ca65.helpers.Cpu;
import org.ca65.helpers.MnemonicHelper;
import org.ca65.helpers.MnemonicInfo;
import org.ca65.psi.AsmTypes;
import org.jetbrains.annotations.NotNull;

import java.awt.*;
import java.util.Set;

import static com.intellij.patterns.PlatformPatterns.psiElement;
import static org.ca65.helpers.MnemonicHelper.allMnemnonics;

public class AsmMnemonicCompletionContributor extends CompletionContributor {
    public AsmMnemonicCompletionContributor() {
        // Offer to auto-complete mnemonics at the start of a blank line
        extend(CompletionType.BASIC, psiElement().afterLeaf(psiElement(AsmTypes.EOL_WS)), new MnemonicCompletionProvider());
    }
}

class MnemonicCompletionProvider extends CompletionProvider<CompletionParameters> {
    public void addCompletions(@NotNull CompletionParameters parameters,
                               @NotNull ProcessingContext context,
                               @NotNull CompletionResultSet resultSet) {
        Color mnemonicColor = AsmSyntaxHighlighter.MNEMONIC.getDefaultAttributes().getForegroundColor();
        Cpu projectCpu = AsmConfiguration.getInstance(parameters.getPosition().getProject()).getCpu();
        Set<String> mnemonicsToShow = MnemonicHelper.getMnemonicsForCpu(projectCpu);
        for(MnemonicInfo completionMnemonic : allMnemnonics) {
            if(!mnemonicsToShow.contains(completionMnemonic.mnemnonic)) {
                // Skip mnemonics which aren't valid for this CPU.
                continue;
            }
            resultSet.addElement(LookupElementBuilder.create(completionMnemonic.mnemnonic)
                    .withItemTextForeground(mnemonicColor)
                    .withTailText(" " + completionMnemonic.description));
        }
    }
}
