package org.ca65.ld65.psi;

import com.intellij.extapi.psi.PsiFileBase;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.psi.FileViewProvider;
import org.ca65.ld65.Ld65FileType;
import org.ca65.ld65.Ld65Language;
import org.jetbrains.annotations.NotNull;

public class Ld65File extends PsiFileBase {
    public Ld65File(@NotNull FileViewProvider viewProvider) {
        super(viewProvider, Ld65Language.INSTANCE);
    }

    @Override
    public @NotNull FileType getFileType() {
        return Ld65FileType.INSTANCE;
    }

    @Override
    public String toString() {
        return "ld65 configuration file";
    }
}
