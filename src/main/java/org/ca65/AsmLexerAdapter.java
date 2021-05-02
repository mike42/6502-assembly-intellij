package org.ca65;

import com.intellij.lexer.FlexAdapter;

public class AsmLexerAdapter extends FlexAdapter {
    public AsmLexerAdapter() {
        super(new AsmLexer(null));
    }
}
