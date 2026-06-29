package org.ca65.ld65;

import com.intellij.lexer.Lexer;
import com.intellij.openapi.editor.DefaultLanguageHighlighterColors;
import com.intellij.openapi.editor.colors.TextAttributesKey;
import com.intellij.openapi.fileTypes.SyntaxHighlighterBase;
import com.intellij.psi.tree.IElementType;
import org.ca65.ld65.psi.Ld65Types;
import org.jetbrains.annotations.NotNull;

import static com.intellij.openapi.editor.colors.TextAttributesKey.createTextAttributesKey;

public class Ld65SyntaxHighlighter extends SyntaxHighlighterBase {
    public static final TextAttributesKey COMMENT =
            createTextAttributesKey("LD65_COMMENT", DefaultLanguageHighlighterColors.LINE_COMMENT);
    public static final TextAttributesKey KEYWORD =
            createTextAttributesKey("LD65_KEYWORD", DefaultLanguageHighlighterColors.KEYWORD);
    public static final TextAttributesKey NUMBER =
            createTextAttributesKey("LD65_NUMBER", DefaultLanguageHighlighterColors.NUMBER);
    public static final TextAttributesKey STRING =
            createTextAttributesKey("LD65_STRING", DefaultLanguageHighlighterColors.STRING);
    public static final TextAttributesKey FORMAT_SPEC =
            createTextAttributesKey("LD65_FORMAT_SPEC", DefaultLanguageHighlighterColors.KEYWORD);
    public static final TextAttributesKey IDENTIFIER =
            createTextAttributesKey("LD65_IDENTIFIER", DefaultLanguageHighlighterColors.IDENTIFIER);

    private static final TextAttributesKey[] COMMENT_KEYS = {COMMENT};
    private static final TextAttributesKey[] KEYWORD_KEYS = {KEYWORD};
    private static final TextAttributesKey[] NUMBER_KEYS = {NUMBER};
    private static final TextAttributesKey[] STRING_KEYS = {STRING};
    private static final TextAttributesKey[] FORMAT_SPEC_KEYS = {FORMAT_SPEC};
    private static final TextAttributesKey[] IDENTIFIER_KEYS = {IDENTIFIER};
    private static final TextAttributesKey[] EMPTY_KEYS = {};

    @Override
    public @NotNull Lexer getHighlightingLexer() {
        return new Ld65LexerAdapter();
    }

    @Override
    public TextAttributesKey @NotNull [] getTokenHighlights(IElementType tokenType) {
        if (tokenType == Ld65Types.COMMENT) return COMMENT_KEYS;
        if (tokenType == Ld65Types.INT_LITERAL) return NUMBER_KEYS;
        if (tokenType == Ld65Types.STRING_LITERAL) return STRING_KEYS;
        if (tokenType == Ld65Types.FORMAT_SPEC) return FORMAT_SPEC_KEYS;
        if (tokenType == Ld65Types.IDENTIFIER) return IDENTIFIER_KEYS;
        if (tokenType == Ld65Types.MEMORY_KW
                || tokenType == Ld65Types.SEGMENTS_KW
                || tokenType == Ld65Types.FILES_KW
                || tokenType == Ld65Types.FORMATS_KW
                || tokenType == Ld65Types.FEATURES_KW
                || tokenType == Ld65Types.SYMBOLS_KW) return KEYWORD_KEYS;
        return EMPTY_KEYS;
    }
}
