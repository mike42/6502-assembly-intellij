package org.ca65.formatting;

import com.intellij.formatting.*;
import com.intellij.lang.ASTNode;
import com.intellij.psi.TokenType;
import com.intellij.psi.formatter.common.AbstractBlock;
import com.intellij.psi.tree.IElementType;
import org.ca65.psi.AsmTypes;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

public class AsmStatementMaybe extends AbstractBlock {
    private final SpacingBuilder mySpacingBuilder;
    private final Alignment myCommentAlignment;

    protected AsmStatementMaybe(@NotNull ASTNode node, @Nullable Wrap wrap, @Nullable Alignment alignment,
                                SpacingBuilder spacingBuilder, @Nullable Alignment commentAlignment) {
        super(node, wrap, alignment);
        this.mySpacingBuilder = spacingBuilder;
        this.myCommentAlignment = commentAlignment;
    }

    @Override
    protected List<Block> buildChildren() {
        List<Block> blocks = new ArrayList<>();
        ASTNode child = myNode.getFirstChildNode();
        while (child != null) {
            IElementType elementType = child.getElementType();
            if (elementType != TokenType.WHITE_SPACE) {
                if (elementType == AsmTypes.COMMENT) {
                    blocks.add(new AsmStatementMaybe(child, Wrap.createWrap(WrapType.NONE, false), myCommentAlignment, mySpacingBuilder, myCommentAlignment));
                } else {
                    blocks.add(new AsmStatementMaybe(child, Wrap.createWrap(WrapType.NONE, false), Alignment.createAlignment(), mySpacingBuilder, myCommentAlignment));
                }
            }
            child = child.getTreeNext();
        }
        return blocks;
    }

    @Override
    public Indent getIndent() {
        return Indent.getNoneIndent();
    }


    @Nullable
    @Override
    public Spacing getSpacing(@Nullable Block child1, @NotNull Block child2) {
        Spacing commentSpacing = getCommentSpacing(child1, child2);
        if (commentSpacing != null) {
            return commentSpacing;
        }
        // Go on with normal stuff
        return mySpacingBuilder.getSpacing(this, child1, child2);
    }

    private Spacing getCommentSpacing(@Nullable Block child1, @NotNull Block child2) {
        Block parent = this;
        if (!(child1 instanceof ASTBlock) || !(child2 instanceof ASTBlock)) {
            return null;
        }
        // First check if this is an end-of-line comment?
        ASTBlock blockChild1 = (ASTBlock) child1;
        ASTBlock blockChild2 = (ASTBlock) child2;
        IElementType block1Type = blockChild1.getNode().getElementType();
        if ((block1Type == AsmTypes.EOL_WS) || blockChild2.getNode().getElementType() != AsmTypes.COMMENT) {
            return null;
        }
        // Well this quite a big hack
        int indentSize = (block1Type == AsmTypes.LLABEL || block1Type == AsmTypes.MACRO || block1Type == AsmTypes.INSTRUCTION_MNEMONIC) ? 4 : 0;
        int lenSoFar = indentSize + blockChild1.getTextRange().getLength();
        int targetLen = 36;
        int spacesBeforeComment = targetLen - lenSoFar;
        while (spacesBeforeComment < 1) {
            spacesBeforeComment += 4;
        }
        return Spacing.createSpacing(spacesBeforeComment, spacesBeforeComment, 0, false, 0);
    }

    @Override
    public boolean isLeaf() {
        return myNode.getFirstChildNode() == null;
    }

}