package org.ca65.ld65;

import com.intellij.lexer.FlexAdapter;

public class Ld65LexerAdapter extends FlexAdapter {
    public Ld65LexerAdapter() {
        super(new Ld65Lexer(null));
    }
}
