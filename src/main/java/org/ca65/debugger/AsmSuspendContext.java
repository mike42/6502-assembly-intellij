package org.ca65.debugger;

import com.intellij.xdebugger.frame.XExecutionStack;
import com.intellij.xdebugger.frame.XStackFrame;
import com.intellij.xdebugger.frame.XSuspendContext;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

public class AsmSuspendContext extends XSuspendContext {
    private final AsmExecutionStack myStack;

    public AsmSuspendContext(@NotNull AsmStackFrame topFrame, @NotNull List<XStackFrame> callerFrames) {
        List<XStackFrame> frames = new ArrayList<>();
        frames.add(topFrame);
        frames.addAll(callerFrames);
        myStack = new AsmExecutionStack(frames);
    }

    @Override
    public @Nullable XExecutionStack getActiveExecutionStack() {
        return myStack;
    }

    private static final class AsmExecutionStack extends XExecutionStack {
        private final List<XStackFrame> myFrames;

        AsmExecutionStack(@NotNull List<XStackFrame> frames) {
            super("6502");
            myFrames = frames;
        }

        @Override
        public @Nullable XStackFrame getTopFrame() {
            return myFrames.get(0);
        }

        @Override
        public void computeStackFrames(int firstFrameIndex, @NotNull XStackFrameContainer container) {
            container.addStackFrames(firstFrameIndex < myFrames.size()
                    ? myFrames.subList(firstFrameIndex, myFrames.size()) : List.of(), true);
        }
    }
}
