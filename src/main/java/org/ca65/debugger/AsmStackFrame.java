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
import com.intellij.xdebugger.frame.XValueNode;
import com.intellij.xdebugger.frame.XValuePlace;
import org.ca65.Asm6502Bundle;
import org.ca65.dbginfo.DbgInfo;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * The top frame of a stopped 6502: source position from the .dbg line
 * table; children are the decoded current instruction, the CPU registers,
 * machine-specific extras, the frame's own stack bytes, and the .dbg
 * symbols grouped by segment.
 */
public class AsmStackFrame extends XStackFrame {
    /** CPU registers shown top-level, in this order; the rest is machine state. */
    private static final List<String> PRIMARY_REGISTERS = List.of("A", "X", "Y", "SP", "PC", "FL");
    /** Segment display order: mutable data first, code/read-only last. */
    private static final List<String> SEGMENT_ORDER =
            List.of("ZEROPAGE", "BSS", "DATA", "CODE", "RODATA");
    private static final int MAX_STACK_BYTES = 16;

    private final AsmDebugProcess myProcess;
    private final @Nullable XSourcePosition mySourcePosition;
    private final int myPc;
    private final Map<String, Integer> myRegisters;
    private final @Nullable String myLabel;
    private final @Nullable InstructionPresentation myInstruction;
    private final int myOwnedFrom;
    private final byte[] myOwnedBytes;

    public AsmStackFrame(@NotNull AsmDebugProcess process, @Nullable XSourcePosition sourcePosition,
                         int pc, @NotNull Map<String, Integer> registers, @Nullable String label,
                         @Nullable InstructionPresentation instruction,
                         int ownedFrom, byte @NotNull [] ownedBytes) {
        myProcess = process;
        mySourcePosition = sourcePosition;
        myPc = pc;
        myRegisters = new LinkedHashMap<>(registers);
        myLabel = label;
        myInstruction = instruction;
        myOwnedFrom = ownedFrom;
        myOwnedBytes = ownedBytes;
    }

    @Override
    public @Nullable XSourcePosition getSourcePosition() {
        return mySourcePosition;
    }

    /**
     * Stable identity across stops so the variables tree keeps its
     * expansion state (without this the platform rebuilds it collapsed).
     */
    @Override
    public Object getEqualityObject() {
        return AsmStackFrame.class;
    }

    @Override
    public @Nullable XDebuggerEvaluator getEvaluator() {
        return new AsmDebuggerEvaluator(myProcess, myRegisters);
    }

    @Override
    public void customizePresentation(@NotNull ColoredTextContainer component) {
        component.setIcon(AllIcons.Debugger.Frame);
        if (myLabel != null) {
            component.append(myLabel + "  ", SimpleTextAttributes.REGULAR_ATTRIBUTES);
        }
        component.append(String.format("$%04x", myPc), myLabel != null
                ? SimpleTextAttributes.GRAY_ATTRIBUTES : SimpleTextAttributes.REGULAR_ATTRIBUTES);
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
        if (myInstruction != null) {
            String text = myInstruction.disassembly();
            if (myInstruction.annotation() != null) {
                text += "  " + myInstruction.annotation();
            }
            children.add(new InstructionValue(text, myInstruction.modeName()));
        }
        Map<String, Integer> remaining = new LinkedHashMap<>(myRegisters);
        for (String name : PRIMARY_REGISTERS) {
            if (remaining.remove(name) != null) {
                children.add(new AsmRegisterValue(name, myProcess, myRegisters));
            }
        }
        if (!remaining.isEmpty()) {
            children.addBottomGroup(new MachineGroup(new ArrayList<>(remaining.keySet())));
        }
        if (myOwnedBytes.length > 0) {
            children.addBottomGroup(new StackBytesGroup());
        }
        children.addBottomGroup(new SymbolsGroup());
        node.addChildren(children, true);
    }

    /** The decoded instruction at PC; the type slot names the addressing mode. */
    private static final class InstructionValue extends AsmCallerFrame.PlainValue {
        private final String myModeName;

        InstructionValue(@NotNull String text, @NotNull String modeName) {
            super(Asm6502Bundle.message("DEBUG.value.instruction"), text);
            myModeName = modeName;
        }

        @Override
        public void computePresentation(@NotNull XValueNode node, @NotNull XValuePlace place) {
            node.setPresentation(AllIcons.Debugger.Value, myModeName, getText(), false);
        }
    }

    /** Machine-specific monitor registers (C64: 6510 port, VIC-II beam). */
    private final class MachineGroup extends XValueGroup {
        private final List<String> myNames;

        MachineGroup(List<String> names) {
            super(Asm6502Bundle.message("DEBUG.group.machine"));
            myNames = names;
        }

        @Override
        public void computeChildren(@NotNull XCompositeNode node) {
            XValueChildrenList children = new XValueChildrenList();
            for (String name : myNames) {
                children.add(new AsmRegisterValue(name, myProcess, myRegisters, describe(name)));
            }
            node.addChildren(children, true);
        }

        @Nullable
        private String describe(String name) {
            return switch (name) {
                case "00" -> Asm6502Bundle.message("DEBUG.register.desc.00");
                case "01" -> Asm6502Bundle.message("DEBUG.register.desc.01");
                case "LIN" -> Asm6502Bundle.message("DEBUG.register.desc.LIN");
                case "CYC" -> Asm6502Bundle.message("DEBUG.register.desc.CYC");
                default -> null;
            };
        }
    }

    /** Bytes this frame's routine pushed on the hardware stack (live). */
    private final class StackBytesGroup extends XValueGroup {
        StackBytesGroup() {
            super(Asm6502Bundle.message("DEBUG.group.stack"));
        }

        @Override
        public void computeChildren(@NotNull XCompositeNode node) {
            XValueChildrenList children = new XValueChildrenList();
            int shown = Math.min(myOwnedBytes.length, MAX_STACK_BYTES);
            for (int i = 0; i < shown; i++) {
                int address = myOwnedFrom + i;
                children.add(new AsmMemoryValue(String.format("$%04x", address), myProcess, address));
            }
            if (myOwnedBytes.length > shown) {
                children.add(new AsmCallerFrame.PlainValue("…",
                        Asm6502Bundle.message("DEBUG.frame.more.bytes", myOwnedBytes.length - shown)));
            }
            node.addChildren(children, true);
        }
    }

    /** Addressable .dbg symbols, one subgroup per segment, mutable data first. */
    private final class SymbolsGroup extends XValueGroup {
        SymbolsGroup() {
            super(Asm6502Bundle.message("DEBUG.group.symbols"));
        }

        @Override
        public void computeChildren(@NotNull XCompositeNode node) {
            String equates = Asm6502Bundle.message("DEBUG.group.equates");
            Map<String, List<DbgInfo.Symbol>> bySegment = new TreeMap<>();
            for (DbgInfo.Symbol symbol : myProcess.getDbgInfo().getSymbols().values()) {
                if (symbol.value() < 0 || symbol.value() > 0xffff) {
                    continue;
                }
                String segment = symbol.segmentName() != null ? symbol.segmentName() : equates;
                bySegment.computeIfAbsent(segment, k -> new ArrayList<>()).add(symbol);
            }
            List<String> order = new ArrayList<>();
            if (bySegment.containsKey(equates)) {
                order.add(equates);
            }
            for (String segment : SEGMENT_ORDER) {
                if (bySegment.containsKey(segment)) {
                    order.add(segment);
                }
            }
            for (String segment : bySegment.keySet()) {   // unknown segments go last
                if (!order.contains(segment)) {
                    order.add(segment);
                }
            }
            XValueChildrenList children = new XValueChildrenList();
            for (String segment : order) {
                List<DbgInfo.Symbol> symbols = bySegment.get(segment);
                symbols.sort((a, b) -> Long.compare(a.value(), b.value()));
                children.addBottomGroup(new SegmentGroup(segment, symbols));
            }
            node.addChildren(children, true);
        }
    }

    private final class SegmentGroup extends XValueGroup {
        private final List<DbgInfo.Symbol> mySymbols;

        SegmentGroup(@NotNull String name, @NotNull List<DbgInfo.Symbol> symbols) {
            super(name);
            mySymbols = symbols;
        }

        @Override
        public void computeChildren(@NotNull XCompositeNode node) {
            XValueChildrenList children = new XValueChildrenList();
            for (DbgInfo.Symbol symbol : mySymbols) {
                children.add(new AsmMemoryValue(symbol.name(), myProcess, (int) symbol.value()));
            }
            node.addChildren(children, true);
        }
    }
}
