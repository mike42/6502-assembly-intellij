package org.ca65.debugger;

import com.intellij.icons.AllIcons;
import com.intellij.ui.ColoredTextContainer;
import com.intellij.ui.SimpleTextAttributes;
import com.intellij.xdebugger.XSourcePosition;
import com.intellij.xdebugger.frame.XCompositeNode;
import com.intellij.xdebugger.frame.XNamedValue;
import com.intellij.xdebugger.frame.XStackFrame;
import com.intellij.xdebugger.frame.XValueChildrenList;
import com.intellij.xdebugger.frame.XValueNode;
import com.intellij.xdebugger.frame.XValuePlace;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * The single (top) frame of a stopped 6502: source position from the .dbg
 * line table, register values as children. 6502 code has no reliable call
 * frames without heuristics, so deeper frames are not synthesized yet.
 */
public class AsmStackFrame extends XStackFrame {
    /** Registers shown first, in this order; anything else follows. */
    private static final List<String> PRIMARY_REGISTERS = List.of("A", "X", "Y", "SP", "PC", "FL");
    private static final String[] FLAG_NAMES = {"N", "V", "-", "B", "D", "I", "Z", "C"};

    private final @Nullable XSourcePosition mySourcePosition;
    private final int myPc;
    private final Map<String, Integer> myRegisters;

    public AsmStackFrame(@Nullable XSourcePosition sourcePosition, int pc,
                         @NotNull Map<String, Integer> registers) {
        mySourcePosition = sourcePosition;
        myPc = pc;
        myRegisters = registers;
    }

    @Override
    public @Nullable XSourcePosition getSourcePosition() {
        return mySourcePosition;
    }

    @Override
    public void customizePresentation(@NotNull ColoredTextContainer component) {
        component.setIcon(AllIcons.Debugger.Frame);
        component.append(String.format("$%04x", myPc), SimpleTextAttributes.REGULAR_ATTRIBUTES);
        if (mySourcePosition != null) {
            component.append("  " + mySourcePosition.getFile().getName()
                            + ":" + (mySourcePosition.getLine() + 1),
                    SimpleTextAttributes.GRAY_ATTRIBUTES);
        } else {
            component.append("  (no source)", SimpleTextAttributes.GRAY_ATTRIBUTES);
        }
    }

    @Override
    public void computeChildren(@NotNull XCompositeNode node) {
        XValueChildrenList children = new XValueChildrenList();
        Map<String, Integer> remaining = new LinkedHashMap<>(myRegisters);
        for (String name : PRIMARY_REGISTERS) {
            Integer value = remaining.remove(name);
            if (value != null) {
                children.add(new RegisterValue(name, value));
            }
        }
        for (Map.Entry<String, Integer> entry : remaining.entrySet()) {
            children.add(new RegisterValue(entry.getKey(), entry.getValue()));
        }
        node.addChildren(children, true);
    }

    private static final class RegisterValue extends XNamedValue {
        private final int myValue;

        RegisterValue(@NotNull String name, int value) {
            super(name);
            myValue = value;
        }

        @Override
        public void computePresentation(@NotNull XValueNode node, @NotNull XValuePlace place) {
            String text;
            if ("FL".equals(getName())) {
                text = String.format("$%02x %s", myValue, describeFlags(myValue));
            } else if ("PC".equals(getName()) || myValue > 0xff) {
                text = String.format("$%04x (%d)", myValue, myValue);
            } else {
                text = String.format("$%02x (%d)", myValue, myValue);
            }
            node.setPresentation(AllIcons.Debugger.Value, null, text, false);
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
}
