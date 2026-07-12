package org.ca65.debugger;

import com.intellij.xdebugger.breakpoints.XBreakpointProperties;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/** No per-breakpoint state yet; conditions are stored by the platform. */
public class AsmLineBreakpointProperties extends XBreakpointProperties<AsmLineBreakpointProperties> {
    @Override
    public @Nullable AsmLineBreakpointProperties getState() {
        return this;
    }

    @Override
    public void loadState(@NotNull AsmLineBreakpointProperties state) {
    }
}
