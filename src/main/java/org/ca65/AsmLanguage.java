package org.ca65;

import com.intellij.lang.Language;

public class AsmLanguage extends Language {
    public static final AsmLanguage INSTANCE = new AsmLanguage();

    protected AsmLanguage() {
        super("6502 Assembly");
    }
}
