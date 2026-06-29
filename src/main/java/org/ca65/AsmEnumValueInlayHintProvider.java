package org.ca65;

import com.intellij.codeInsight.hints.declarative.*;
import com.intellij.openapi.editor.Editor;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import kotlin.Unit;
import org.ca65.psi.AsmEnumMember;
import org.ca65.psi.impl.AsmEnumMemberMixin;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class AsmEnumValueInlayHintProvider implements InlayHintsProvider {

    @Nullable
    @Override
    public InlayHintsCollector createCollector(@NotNull PsiFile file, @NotNull Editor editor) {
        return new SharedBypassCollector() {
            @Override
            public void collectFromElement(@NotNull PsiElement element, @NotNull InlayTreeSink sink) {
                if (!(element instanceof AsmEnumMember member)) return;
                Long value = ((AsmEnumMemberMixin) member).getComputedValue();
                if (value == null) return;
                String hint = "= $" + Long.toHexString(value).toUpperCase();
                int offset = element.getTextRange().getEndOffset();
                sink.addPresentation(
                        new InlineInlayPosition(offset, false, 0),
                        null,
                        hint,
                        false,
                        builder -> {
                            builder.text(hint, null);
                            return Unit.INSTANCE;
                        }
                );
            }
        };
    }
}
