package org.ca65.psi;

import com.intellij.extapi.psi.PsiFileBase;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.psi.FileViewProvider;
import org.ca65.AsmFileType;
import org.ca65.AsmLanguage;
import org.jetbrains.annotations.NotNull;

public class AsmFile extends PsiFileBase {

    public AsmFile(@NotNull FileViewProvider viewProvider) {
        super(viewProvider, AsmLanguage.INSTANCE);
    }

    @NotNull
    @Override
    public FileType getFileType() {
        return AsmFileType.INSTANCE;
    }

    @Override
    public String toString() {
        return "Assembly file";
    }
}