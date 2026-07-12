package org.ca65.debugger;

import com.intellij.icons.AllIcons;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.TextRange;
import com.intellij.xdebugger.XSourcePosition;
import com.intellij.xdebugger.evaluation.ExpressionInfo;
import com.intellij.xdebugger.evaluation.XDebuggerEvaluator;
import com.intellij.xdebugger.frame.XValue;
import com.intellij.xdebugger.frame.XValueNode;
import com.intellij.xdebugger.frame.XValuePlace;
import org.ca65.Asm6502Bundle;
import org.ca65.dbginfo.DbgInfo;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Map;
import java.util.Set;

/**
 * Evaluates watch/hover expressions against a stopped 6502: register names,
 * {@code $hex} addresses, and symbols from the .dbg symbol table (labels and
 * equates resolve to the byte at their address; larger equates display as
 * plain constants).
 */
public class AsmDebuggerEvaluator extends XDebuggerEvaluator {
    private final AsmDebugProcess myProcess;
    private final Map<String, Integer> myRegisters;

    AsmDebuggerEvaluator(@NotNull AsmDebugProcess process, @NotNull Map<String, Integer> registers) {
        myProcess = process;
        myRegisters = registers;
    }

    /** What an expression refers to; resolution is pure for testability. */
    sealed interface Target {
        record Register(@NotNull String name) implements Target {
        }

        record Memory(int address) implements Target {
        }

        record Constant(long value) implements Target {
        }
    }

    @Nullable
    static Target resolve(@NotNull String expression, @NotNull Set<String> registerNames,
                          @NotNull DbgInfo dbgInfo) {
        String trimmed = expression.trim();
        if (trimmed.isEmpty()) {
            return null;
        }
        for (String register : registerNames) {
            if (register.equalsIgnoreCase(trimmed)) {
                return new Target.Register(register);
            }
        }
        if (trimmed.startsWith("$")) {
            Integer address = parseNumber(trimmed);
            return address != null && address <= 0xffff ? new Target.Memory(address) : null;
        }
        DbgInfo.Symbol symbol = dbgInfo.getSymbol(trimmed);
        if (symbol == null) {
            return null;
        }
        if (symbol.value() >= 0 && symbol.value() <= 0xffff) {
            return new Target.Memory((int) symbol.value());
        }
        return new Target.Constant(symbol.value());
    }

    /** Parses {@code $1f}, {@code 31} or {@code %00011111}; null when malformed. */
    @Nullable
    static Integer parseNumber(@NotNull String text) {
        String trimmed = text.trim();
        try {
            if (trimmed.startsWith("$")) {
                return Integer.parseInt(trimmed.substring(1), 16);
            }
            if (trimmed.startsWith("%")) {
                return Integer.parseInt(trimmed.substring(1), 2);
            }
            return Integer.parseInt(trimmed);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    /**
     * The identifier or {@code $hex} literal covering {@code offset}, for
     * hover evaluation. Pure for testability.
     */
    @Nullable
    static TextRange expressionRangeAt(@NotNull CharSequence text, int offset) {
        if (offset < 0 || offset >= text.length() || !isIdentifierChar(text.charAt(offset))) {
            return null;
        }
        int start = offset;
        while (start > 0 && isIdentifierChar(text.charAt(start - 1))) {
            start--;
        }
        int end = offset + 1;
        while (end < text.length() && isIdentifierChar(text.charAt(end))) {
            end++;
        }
        if (start > 0 && text.charAt(start - 1) == '$') {
            start--;
        }
        return new TextRange(start, end);
    }

    private static boolean isIdentifierChar(char c) {
        return Character.isLetterOrDigit(c) || c == '_' || c == '@';
    }

    @Override
    public void evaluate(@NotNull String expression, @NotNull XEvaluationCallback callback,
                         @Nullable XSourcePosition expressionPosition) {
        Target target = resolve(expression, myRegisters.keySet(), myProcess.getDbgInfo());
        if (target == null) {
            callback.errorOccurred(Asm6502Bundle.message("DEBUG.evaluate.unknown", expression.trim()));
        } else if (target instanceof Target.Register register) {
            callback.evaluated(new AsmRegisterValue(register.name(), myProcess, myRegisters));
        } else if (target instanceof Target.Memory memory) {
            callback.evaluated(new AsmMemoryValue(expression.trim(), myProcess, memory.address()));
        } else if (target instanceof Target.Constant constant) {
            callback.evaluated(new ConstantValue(constant.value()));
        }
    }

    @Override
    public @Nullable ExpressionInfo getExpressionInfoAtOffset(@NotNull Project project,
                                                              @NotNull Document document, int offset,
                                                              boolean sideEffectsAllowed) {
        TextRange range = expressionRangeAt(document.getCharsSequence(), offset);
        return range == null ? null : new ExpressionInfo(range);
    }

    /** An equate too large to be an address: shown as-is, read-only. */
    private static final class ConstantValue extends XValue {
        private final long myValue;

        ConstantValue(long value) {
            myValue = value;
        }

        @Override
        public void computePresentation(@NotNull XValueNode node, @NotNull XValuePlace place) {
            node.setPresentation(AllIcons.Debugger.Value, null,
                    String.format("$%x (%d)", myValue, myValue), false);
        }
    }
}
