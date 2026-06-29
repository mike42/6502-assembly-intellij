package org.ca65.ld65;

import com.intellij.lexer.FlexLexer;
import com.intellij.psi.tree.IElementType;
import static com.intellij.psi.TokenType.*;
import static org.ca65.ld65.psi.Ld65Types.*;

%%
%ignorecase
%class Ld65Lexer
%implements FlexLexer
%unicode
%function advance
%type IElementType
%eof{  return;
%eof}

WHITE_SPACE    = [\ \t\r\n]+
COMMENT        = "#" [^\r\n]*
STRING_LITERAL = \" ([^\\\"] | \\[^])* \"
HEX_LITERAL    = "$" [0-9A-Fa-f]+
DEC_LITERAL    = [0-9]+
FORMAT_SPEC    = "%" [OS]
IDENTIFIER     = [A-Za-z_][A-Za-z0-9_]*

%%

<YYINITIAL> {
    {WHITE_SPACE}       { return WHITE_SPACE; }
    {COMMENT}           { return COMMENT; }
    {STRING_LITERAL}    { return STRING_LITERAL; }
    {HEX_LITERAL}       { return INT_LITERAL; }
    {DEC_LITERAL}       { return INT_LITERAL; }
    {FORMAT_SPEC}       { return FORMAT_SPEC; }

    // Section keywords (must precede IDENTIFIER to take priority on exact match)
    "MEMORY"            { return MEMORY_KW; }
    "SEGMENTS"          { return SEGMENTS_KW; }
    "FILES"             { return FILES_KW; }
    "FORMATS"           { return FORMATS_KW; }
    "FEATURES"          { return FEATURES_KW; }
    "SYMBOLS"           { return SYMBOLS_KW; }

    {IDENTIFIER}        { return IDENTIFIER; }

    "{"                 { return LBRACE; }
    "}"                 { return RBRACE; }
    "("                 { return LPAREN; }
    ")"                 { return RPAREN; }
    ";"                 { return SEMICOLON; }
    ","                 { return COMMA; }
    "="                 { return EQUALS; }
    ":"                 { return COLON; }
    "+"                 { return PLUS; }
    "-"                 { return MINUS; }
    "*"                 { return STAR; }
    "/"                 { return DIV; }
}

[^] { return BAD_CHARACTER; }
