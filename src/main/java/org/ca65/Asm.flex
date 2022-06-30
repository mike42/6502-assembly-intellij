package org.ca65;

import com.intellij.lexer.FlexLexer;
import com.intellij.psi.tree.IElementType;
import static com.intellij.psi.TokenType.*;
import static org.ca65.psi.AsmTypes.*;

%%
%ignorecase
%class AsmLexer
%implements FlexLexer
%unicode
%function advance
%type IElementType
%eof{  return;
%eof}

EOL_WS           = \n | \r | \r\n
LINE_WS          = [\ \t]
WHITE_SPACE_CHAR = {EOL_WS} | {LINE_WS}
//WHITE_SPACE      = {WHITE_SPACE_CHAR}+

INT_LITERAL = ( {DEC_LITERAL}
              | {HEX_LITERAL}
              | {BIN_LITERAL} )

DEC_LITERAL = [0-9] [0-9_]*
HEX_LITERAL = "$" [a-fA-F0-9_]*
BIN_LITERAL = "%" [01_]*

STRING_LITERAL = \" ( [^\\\"] | \\[^] )* \"
CHAR_LITERAL = \' ( [^\\\'] | \\[^] ){1} \'

IDENTIFIER = [A-Za-z_]+[A-Za-z0-9_]*
GLOBAL_LABEL = [A-Za-z_]+[A-Za-z0-9_]* ":"
LOCAL_LABEL = "@" [A-Za-z_]+[A-Za-z0-9_]* ":"
LABEL = ({GLOBAL_LABEL} | {LOCAL_LABEL})

DOT=.
COMMA=,
EQUALS=\=
DOUBLE_QUOTE=\"
CRLF=\R
END_OF_LINE_COMMENT=(";")[^\r\n]*

MNEMONIC = ( ADC | ADD | ALR | ANC | AND | ANE | ARR | ASL | ASR | ASW | AXS | BBR0 | BBR1 | BBR2 | BBR3 | BBR4 | BBR5
           | BBR6 | BBR7 | BBS0 | BBS1 | BBS2 | BBS3 | BBS4 | BBS5 | BBS6 | BBS7 | BC | BCC | BCS | BEQ | BIT | BK
           | BM | BM1 | BMI | BNC | BNE | BNM1 | BNZ | BP | BPL | BR | BRA | BRK | BRL | BS | BSR | BVC | BVS | BZ
           | CLA | CLC | CLD | CLE | CLI | CLV | CLX | CLY | CMP | COP | CPA | CPR | CPX | CPY | CPZ | CSH | CSL
           | DCP | DCR | DEA | DEC | DEW | DEX | DEY | DEZ | EOM | EOR | INA | INC | INR | INW | INX | INY | INZ
           | ISC | JAM | JML | JMP | JSL | JSR | LAS | LAX | LBCC | LBCS | LBEQ | LBMI | LBNE | LBPL | LBRA | LBVC
           | LBVS | LD | LDA | LDD | LDX | LDY | LDZ | LSR | MAP | MVN | MVP | NEG | NOP | ORA | PEA | PEI | PER | PHA
           | PHB | PHD | PHK | PHP | PHW | PHX | PHY | PHZ | PLA | PLB | PLD | PLP | PLX | PLY | PLZ | POP | POPD | REP
           | RLA | RMB0 | RMB1 | RMB2 | RMB3 | RMB4 | RMB5 | RMB6 | RMB7 | ROL | ROR | ROW | RRA | RS | RTI | RTL | RTN
           | RTS | SAC | SAX | SAY | SBC | SEC | SED | SEE | SEI | SEP | SET | SHA | SHX | SHY | SIR | SLO | SMB0
           | SMB1 | SMB2 | SMB3 | SMB4 | SMB5 | SMB6 | SMB7 | SRE | ST | ST0 | ST1 | ST2 | STA | STD | STP | STX | STY
           | STZ | SUB | SWA | SXY | TAB | TAD | TAI | TAM | TAM0 | TAM1 | TAM2 | TAM3 | TAM4 | TAM5 | TAM6 | TAM7
           | TAS | TAX | TAY | TAZ | TBA | TCD | TCS | TDA | TDC | TDD | TIA | TII | TIN | TMA | TMA0 | TMA1 | TMA2
           | TMA3 | TMA4 | TMA5 | TMA6 | TMA7 | TRB | TSA | TSB | TSC | TST | TSX | TSY | TXA | TXS | TXY | TYA | TYS
           | TYX | TZA | WAI | WDM | XBA | XCE)

REGISTER = (X | Y)

DOT_KEYWORD = "\." ( A16 | A8 | ADDR | ADDRSIZE | ALIGN | AND | ASCIIZ | ASIZE | ASSERT | AUTOIMPORT | BANK | BANKBYTE
                | BANKBYTES | BITAND | BITNOT | BITOR | BITXOR | BLANK | BSS | BYT | BYTE | CASE | CHARMAP | CODE
                | CONCAT | CONDES | CONST | CONSTRUCTOR | CPU | DATA | DBG | DBYT | DEBUGINFO | DEF | DEFINE | DEFINED
                | DEFINEDMACRO | DELMAC | DELMACRO | DESTRUCTOR | DWORD | ELSE | ELSEIF | END | ENDENUM | ENDIF
                | ENDMAC | ENDMACRO | ENDPROC | ENDREP | ENDREPEAT | ENDSCOPE | ENDSTRUCT | ENDUNION | ENUM | ERROR
                | EXITMAC | EXITMACRO | EXPORT | EXPORTZP | FARADDR | FATAL | FEATURE | FILEOPT | FOPT | FORCEIMPORT
                | FORCEWORD | GLOBAL | GLOBALZP | HIBYTE | HIBYTES | HIWORD | I16 | I8 | IDENT | IF | IFBLANK | IFCONST
                | IFDEF | IFNBLANK | IFNCONST | IFNDEF | IFNREF | IFP02 | IFP4510 | IFP816 | IFPC02 | IFPDTV | IFPSC02
                | IFREF | INCBIN | INCLUDE | INTERRUPTOR | ISIZE | ISMNEM | ISMNEMONIC | LEFT
                | LINECONT | LIST | LISTBYTES | LITERAL | LOBYTE | LOBYTES | LOCAL | LOCALCHAR | LOWORD | MAC | MACPACK
                | MACRO | MATCH | MAX | MID | MIN | MOD | NOT | NULL | OR | ORG | OUT | P02 | P4510 | P816 | PAGELEN
                | PAGELENGTH | PARAMCOUNT | PC02 | PDTV | POPCHARMAP | POPCPU | POPSEG | PROC | PSC02 | PUSHCHARMAP
                | PUSHCPU | PUSHSEG | REF | REFERENCED | RELOC | REPEAT | RES | RIGHT | RODATA | SCOPE | SEGMENT | SET
                | SETCPU | SHL | SHR | SIZEOF | SMART | SPRINTF | STRAT | STRING | STRLEN | STRUCT | TAG | TCOUNT
                | TIME | UNDEF | UNDEFINE | UNION | VERSION | WARNING | WORD | XMATCH | XOR | ZEROPAGE )

IMPORT_KEYWORD = "\." (IMPORT  | IMPORTZP)

SHORTLABEL_REF = \: (\+ | \-)+
LOCAL_LABEL_REF = "@" [A-Za-z_]+[A-Za-z0-9_]*

%%

<YYINITIAL> {
    {EOL_WS}+                  {return EOL_WS;}
    {LINE_WS}+                 {return LINE_WS;}
    {END_OF_LINE_COMMENT}      {return COMMENT;}
    {LABEL}                    {return LABEL;}
    {MNEMONIC}                 {return MNEMONIC;}
    {REGISTER}                 {return REGISTER; }
    {DOT_KEYWORD}              {return DOT_KEYWORD;}
    {IMPORT_KEYWORD}           {return IMPORT_KEYWORD;}
    {IDENTIFIER}               {return IDENTIFIER;}
    {INT_LITERAL}              {return INT_LITERAL;}
    {STRING_LITERAL}           {return STRING_LITERAL;}
    {CHAR_LITERAL}             {return STRING_LITERAL;}
    {EQUALS}                   {return EQUALS; }
    {COMMA}                    {return COMMA; }
    ":="                       { return COLON_EQUALS; }
    "("                        { return LPAREN; }
    ")"                        { return RPAREN; }
    "["                        { return LSQUAREBRACKET; }
    "]"                        { return RSQUAREBRACKET; }
    "||"                       { return BOOLOR; }
    "|"                        { return OR; }
    "<<"                       { return LSHIFT; }
    ">>"                       { return RSHIFT; }
    "&&"                       { return BOOLAND; }
    "&"                        { return AND; }
    "#"                        { return CONSTEXPR; }
    "::"                       { return SCOPE_ACCESS; }
    {SHORTLABEL_REF}           { return SHORTLABEL_REF; }
    ":"                        { return SHORTLABEL; }
    ">"                        { return HIBYTE; }
    "<"                        { return LOBYTE; }
    "/"                        { return DIV; }
    "*"                        { return MUL; }
    "+"                        { return ADD; }
    "~"                        { return NOT; }
    "-"                        { return SUB; }
    "^"                        { return XOR; }
    {LOCAL_LABEL_REF}          { return LOCAL_LABEL_REF; }
}

[^]                            { return BAD_CHARACTER; }
