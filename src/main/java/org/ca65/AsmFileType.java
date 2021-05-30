package org.ca65;

import com.intellij.openapi.fileTypes.LanguageFileType;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;

public class AsmFileType extends LanguageFileType {
    public static final AsmFileType INSTANCE = new AsmFileType();

    protected AsmFileType() {
        super(AsmLanguage.INSTANCE);
    }

    @NotNull
    @Override
    public String getName() {
        return "Assembly file";
    }

    @Override
    public @NotNull String getDescription() {
        return "Assembly file (6502, 65C02, 65C816)";
    }

    @NotNull
    @Override
    public String getDefaultExtension() {
        return "s";
    }

    @Override
    public Icon getIcon() {
        return AsmIcons.ASSEMBLY_FILE;
    }
}
