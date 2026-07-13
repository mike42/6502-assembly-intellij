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
import org.ca65.Asm6502Bundle;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * A synthetic caller frame detected by {@link StackScanner}: the routine
 * that executed a {@code jsr} we have not returned from yet. Shows the
 * call site as its position and, as children, the return address and the
 * stack bytes the routine pushed after making the call.
 */
public class AsmCallerFrame extends XStackFrame {
    /** Owned stack bytes shown before truncating (the tail is boot junk on the outermost frame). */
    private static final int MAX_OWNED_BYTES = 16;

    private final AsmDebugProcess myProcess;
    private final StackScanner.CallerFrame myFrame;
    private final @Nullable String myLabel;
    private final @Nullable String myReturnLabel;
    private final @Nullable XSourcePosition myPosition;

    AsmCallerFrame(@NotNull AsmDebugProcess process, @NotNull StackScanner.CallerFrame frame,
                   @Nullable String label, @Nullable String returnLabel,
                   @Nullable XSourcePosition position) {
        myProcess = process;
        myFrame = frame;
        myLabel = label;
        myReturnLabel = returnLabel;
        myPosition = position;
    }

    @Override
    public @Nullable XSourcePosition getSourcePosition() {
        return myPosition;
    }

    @Override
    public Object getEqualityObject() {
        return "Asm6502CallerFrame:" + myFrame.returnTo();
    }

    @Override
    public void customizePresentation(@NotNull ColoredTextContainer component) {
        component.setIcon(AllIcons.Debugger.Frame);
        component.append(myLabel != null ? myLabel : String.format("$%04x", myFrame.jsrAddress()),
                SimpleTextAttributes.REGULAR_ATTRIBUTES);
        if (myPosition != null) {
            component.append("  " + myPosition.getFile().getName() + ":" + (myPosition.getLine() + 1),
                    SimpleTextAttributes.GRAY_ATTRIBUTES);
        }
    }

    @Override
    public void computeChildren(@NotNull XCompositeNode node) {
        XValueChildrenList children = new XValueChildrenList();
        String returnText = String.format("$%04x", myFrame.returnTo())
                + (myReturnLabel != null ? " (" + myReturnLabel + ")" : "");
        children.add(new PlainValue(Asm6502Bundle.message("DEBUG.frame.returns.to"), returnText));
        byte[] owned = myFrame.ownedBytes();
        int shown = Math.min(owned.length, MAX_OWNED_BYTES);
        for (int i = 0; i < shown; i++) {
            int address = myFrame.ownedFrom() + i;
            children.add(new AsmMemoryValue(String.format("$%04x", address), myProcess, address));
        }
        if (owned.length > shown) {
            children.add(new PlainValue("…",
                    Asm6502Bundle.message("DEBUG.frame.more.bytes", owned.length - shown)));
        }
        node.addChildren(children, true);
    }

    /** A fixed, read-only text value. */
    static class PlainValue extends XNamedValue {
        private final String myText;

        PlainValue(@NotNull String name, @NotNull String text) {
            super(name);
            myText = text;
        }

        @NotNull
        String getText() {
            return myText;
        }

        @Override
        public void computePresentation(@NotNull XValueNode node, @NotNull XValuePlace place) {
            node.setPresentation(AllIcons.Debugger.Value, null, myText, false);
        }
    }
}
