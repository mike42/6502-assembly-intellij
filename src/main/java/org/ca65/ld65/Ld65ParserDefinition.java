package org.ca65.ld65;

import com.intellij.lang.ASTNode;
import com.intellij.lang.ParserDefinition;
import com.intellij.lang.PsiParser;
import com.intellij.lexer.Lexer;
import com.intellij.openapi.project.Project;
import com.intellij.psi.FileViewProvider;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.TokenType;
import com.intellij.psi.tree.IFileElementType;
import com.intellij.psi.tree.TokenSet;
import org.ca65.ld65.psi.Ld65File;
import org.ca65.ld65.psi.Ld65Types;
import org.jetbrains.annotations.NotNull;

public class Ld65ParserDefinition implements ParserDefinition {
    public static final IFileElementType FILE = new IFileElementType(Ld65Language.INSTANCE);

    public static final TokenSet WHITE_SPACES = TokenSet.create(TokenType.WHITE_SPACE);
    public static final TokenSet COMMENTS = TokenSet.create(Ld65Types.COMMENT);
    public static final TokenSet STRINGS = TokenSet.create(Ld65Types.STRING_LITERAL);

    @Override
    public @NotNull Lexer createLexer(Project project) {
        return new Ld65LexerAdapter();
    }

    @Override
    public @NotNull TokenSet getWhitespaceTokens() {
        return WHITE_SPACES;
    }

    @Override
    public @NotNull TokenSet getCommentTokens() {
        return COMMENTS;
    }

    @Override
    public @NotNull TokenSet getStringLiteralElements() {
        return STRINGS;
    }

    @Override
    public @NotNull PsiParser createParser(Project project) {
        return new Ld65Parser();
    }

    @Override
    public @NotNull IFileElementType getFileNodeType() {
        return FILE;
    }

    @Override
    public @NotNull PsiFile createFile(@NotNull FileViewProvider viewProvider) {
        return new Ld65File(viewProvider);
    }

    @Override
    public @NotNull SpaceRequirements spaceExistenceTypeBetweenTokens(ASTNode left, ASTNode right) {
        return SpaceRequirements.MAY;
    }

    @Override
    public @NotNull PsiElement createElement(ASTNode node) {
        return Ld65Types.Factory.createElement(node);
    }
}
