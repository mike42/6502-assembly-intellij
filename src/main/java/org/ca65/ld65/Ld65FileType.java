package org.ca65.ld65;

import com.intellij.openapi.fileTypes.LanguageFileType;
import org.ca65.AsmIcons;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;

public class Ld65FileType extends LanguageFileType {
    public static final Ld65FileType INSTANCE = new Ld65FileType();

    protected Ld65FileType() {
        super(Ld65Language.INSTANCE);
    }

    @Override
    public @NotNull String getName() {
        return "ld65 Config";
    }

    @Override
    public @NotNull String getDescription() {
        return "ld65 linker configuration file";
    }

    @Override
    public @NotNull String getDefaultExtension() {
        return "cfg";
    }

    @Override
    public Icon getIcon() {
        return AsmIcons.LINKER;
    }
}
