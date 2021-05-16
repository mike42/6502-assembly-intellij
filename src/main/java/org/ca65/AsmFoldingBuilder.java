package org.ca65;

import com.intellij.lang.ASTNode;
import com.intellij.lang.folding.FoldingBuilder;
import com.intellij.lang.folding.FoldingBuilderEx;
import com.intellij.lang.folding.FoldingDescriptor;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.FoldingGroup;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiElement;
import com.intellij.psi.util.PsiTreeUtil;
import org.ca65.psi.AsmDotexpr;
import org.ca65.psi.AsmMarker;
import org.ca65.psi.AsmTypes;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Deque;
import java.util.LinkedList;
import java.util.List;

public class AsmFoldingBuilder extends FoldingBuilderEx implements DumbAware {
    @Override
    public FoldingDescriptor @NotNull [] buildFoldRegions(PsiElement root, @NotNull Document document, boolean quick) {
        // Create folding regions for start/end of procs and scopes
        AsmDotexpr[] dotexprs = PsiTreeUtil.getChildrenOfType(root.getContainingFile(), AsmDotexpr.class);
        List<FoldingDescriptor> descriptors = new ArrayList<>();
        if(dotexprs != null) {
            Deque<AsmDotexpr> procStartStack = new LinkedList<>();
            Deque<AsmDotexpr> scopeStartStack = new LinkedList<>();
            for (AsmDotexpr expr : dotexprs) {
                String exprText = expr.getFirstChild().getText();
                if(exprText.equals(".proc")) {
                    procStartStack.add(expr);
                } else if(exprText.equals(".scope")) {
                    scopeStartStack.add(expr);
                } else if(!procStartStack.isEmpty() && exprText.equals(".endproc")) {
                    AsmDotexpr procStart = procStartStack.removeLast();
                    descriptors.add(new FoldingDescriptor(procStart.getNode(),
                            new TextRange(procStart.getTextRange().getEndOffset(),
                                    expr.getTextRange().getEndOffset())));
                } else if(!scopeStartStack.isEmpty() && exprText.equals(".endscope")) {
                    AsmDotexpr scopeStart = scopeStartStack.removeLast();
                    descriptors.add(new FoldingDescriptor(scopeStart.getNode(),
                            new TextRange(scopeStart.getTextRange().getEndOffset(),
                                    expr.getTextRange().getEndOffset())));
                }
            }
        }
        return descriptors.toArray(new FoldingDescriptor[descriptors.size()]);
    }

    @Override
    public @Nullable String getPlaceholderText(@NotNull ASTNode node) {
        return null;
    }

    @Override
    public boolean isCollapsedByDefault(@NotNull ASTNode node) {
        return false;
    }
}
