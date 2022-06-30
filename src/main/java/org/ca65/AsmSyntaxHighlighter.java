package org.ca65;

import com.intellij.lexer.Lexer;
import com.intellij.openapi.editor.DefaultLanguageHighlighterColors;
import com.intellij.openapi.editor.colors.TextAttributesKey;
import com.intellij.openapi.fileTypes.SyntaxHighlighterBase;
import com.intellij.psi.tree.IElementType;
import org.jetbrains.annotations.NotNull;
import org.ca65.psi.AsmTypes;

import static com.intellij.openapi.editor.colors.TextAttributesKey.createTextAttributesKey;

public class AsmSyntaxHighlighter extends SyntaxHighlighterBase {
    public static final TextAttributesKey COMMENT =
            createTextAttributesKey("ASM_COMMENT", DefaultLanguageHighlighterColors.LINE_COMMENT);
    public static final TextAttributesKey NUMBER =
            createTextAttributesKey("ASM_NUMBER", DefaultLanguageHighlighterColors.NUMBER);
    public static final TextAttributesKey MNEMONIC =
            createTextAttributesKey("ASM_MNEMONIC", DefaultLanguageHighlighterColors.INSTANCE_METHOD);
    public static final TextAttributesKey DOT_KEYWORD =
            createTextAttributesKey("ASM_KEYWORD", DefaultLanguageHighlighterColors.KEYWORD);
    public static final TextAttributesKey IDENTIFIER =
            createTextAttributesKey("ASM_IDENTIFIER", DefaultLanguageHighlighterColors.CONSTANT);
    public static final TextAttributesKey STRING_LITERAL =
            createTextAttributesKey("ASM_STRING_LITERAL", DefaultLanguageHighlighterColors.STRING);

    private static final TextAttributesKey[] COMMENT_KEYS = new TextAttributesKey[]{COMMENT};
    private static final TextAttributesKey[] NUMBER_KEYS = new TextAttributesKey[]{NUMBER};
    private static final TextAttributesKey[] STRING_LITERAL_KEYS = new TextAttributesKey[]{STRING_LITERAL};
    private static final TextAttributesKey[] MNEMONIC_KEYS = new TextAttributesKey[]{MNEMONIC};
    private static final TextAttributesKey[] DOT_KEYWORD_KEYS = new TextAttributesKey[]{DOT_KEYWORD};
    private static final TextAttributesKey[] IDENTIFIER_KEYS = new TextAttributesKey[]{IDENTIFIER};
    private static final TextAttributesKey[] EMPTY_KEYS = new TextAttributesKey[0];

    @NotNull
    @Override
    public Lexer getHighlightingLexer() {
        return new AsmLexerAdapter();
    }

    @NotNull
    @Override
    public TextAttributesKey[] getTokenHighlights(IElementType tokenType) {
        if (tokenType.equals(AsmTypes.COMMENT)) {
            return COMMENT_KEYS;
        } else if(tokenType.equals(AsmTypes.INT_LITERAL)) {
            return NUMBER_KEYS;
        } else if(tokenType.equals(AsmTypes.STRING_LITERAL)) {
            return STRING_LITERAL_KEYS;
        } else if(tokenType.equals(AsmTypes.MNEMONIC) || tokenType.equals(AsmTypes.REGISTER)) {
            return MNEMONIC_KEYS;
        } else if(tokenType.equals(AsmTypes.DOT_KEYWORD) || tokenType.equals(AsmTypes.IMPORT_KEYWORD) || tokenType.equals(AsmTypes.REGISTER_DOT_KEYWORD)) {
            return DOT_KEYWORD_KEYS;
        } else if(tokenType.equals(AsmTypes.IDENTIFIER) || tokenType.equals(AsmTypes.LABEL) || tokenType.equals(AsmTypes.LOCAL_LABEL_REF)) {
            return IDENTIFIER_KEYS;
        } else {
            return EMPTY_KEYS;
        }
    }

}