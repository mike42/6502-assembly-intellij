package org.ca65.formatting;

import com.intellij.formatting.*;
import com.intellij.psi.codeStyle.CodeStyleSettings;
import com.intellij.psi.tree.TokenSet;
import org.ca65.AsmLanguage;
import org.ca65.psi.AsmTypes;
import org.jetbrains.annotations.NotNull;

public class AsmFormattingModelBuilder implements FormattingModelBuilder {
    private static SpacingBuilder createSpaceBuilder(CodeStyleSettings settings) {
        return new SpacingBuilder(settings, AsmLanguage.INSTANCE)
                .after(AsmTypes.COMMA)
                .spaces(1)
                .between(AsmTypes.EOL_WS, TokenSet.create(AsmTypes.LLABEL, AsmTypes.MACRO))
                .spaces(4)
                .between(AsmTypes.EOL_WS, TokenSet.create(AsmTypes.MARKER, AsmTypes.DOTEXPR, AsmTypes.IMPORTS, AsmTypes.DEFINE_CONSTANT_NUMERIC, AsmTypes.DEFINE_CONSTANT_LABEL))
                .spaces(0)
                .between(AsmTypes.EOL_WS, AsmTypes.COMMENT)
                .spacing(0, 4, 0, false, 0)
                .between(AsmTypes.INSTRUCTION_MNEMONIC, AsmTypes.EXPR)
                .spaces(1);
    }

    @Override
    public @NotNull FormattingModel createModel(@NotNull FormattingContext formattingContext) {
        final CodeStyleSettings codeStyleSettings = formattingContext.getCodeStyleSettings();
        final Alignment commentAlignment = Alignment.createAlignment(true, Alignment.Anchor.LEFT);
        return FormattingModelProvider
                .createFormattingModelForPsiFile(formattingContext.getContainingFile(),
                        new AsmStatementMaybe(formattingContext.getNode(),
                                Wrap.createWrap(WrapType.NONE, false),
                                Alignment.createAlignment(),
                                createSpaceBuilder(codeStyleSettings),
                                commentAlignment),
                        codeStyleSettings);
    }
}
