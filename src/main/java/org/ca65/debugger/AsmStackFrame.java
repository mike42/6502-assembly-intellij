package org.ca65.debugger;

import com.intellij.icons.AllIcons;
import com.intellij.ui.ColoredTextContainer;
import com.intellij.ui.SimpleTextAttributes;
import com.intellij.xdebugger.XSourcePosition;
import com.intellij.xdebugger.evaluation.XDebuggerEvaluator;
import com.intellij.xdebugger.frame.XCompositeNode;
import com.intellij.xdebugger.frame.XStackFrame;
import com.intellij.xdebugger.frame.XValueChildrenList;
import com.intellij.xdebugger.frame.XValueGroup;
import org.ca65.Asm6502Bundle;
import org.ca65.dbginfo.DbgInfo;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * The single (top) frame of a stopped 6502: source position from the .dbg
 * line table, registers and .dbg symbols as children. 6502 code has no
 * reliable call frames without heuristics, so deeper frames are not
 * synthesized yet.
 */
public class AsmStackFrame extends XStackFrame {
    /** Registers shown first, in this order; anything else follows. */
    private static final List<String> PRIMARY_REGISTERS = List.of("A", "X", "Y", "SP", "PC", "FL");

    private final AsmDebugProcess myProcess;
    private final @Nullable XSourcePosition mySourcePosition;
    private final int myPc;
    private final Map<String, Integer> myRegisters;

    public AsmStackFrame(@NotNull AsmDebugProcess process, @Nullable XSourcePosition sourcePosition,
                         int pc, @NotNull Map<String, Integer> registers) {
        myProcess = process;
        mySourcePosition = sourcePosition;
        myPc = pc;
        myRegisters = new LinkedHashMap<>(registers);
    }

    @Override
    public @Nullable XSourcePosition getSourcePosition() {
        return mySourcePosition;
    }

    @Override
    public @Nullable XDebuggerEvaluator getEvaluator() {
        return new AsmDebuggerEvaluator(myProcess, myRegisters);
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
            if (remaining.remove(name) != null) {
                children.add(new AsmRegisterValue(name, myProcess, myRegisters));
            }
        }
        for (String name : remaining.keySet()) {
            children.add(new AsmRegisterValue(name, myProcess, myRegisters));
        }
        children.addBottomGroup(new SymbolsGroup());
        node.addChildren(children, true);
    }

    /** All addressable symbols from the .dbg file, with live memory bytes. */
    private final class SymbolsGroup extends XValueGroup {
        SymbolsGroup() {
            super(Asm6502Bundle.message("DEBUG.group.symbols"));
        }

        @Override
        public void computeChildren(@NotNull XCompositeNode node) {
            List<DbgInfo.Symbol> symbols =
                    new ArrayList<>(myProcess.getDbgInfo().getSymbols().values());
            symbols.sort((a, b) -> a.name().compareToIgnoreCase(b.name()));
            XValueChildrenList children = new XValueChildrenList();
            for (DbgInfo.Symbol symbol : symbols) {
                if (symbol.value() >= 0 && symbol.value() <= 0xffff) {
                    children.add(new AsmMemoryValue(symbol.name(), myProcess, (int) symbol.value()));
                }
            }
            node.addChildren(children, true);
        }
    }
}
