package org.ca65;

import com.intellij.lang.ASTNode;
import com.intellij.lang.folding.FoldingBuilderEx;
import com.intellij.lang.folding.FoldingDescriptor;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiElement;
import com.intellij.psi.util.PsiTreeUtil;
import org.ca65.psi.*;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;

public class AsmFoldingBuilder extends FoldingBuilderEx implements DumbAware {
    @Override
    public FoldingDescriptor @NotNull [] buildFoldRegions(PsiElement root, @NotNull Document document, boolean quick) {
        List<FoldingDescriptor> descriptors = new ArrayList<>();
        PsiElement file = root.getContainingFile();

        // Enum, struct, union blocks are now first-class PSI nodes — fold from keyword to end keyword.
        for (AsmEnumDef enumDef : PsiTreeUtil.findChildrenOfType(file, AsmEnumDef.class)) {
            descriptors.add(new FoldingDescriptor(enumDef.getNode(), enumDef.getTextRange()));
        }
        for (AsmStructDef structDef : PsiTreeUtil.findChildrenOfType(file, AsmStructDef.class)) {
            descriptors.add(new FoldingDescriptor(structDef.getNode(), structDef.getTextRange()));
        }
        for (AsmUnionDef unionDef : PsiTreeUtil.findChildrenOfType(file, AsmUnionDef.class)) {
            descriptors.add(new FoldingDescriptor(unionDef.getNode(), unionDef.getTextRange()));
        }

        // Remaining blocks (if/proc/macro/scope/repeat) are still plain dotexpr nodes.
        AsmDotexpr[] dotexprs = PsiTreeUtil.getChildrenOfType(file, AsmDotexpr.class);
        if (dotexprs != null) {
            List<FoldableStack> foldableBlockTypes = Arrays.asList(
                    new FoldableStack(Set.of(".if", ".ifblank", ".ifconst", ".ifdef", ".ifnblank", ".ifndef", ".ifnfref", ".ifp02", ".ifp816", ".ifpc02", ".ifpsc02", ".ifref"), Set.of(".endif")),
                    new FoldableStack(Set.of(".macro", ".mac"), Set.of(".endmacro", ".endmac")),
                    new FoldableStack(Set.of(".proc"), Set.of(".endproc")),
                    new FoldableStack(Set.of(".repeat"), Set.of(".endrep", ".endrepeat")),
                    new FoldableStack(Set.of(".scope"), Set.of(".endscope"))
            );
            for (AsmDotexpr expr : dotexprs) {
                for (FoldableStack foldableStack : foldableBlockTypes) {
                    foldableStack.apply(expr, descriptors);
                }
            }
        }
        return descriptors.toArray(new FoldingDescriptor[0]);
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

class FoldableStack {
    private final Set<String>  start;
    private final Set<String>  end;
    final private Deque<AsmDotexpr> startStack = new LinkedList<>();

    public FoldableStack(Set<String> start, Set<String> end) {
        this.start = start;
        this.end = end;
    }

    public void apply(AsmDotexpr expr, List<FoldingDescriptor> descriptors) {
        String exprText = expr.getFirstChild().getText().toLowerCase();
        if(start.contains(exprText)) {
            startStack.add(expr);
        } else if (!startStack.isEmpty() && end.contains(exprText)) {
            AsmDotexpr scopeStart = startStack.removeLast();
            descriptors.add(new FoldingDescriptor(scopeStart.getNode(),
                    new TextRange(scopeStart.getTextRange().getEndOffset(),
                            expr.getTextRange().getEndOffset())));
        }
    }
}