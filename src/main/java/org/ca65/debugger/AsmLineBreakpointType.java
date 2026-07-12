package org.ca65.debugger;

import com.intellij.openapi.fileTypes.FileTypeRegistry;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import org.ca65.Asm6502Bundle;
import org.ca65.AsmFileType;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import com.intellij.xdebugger.breakpoints.XLineBreakpointType;

/**
 * Line breakpoints in assembly files. Whether a line actually maps to an
 * address is only known once a session's .dbg file is loaded; lines
 * without code are marked invalid at session start.
 */
public class AsmLineBreakpointType extends XLineBreakpointType<AsmLineBreakpointProperties> {
    public static final String ID = "ca65-line";

    public AsmLineBreakpointType() {
        super(ID, Asm6502Bundle.message("DEBUG.breakpoint.title"));
    }

    @Override
    public boolean canPutAt(@NotNull VirtualFile file, int line, @NotNull Project project) {
        return FileTypeRegistry.getInstance().isFileOfType(file, AsmFileType.INSTANCE);
    }

    @Override
    public @Nullable AsmLineBreakpointProperties createBreakpointProperties(@NotNull VirtualFile file, int line) {
        return new AsmLineBreakpointProperties();
    }
}
