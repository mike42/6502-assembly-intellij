package org.ca65.action;

import com.intellij.codeInsight.TargetElementUtilBase;
import com.intellij.codeInsight.intention.impl.BaseIntentionAction;
import com.intellij.codeInspection.util.IntentionFamilyName;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.impl.source.tree.LeafPsiElement;
import com.intellij.util.IncorrectOperationException;
import org.ca65.Asm6502Bundle;
import org.ca65.psi.AsmFile;
import org.ca65.psi.AsmTypes;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Replaces the angle-bracket form of an unnamed label reference (:>, :<)
 * with the more common :+ / :- form. Cursor-based so the platform can generate a live preview.
 */
public class NormalizeShortLabelRefIntentionAction extends BaseIntentionAction {
    @Override
    public @NotNull @IntentionFamilyName String getFamilyName() {
        return Asm6502Bundle.message("INTN.NAME.normalize.shortlabel");
    }

    @Override
    public boolean isAvailable(@NotNull Project project, Editor editor, PsiFile file) {
        if (!(file instanceof AsmFile)) return false;
        PsiElement ref = getShortLabelRef(editor, file);
        if (ref == null) return false;
        String text = ref.getText();
        if (text.indexOf('<') < 0 && text.indexOf('>') < 0) return false;
        setText(Asm6502Bundle.message("INTN.normalize.shortlabel", normalize(text)));
        return true;
    }

    @Override
    public void invoke(@NotNull Project project, Editor editor, PsiFile file) throws IncorrectOperationException {
        PsiElement ref = getShortLabelRef(editor, file);
        if (!(ref instanceof LeafPsiElement leaf)) return;
        // replaceWithText swaps the leaf in place (token type is unchanged); no document reparse.
        leaf.replaceWithText(normalize(leaf.getText()));
    }

    private static @Nullable PsiElement getShortLabelRef(Editor editor, PsiFile file) {
        int offset = TargetElementUtilBase.adjustOffset(file, editor.getDocument(), editor.getCaretModel().getOffset());
        PsiElement element = file.findElementAt(offset);
        return element != null && element.getNode().getElementType() == AsmTypes.SHORTLABEL_REF ? element : null;
    }

    private static String normalize(String text) {
        return text.replace('>', '+').replace('<', '-');
    }
}
