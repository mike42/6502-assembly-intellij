package org.ca65;

import com.intellij.codeInsight.daemon.RelatedItemLineMarkerInfo;
import com.intellij.codeInsight.daemon.RelatedItemLineMarkerProvider;
import com.intellij.codeInsight.navigation.NavigationGutterIconBuilder;
import com.intellij.icons.AllIcons;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiReference;
import com.intellij.psi.PsiWhiteSpace;
import com.intellij.psi.impl.source.tree.LeafPsiElement;
import org.ca65.psi.AsmExpr;
import org.ca65.psi.impl.AsmExprImpl;
import org.jetbrains.annotations.NotNull;

import java.util.*;

import static org.ca65.psi.AsmTypes.*;

public class AsmLineMarkerProvider extends RelatedItemLineMarkerProvider {
    private static final Set<String> jumpMnemonics = new HashSet<>(Arrays.asList(
            "jmp",
            "jsr",
            "bcc",
            "bcs",
            "beq",
            "bmi",
            "bne",
            "bpl",
            "bvc",
            "bvs"));

    @Override
    protected void collectNavigationMarkers(@NotNull PsiElement element,
                                            @NotNull Collection<? super RelatedItemLineMarkerInfo<?>> result) {
        if (!(element instanceof LeafPsiElement)) {
            return;
        }
        LeafPsiElement leafElement = (LeafPsiElement) element;
        if (leafElement.getElementType() == IDENTIFIER) {
            collectNavigateToJumpTargetMarker(leafElement, result);
        } else if (leafElement.getElementType() == LOCAL_LABEL_REF) {
            collectNavigateToJumpTargetMarker(leafElement, result);
        }
    }

    private void collectNavigateToJumpTargetMarker(LeafPsiElement leafElement, Collection<? super RelatedItemLineMarkerInfo<?>> result) {
        // Give up if looking at anything other than a label for a jump
        if(leafElement.getOriginalElement().getParent() == null) {
            return;
        }
        PsiElement identifierElement = leafElement.getOriginalElement().getParent();
        if(identifierElement.getReference() == null || identifierElement.getParent() == null || identifierElement.getParent().getParent() == null) {
            return;
        }
        PsiElement maybeJumpArgument = identifierElement.getParent().getParent();
        if(!(maybeJumpArgument instanceof AsmExpr) || maybeJumpArgument.getPrevSibling() == null || !(maybeJumpArgument.getPrevSibling() instanceof PsiWhiteSpace) || maybeJumpArgument.getPrevSibling().getPrevSibling() == null) {
            return;
        }
        PsiElement maybeJumpInstruction = maybeJumpArgument.getPrevSibling().getPrevSibling();
        if(!(maybeJumpInstruction instanceof  LeafPsiElement) || ((LeafPsiElement) maybeJumpInstruction).getElementType() != MNEMONIC) {
            return;
        }
        if(!isJumpMnemonic(maybeJumpInstruction)) {
            return;
        }
        // Give up if we can't find where the label is defined
        PsiElement labelDefinition = identifierElement.getReference().resolve();
        if(labelDefinition == null) {
            return;
        }
        NavigationGutterIconBuilder<PsiElement> builder =
                NavigationGutterIconBuilder.create(leafElement.getText().startsWith("@") ? AsmIcons.JUMP_TO_LOCAL_LABEL : AsmIcons.JUMP_TO_LABEL)
                        .setTarget(labelDefinition)
                        .setTooltipText("Navigate to label");
        result.add(builder.createLineMarkerInfo(leafElement));
    }

    private static boolean isJumpMnemonic(PsiElement mnemonicElement) {
        // True for mnemonics which are jumps or branches
        String test = mnemonicElement.getText().toLowerCase(Locale.ROOT);
        return jumpMnemonics.contains(test);
    }
}
