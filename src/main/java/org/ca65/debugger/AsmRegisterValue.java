package org.ca65.debugger;

import com.intellij.icons.AllIcons;
import com.intellij.xdebugger.frame.XNamedValue;
import com.intellij.xdebugger.frame.XValueModifier;
import com.intellij.xdebugger.frame.XValueNode;
import com.intellij.xdebugger.frame.XValuePlace;
import org.ca65.Asm6502Bundle;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Map;

/**
 * One CPU register in the variables view. Reads from the suspend context's
 * register snapshot; writing goes through the protocol and updates the
 * snapshot so the node re-renders without a new stop.
 */
class AsmRegisterValue extends XNamedValue {
    private static final String[] FLAG_NAMES = {"N", "V", "-", "B", "D", "I", "Z", "C"};

    private final AsmDebugProcess myProcess;
    private final Map<String, Integer> myRegisters;
    /** Short explanation shown in the type slot (for machine-specific registers). */
    private final @Nullable String myDescription;

    AsmRegisterValue(@NotNull String name, @NotNull AsmDebugProcess process,
                     @NotNull Map<String, Integer> registers) {
        this(name, process, registers, null);
    }

    AsmRegisterValue(@NotNull String name, @NotNull AsmDebugProcess process,
                     @NotNull Map<String, Integer> registers, @Nullable String description) {
        super(name);
        myProcess = process;
        myRegisters = registers;
        myDescription = description;
    }

    @Override
    public void computePresentation(@NotNull XValueNode node, @NotNull XValuePlace place) {
        int value = myRegisters.getOrDefault(getName(), 0);
        String text;
        if ("FL".equals(getName())) {
            text = String.format("$%02x %s", value, describeFlags(value));
        } else if ("PC".equals(getName()) || value > 0xff) {
            text = String.format("$%04x (%d)", value, value);
        } else {
            text = String.format("$%02x (%d)", value, value);
        }
        node.setPresentation(AllIcons.Debugger.Value, myDescription, text, false);
    }

    @Override
    public @Nullable XValueModifier getModifier() {
        return new XValueModifier() {
            @Override
            public void setValue(@NotNull String expression, @NotNull XModificationCallback callback) {
                Integer value = AsmDebuggerEvaluator.parseNumber(expression);
                if (value == null || value < 0 || value > 0xffff) {
                    callback.errorOccurred(Asm6502Bundle.message("DEBUG.value.bad.value"));
                    return;
                }
                myProcess.setRegisterValue(getName(), value, () -> {
                    myRegisters.put(getName(), value);
                    callback.valueModified();
                }, callback::errorOccurred);
            }
        };
    }

    private static String describeFlags(int flags) {
        StringBuilder text = new StringBuilder();
        for (int bit = 7; bit >= 0; bit--) {
            String name = FLAG_NAMES[7 - bit];
            text.append((flags & (1 << bit)) != 0 ? name : name.toLowerCase());
        }
        return text.toString();
    }
}
