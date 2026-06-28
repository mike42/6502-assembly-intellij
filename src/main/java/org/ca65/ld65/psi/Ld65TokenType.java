package org.ca65.ld65.psi;

import com.intellij.psi.tree.IElementType;
import org.ca65.ld65.Ld65Language;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

public class Ld65TokenType extends IElementType {
    public Ld65TokenType(@NotNull @NonNls String debugName) {
        super(debugName, Ld65Language.INSTANCE);
    }

    @Override
    public String toString() {
        return "Ld65TokenType." + super.toString();
    }
}
