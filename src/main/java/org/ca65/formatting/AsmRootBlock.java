package org.ca65.formatting;

import com.intellij.formatting.*;
import com.intellij.lang.ASTNode;
import com.intellij.psi.TokenType;
import com.intellij.psi.codeStyle.CodeStyleSettings;
import com.intellij.psi.formatter.common.AbstractBlock;
import com.intellij.psi.tree.IElementType;
import org.ca65.psi.AsmTypes;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

public class AsmRootBlock extends AbstractBlock {
    private final CodeStyleSettings mySettings;
    private final Alignment myCommentAlignment;
    private final Alignment myMnemonicAlignment;

    AsmRootBlock(@NotNull ASTNode node, @NotNull CodeStyleSettings codeStyleSettings) {
        super(node, null, Alignment.createAlignment());
        mySettings = codeStyleSettings;
        myCommentAlignment = Alignment.createAlignment(true, Alignment.Anchor.LEFT);
        myMnemonicAlignment = Alignment.createAlignment(true, Alignment.Anchor.LEFT);
    }

    @Override
    protected List<Block> buildChildren() {
        List<Block> result = new ArrayList<>();
        ASTNode child = myNode.getFirstChildNode();
        while (child != null) {
            IElementType elementType = child.getElementType();
            if (elementType != TokenType.WHITE_SPACE) {
                if(elementType == AsmTypes.COMMENT) {
//                    result.add(new AsmBlock(child, myCommentAlignment, spacingBuilder));
//                } else if (elementType == AsmTypes.MARKER) {
//                    result.add(new AsmBlock(child, myMnemonicAlignment));
                } else {
//                    result.add(new AsmBlock(child, Alignment.createAlignment(), spacingBuilder));
                }
            }
            child = child.getTreeNext();
        }
        return result;
    }

    @Override
    public @Nullable Spacing getSpacing(@Nullable Block child1, @NotNull Block child2) {
        return null;
    }

    @Override
    public boolean isLeaf() {
        return false;
    }
}
