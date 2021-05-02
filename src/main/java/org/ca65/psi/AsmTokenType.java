package org.ca65.psi;

import com.intellij.psi.tree.IElementType;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.ca65.AsmLanguage;

public class AsmTokenType extends IElementType {

    public AsmTokenType(@NotNull @NonNls String debugName) {
        super(debugName, AsmLanguage.INSTANCE);
    }

    @Override
    public String toString() {
        return "AsmTokenType." + super.toString();
    }

}