package org.ca65.debugger;

import com.intellij.icons.AllIcons;
import com.intellij.xdebugger.frame.XNamedValue;
import com.intellij.xdebugger.frame.XValueModifier;
import com.intellij.xdebugger.frame.XValueNode;
import com.intellij.xdebugger.frame.XValuePlace;
import org.ca65.Asm6502Bundle;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * One byte of emulator memory in the variables/watches view, labeled with
 * its symbol or address. The byte is fetched lazily per presentation, so
 * values are always current for the stop being shown; writes go through
 * MEM_SET and re-render.
 */
class AsmMemoryValue extends XNamedValue {
    private final AsmDebugProcess myProcess;
    private final int myAddress;

    AsmMemoryValue(@NotNull String name, @NotNull AsmDebugProcess process, int address) {
        super(name);
        myProcess = process;
        myAddress = address;
    }

    @Override
    public void computePresentation(@NotNull XValueNode node, @NotNull XValuePlace place) {
        String type = String.format("@$%04x", myAddress);
        myProcess.readByte(myAddress,
                value -> node.setPresentation(AllIcons.Debugger.Value, type,
                        String.format("$%02x (%d)", value, value), false),
                error -> node.setPresentation(AllIcons.Debugger.Db_obsolete, type, error, false));
    }

    @Override
    public @Nullable XValueModifier getModifier() {
        return new XValueModifier() {
            @Override
            public void setValue(@NotNull String expression, @NotNull XModificationCallback callback) {
                Integer value = AsmDebuggerEvaluator.parseNumber(expression);
                if (value == null || value < 0 || value > 0xff) {
                    callback.errorOccurred(Asm6502Bundle.message("DEBUG.value.bad.value"));
                    return;
                }
                myProcess.writeByte(myAddress, value, callback::valueModified, callback::errorOccurred);
            }
        };
    }
}
