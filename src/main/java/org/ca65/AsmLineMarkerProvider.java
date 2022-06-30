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
import org.ca65.psi.AsmMarker;
import org.ca65.psi.impl.AsmExprImpl;
import org.ca65.psi.impl.AsmInstructionMnemonicImpl;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.util.*;

import static org.ca65.psi.AsmTypes.*;

public class AsmLineMarkerProvider extends RelatedItemLineMarkerProvider {
    private static final Set<String> jumpMnemonics = new HashSet<>(Arrays.asList(
            "bcc",
            "bcs",
            "beq",
            "bmi",
            "bne",
            "bpl",
            "bra",
            "brl",
            "bvc",
            "bvs",
            "jmp",
            "jsl",
            "jsr"));

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
        } else if (leafElement.getElementType() == SHORTLABEL_REF) {
            collectNavigateToJumpTargetWithoutName(leafElement, result);
        }
    }

    private void collectNavigateToJumpTargetWithoutName(LeafPsiElement leafElement, Collection<? super RelatedItemLineMarkerInfo<?>> result) {
        // Give up if looking at anything other than a label for a jump
        if(leafElement.getOriginalElement().getParent() == null) {
            return;
        }
        PsiElement anythingElement = leafElement.getOriginalElement().getParent();
        if(anythingElement.getParent() == null || anythingElement.getParent().getParent() == null) {
            return;
        }
        PsiElement maybeJumpArgument = anythingElement.getParent();
        if(!(maybeJumpArgument instanceof AsmExpr) || maybeJumpArgument.getPrevSibling() == null || !(maybeJumpArgument.getPrevSibling() instanceof PsiWhiteSpace) || maybeJumpArgument.getPrevSibling().getPrevSibling() == null) {
            return;
        }
        PsiElement maybeJumpInstruction = maybeJumpArgument.getPrevSibling().getPrevSibling();
        if(!(maybeJumpInstruction instanceof AsmInstructionMnemonicImpl)) {
            return;
        }
        if(!isJumpMnemonic(maybeJumpInstruction)) {
            return;
        }
        String labelText = leafElement.getText().substring(1);
        int len = labelText.length();
        final PsiElement labelDefinition;
        if(labelText.equals("+".repeat(len))) {
            labelDefinition = walkToLabel(maybeJumpInstruction.getParent(), len, true);
        } else if (labelText.equals("-".repeat(len))) {
            labelDefinition = walkToLabel(maybeJumpInstruction.getParent(), len, false);
        } else {
            labelDefinition = null; // reference should be eg. :-- or :++
        }
        if(labelDefinition == null) {
            return; // Could not find
        }
        // Select icon
        Icon icon = AsmIcons.JUMP_TO_LABEL;
        if(labelDefinition.getText().startsWith("@")) {
            icon = AsmIcons.JUMP_TO_LOCAL_LABEL;
        } else if (labelDefinition.getText().startsWith(":")) {
            icon = AsmIcons.JUMP_TO_UNNAMED_LABEL;
        }



        NavigationGutterIconBuilder<PsiElement> builder =
                NavigationGutterIconBuilder.create(icon)
                        .setTarget(labelDefinition)
                        .setTooltipText("Navigate to label");
        result.add(builder.createLineMarkerInfo(leafElement));
    }

    private PsiElement walkToLabel(PsiElement element, int len, boolean forward) {
        while(element != null && len > 0) {
            if (element instanceof AsmMarker) {
                len--;
                if (len == 0) {
                    return element;
                }
            }
            element = forward ? element.getNextSibling() : element.getPrevSibling();
        }
        return null;
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
        if(!(maybeJumpInstruction instanceof AsmInstructionMnemonicImpl)) {
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
