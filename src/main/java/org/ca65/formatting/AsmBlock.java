package org.ca65.formatting;

import com.intellij.formatting.*;
import com.intellij.lang.ASTNode;
import com.intellij.psi.formatter.common.AbstractBlock;
import org.ca65.psi.AsmTypes;
import org.codehaus.groovy.ast.builder.AstSpecificationCompiler;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collections;
import java.util.List;

public class AsmBlock extends AbstractBlock {
    private final SpacingBuilder mySpacingBuilder;

    public AsmBlock(ASTNode child, Alignment alignment, SpacingBuilder spacingBuilder) {
        super(child, null, alignment);
        this.mySpacingBuilder = spacingBuilder;
    }

    @Override
    protected List<Block> buildChildren() {
        return Collections.emptyList();
    }

    @Override
    public Indent getIndent() {
        return Indent.getNoneIndent();
    }

    @Nullable
    @Override
    public Spacing getSpacing(@Nullable Block child1, @NotNull Block child2) {
        Spacing commentSpacing = getCommentSpacing(child1, child2);
        if(commentSpacing != null) {
            return commentSpacing;
        }
        // Go on with normal stuff
        return mySpacingBuilder.getSpacing(this, child1, child2);
    }

    private Spacing getCommentSpacing(@Nullable Block child1, @NotNull Block child2) {
        // First check if this is an end-of-line comment.
        Block parent = this;
        // Well well well.
        if (!(child1 instanceof ASTBlock) || !(child2 instanceof ASTBlock)) {
            return null;
        }

        ASTBlock blockChild1 = (ASTBlock) child1;
        ASTBlock blockChild2 = (ASTBlock) child2;
        if ((blockChild1.getNode().getElementType() != AsmTypes.LLABEL && blockChild1.getNode().getElementType() != AsmTypes.INSTRUCTION_MNEMONIC) || blockChild2.getNode().getElementType() != AsmTypes.COMMENT) {
            return null;
        }
        return Spacing.createSpacing(50, 50, 0, false, 0);
    }

    @Override
    public boolean isLeaf() {
        return myNode.getFirstChildNode() == null;
    }

}
