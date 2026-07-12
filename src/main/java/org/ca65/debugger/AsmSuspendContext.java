package org.ca65.debugger;

import com.intellij.xdebugger.frame.XExecutionStack;
import com.intellij.xdebugger.frame.XStackFrame;
import com.intellij.xdebugger.frame.XSuspendContext;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

public class AsmSuspendContext extends XSuspendContext {
    private final AsmExecutionStack myStack;

    public AsmSuspendContext(@NotNull AsmStackFrame topFrame) {
        myStack = new AsmExecutionStack(topFrame);
    }

    @Override
    public @Nullable XExecutionStack getActiveExecutionStack() {
        return myStack;
    }

    private static final class AsmExecutionStack extends XExecutionStack {
        private final AsmStackFrame myTopFrame;

        AsmExecutionStack(@NotNull AsmStackFrame topFrame) {
            super("6502");
            myTopFrame = topFrame;
        }

        @Override
        public @Nullable XStackFrame getTopFrame() {
            return myTopFrame;
        }

        @Override
        public void computeStackFrames(int firstFrameIndex, @NotNull XStackFrameContainer container) {
            container.addStackFrames(firstFrameIndex == 0 ? List.of(myTopFrame) : List.of(), true);
        }
    }
}
