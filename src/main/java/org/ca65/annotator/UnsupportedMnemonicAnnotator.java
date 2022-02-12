package org.ca65.annotator;

import com.intellij.codeInspection.ProblemHighlightType;
import com.intellij.lang.annotation.AnnotationBuilder;
import com.intellij.lang.annotation.AnnotationHolder;
import com.intellij.lang.annotation.Annotator;
import com.intellij.lang.annotation.HighlightSeverity;
import com.intellij.psi.PsiElement;
import org.ca65.action.ChangeProjectCpuIntentionAction;
import org.ca65.config.AsmConfiguration;
import org.ca65.helpers.Cpu;
import org.ca65.helpers.MnemonicHelper;
import org.ca65.psi.AsmInstructionMnemonic;
import org.jetbrains.annotations.NotNull;

public class UnsupportedMnemonicAnnotator implements Annotator {
    @Override
    public void annotate(@NotNull final PsiElement element, @NotNull AnnotationHolder holder) {
        if (!(element instanceof AsmInstructionMnemonic)) {
            return;
        }
        String thisMnemonic = element.getText().toLowerCase();
        Cpu projectCpu = AsmConfiguration.getInstance(element.getProject()).getCpu();
        if (MnemonicHelper.getMnemonicsForCpu(projectCpu).contains(thisMnemonic)) {
            return; // All is good
        }
        AnnotationBuilder builder = holder.newAnnotation(HighlightSeverity.ERROR, "The '" + thisMnemonic + "' instruction is not available on the " + projectCpu.name + " CPU")
                .range(element.getTextRange())
                .highlightType(ProblemHighlightType.LIKE_UNKNOWN_SYMBOL);
        // Suggest alternative CPU setting if we have another CPU config which contains this
        if (MnemonicHelper.getMnemonicsForCpu(Cpu.CPU_65C02).contains(thisMnemonic)) {
            builder = builder.withFix(new ChangeProjectCpuIntentionAction(Cpu.CPU_65C02));
        } else if(MnemonicHelper.getMnemonicsForCpu(Cpu.CPU_65C816).contains(thisMnemonic)) {
            // Suggest 65C816 only if instruction is not available on 65C02!
            builder = builder.withFix(new ChangeProjectCpuIntentionAction(Cpu.CPU_65C816));
        }
        builder.create();
    }
}
