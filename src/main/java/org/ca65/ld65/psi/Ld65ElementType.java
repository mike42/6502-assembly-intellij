package org.ca65.ld65.psi;

import com.intellij.psi.tree.IElementType;
import org.ca65.ld65.Ld65Language;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

public class Ld65ElementType extends IElementType {
    public Ld65ElementType(@NotNull @NonNls String debugName) {
        super(debugName, Ld65Language.INSTANCE);
    }
}
