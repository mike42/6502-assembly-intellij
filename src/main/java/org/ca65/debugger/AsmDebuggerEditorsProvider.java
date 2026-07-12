package org.ca65.debugger;

import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.EditorFactory;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.project.Project;
import com.intellij.xdebugger.XExpression;
import com.intellij.xdebugger.XSourcePosition;
import com.intellij.xdebugger.evaluation.EvaluationMode;
import com.intellij.xdebugger.evaluation.XDebuggerEditorsProvider;
import org.ca65.AsmFileType;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Minimal editors provider for condition/expression fields. Conditions use
 * VICE monitor syntax (e.g. {@code X == $05}, {@code @ram:($02) == $10})
 * and are passed to the emulator verbatim, so a plain document suffices.
 */
public class AsmDebuggerEditorsProvider extends XDebuggerEditorsProvider {
    @Override
    public @NotNull FileType getFileType() {
        return AsmFileType.INSTANCE;
    }

    @Override
    public @NotNull Document createDocument(@NotNull Project project, @NotNull XExpression expression,
                                            @Nullable XSourcePosition sourcePosition,
                                            @NotNull EvaluationMode mode) {
        return EditorFactory.getInstance().createDocument(expression.getExpression());
    }
}
