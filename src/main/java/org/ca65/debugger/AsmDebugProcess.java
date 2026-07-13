package org.ca65.debugger;

import com.intellij.execution.ExecutionException;
import com.intellij.execution.configurations.GeneralCommandLine;
import com.intellij.execution.process.KillableProcessHandler;
import com.intellij.execution.process.ProcessHandler;
import com.intellij.execution.process.ProcessTerminatedListener;
import com.intellij.execution.ui.ConsoleView;
import com.intellij.execution.ui.ConsoleViewContentType;
import com.intellij.icons.AllIcons;
import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.concurrency.AppExecutorUtil;
import com.intellij.xdebugger.XDebugProcess;
import com.intellij.xdebugger.XDebugSession;
import com.intellij.xdebugger.XDebugSessionListener;
import com.intellij.xdebugger.XDebuggerUtil;
import com.intellij.xdebugger.XSourcePosition;
import com.intellij.xdebugger.breakpoints.XBreakpointHandler;
import com.intellij.xdebugger.breakpoints.XLineBreakpoint;
import com.intellij.xdebugger.evaluation.XDebuggerEditorsProvider;
import com.intellij.xdebugger.frame.XStackFrame;
import com.intellij.xdebugger.frame.XSuspendContext;
import org.ca65.Asm6502Bundle;
import org.ca65.buildcfg.Ca65BuildConfiguration;
import org.ca65.buildcfg.Ca65BuildPaths;
import org.ca65.buildcfg.Ca65TargetSystem;
import org.ca65.dbginfo.DbgInfo;
import org.ca65.debugger.backend.DebuggerBackend;
import org.ca65.debugger.backend.Mos6502Registers;
import org.ca65.debugger.decode.AddressingMode;
import org.ca65.debugger.decode.InstructionDecoder;
import org.ca65.debugger.decode.InstructionDecoders;
import org.ca65.run.Ca65RunConfiguration;
import org.ca65.run.EmulatorLaunchRequest;
import org.ca65.run.EmulatorProvider;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.net.ServerSocket;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Debug session against an emulator, through the emulator-neutral
 * {@link DebuggerBackend}. This class owns the IDE side — breakpoint and
 * stepping policy, stack/register/inlay presentation — while each backend
 * hides its protocol's quirks.
 *
 * <p>Threading: every backend call happens on a dedicated sequential
 * executor — xdebugger callbacks arrive on the EDT and backend events on
 * a reader thread, and neither may block on protocol requests.
 */
public class AsmDebugProcess extends XDebugProcess implements DebuggerBackend.Listener {
    private static final Logger LOG = Logger.getInstance(AsmDebugProcess.class);
    private static final int OPCODE_JSR = 0x20;
    private static final Pattern INLAY_IDENTIFIER = Pattern.compile("[A-Za-z_@][A-Za-z0-9_@]*");
    private static final Pattern INLAY_INDEX_REGISTER = Pattern.compile(",\\s*([xyXY])\\b");

    private final Ca65BuildConfiguration myBuildConfig;
    private final String myBasePath;
    private final DbgInfo myDbgInfo;
    /** Null when the configured CPU has no instruction decoder yet (e.g. 65816). */
    private final @Nullable InstructionDecoder myDecoder;
    private final AsmDebugInlays myInlays;
    private final int myPort;
    private final EmulatorProvider myProvider;
    private final EmulatorLaunchRequest myLaunchRequest;
    private final KillableProcessHandler myEmulatorHandler;
    private final AsmDebuggerEditorsProvider myEditorsProvider = new AsmDebuggerEditorsProvider();
    private final XBreakpointHandler<?>[] myBreakpointHandlers =
            new XBreakpointHandler<?>[]{new LineBreakpointHandler()};

    /** Serializes all protocol interaction. */
    private final ExecutorService myProtocolExecutor =
            AppExecutorUtil.createBoundedApplicationPoolExecutor("6502 debugger", 1);

    private volatile DebuggerBackend myBackend;
    private volatile boolean myConnected;
    private volatile boolean myTerminating;
    private volatile boolean mySuspended;

    // Breakpoint bookkeeping (backend breakpoint id ↔ IDE breakpoint)
    private final Map<Integer, XLineBreakpoint<AsmLineBreakpointProperties>> myIdToBreakpoint =
            new ConcurrentHashMap<>();
    private final Map<XLineBreakpoint<AsmLineBreakpointProperties>, Integer> myBreakpointToId =
            new ConcurrentHashMap<>();
    /** Breakpoints registered before the connection was up. */
    private final List<XLineBreakpoint<AsmLineBreakpointProperties>> myPendingBreakpoints = new ArrayList<>();

    /** Temporary breakpoint carrying a step-over / step-out / run-to-cursor. */
    private volatile int myTempStopId = -1;

    public AsmDebugProcess(@NotNull XDebugSession session, @NotNull Ca65RunConfiguration runConfiguration)
            throws ExecutionException {
        super(session);
        myBuildConfig = runConfiguration.getBuildSpec();
        Ca65TargetSystem system = myBuildConfig.getTargetSystemEnum();
        myProvider = EmulatorProvider.findById(runConfiguration.getEmulatorId());
        if (system == null || myProvider == null || !myProvider.supportsDebugging(system)) {
            throw new ExecutionException(Asm6502Bundle.message("RUN.error.no.emulator"));
        }
        myBasePath = runConfiguration.getProject().getBasePath();
        if (myBasePath == null) {
            throw new ExecutionException(Asm6502Bundle.message("RUN.error.no.project.path"));
        }

        File dbgFile = Ca65BuildPaths.dbgFile(myBasePath, myBuildConfig);
        if (!dbgFile.isFile()) {
            throw new ExecutionException(Asm6502Bundle.message("DEBUG.error.no.dbgfile", dbgFile.getPath()));
        }
        try (FileReader reader = new FileReader(dbgFile, StandardCharsets.UTF_8)) {
            myDbgInfo = DbgInfo.parse(reader);
        } catch (IOException e) {
            throw new ExecutionException(Asm6502Bundle.message("DEBUG.error.bad.dbgfile", dbgFile.getPath()), e);
        }

        File binary = Ca65BuildPaths.binaryFile(myBasePath, myBuildConfig);
        if (!binary.isFile()) {
            throw new ExecutionException(Asm6502Bundle.message("RUN.error.binary.missing", binary.getPath()));
        }
        myDecoder = InstructionDecoders.forCpuName(myBuildConfig.getEffectiveCpuName());
        myInlays = new AsmDebugInlays(runConfiguration.getProject());
        myPort = findFreePort();
        File labelFile = Ca65BuildPaths.labelFile(myBasePath, myBuildConfig);
        myLaunchRequest = new EmulatorLaunchRequest(system, binary.getAbsolutePath(),
                labelFile.isFile() ? labelFile.getAbsolutePath() : null,
                runConfiguration.getEmulatorExecutable(), runConfiguration.getEmulatorExtraArgs());
        GeneralCommandLine commandLine = myProvider.createDebugCommandLine(myLaunchRequest, myPort)
                .withWorkDirectory(myBasePath);
        myEmulatorHandler = new KillableProcessHandler(commandLine);
        if (!myProvider.supportsSoftKill()) {
            myEmulatorHandler.setShouldKillProcessSoftly(false);
        }
        ProcessTerminatedListener.attach(myEmulatorHandler);
    }

    private static int findFreePort() throws ExecutionException {
        try (ServerSocket socket = new ServerSocket(0)) {
            return socket.getLocalPort();
        } catch (IOException e) {
            throw new ExecutionException(Asm6502Bundle.message("DEBUG.error.no.port"), e);
        }
    }

    // ---- session lifecycle ----------------------------------------------

    @Override
    public void sessionInitialized() {
        getSession().addSessionListener(new XDebugSessionListener() {
            @Override
            public void sessionResumed() {
                myInlays.clear();
            }

            @Override
            public void sessionStopped() {
                myInlays.clear();
            }
        });
        myProtocolExecutor.execute(this::connectAndLaunch);
    }

    private void connectAndLaunch() {
        try {
            myBackend = myProvider.connectDebugger(myLaunchRequest, myPort, this, 20_000);
            printToConsole(Asm6502Bundle.message("DEBUG.console.connected",
                    myBackend.getDescription(), myPort) + "\n");
            myConnected = true;
            List<XLineBreakpoint<AsmLineBreakpointProperties>> pending;
            synchronized (myPendingBreakpoints) {
                pending = new ArrayList<>(myPendingBreakpoints);
                myPendingBreakpoints.clear();
            }
            for (XLineBreakpoint<AsmLineBreakpointProperties> breakpoint : pending) {
                armBreakpoint(breakpoint);
            }
            myBackend.startProgram();
        } catch (IOException e) {
            LOG.warn("Debug session launch failed", e);
            printToConsole(Asm6502Bundle.message("DEBUG.console.connect.failed", e.getMessage()) + "\n");
            getSession().stop();
        }
    }

    @Override
    public void stop() {
        myTerminating = true;
        DebuggerBackend backend = myBackend;
        if (backend == null || backend.isClosed()) {
            return;
        }
        myProtocolExecutor.execute(backend::terminate);
    }

    @Override
    protected @Nullable ProcessHandler doGetProcessHandler() {
        return myEmulatorHandler;
    }

    @Override
    public @NotNull XDebuggerEditorsProvider getEditorsProvider() {
        return myEditorsProvider;
    }

    @Override
    public XBreakpointHandler<?> @NotNull [] getBreakpointHandlers() {
        return myBreakpointHandlers;
    }

    // ---- execution control (called on EDT; dispatch to executor) --------

    @Override
    public void resume(@Nullable XSuspendContext context) {
        myProtocolExecutor.execute(() -> {
            try {
                mySuspended = false;
                myBackend.resume();
            } catch (IOException e) {
                reportProtocolError(e);
            }
        });
    }

    @Override
    public void startPausing() {
        myProtocolExecutor.execute(() -> {
            try {
                myBackend.pause();
            } catch (IOException e) {
                reportProtocolError(e);
            }
        });
    }

    @Override
    public void startStepInto(@Nullable XSuspendContext context) {
        myProtocolExecutor.execute(() -> {
            try {
                mySuspended = false;
                myBackend.stepInstruction();
            } catch (IOException e) {
                reportProtocolError(e);
            }
        });
    }

    @Override
    public void startStepOver(@Nullable XSuspendContext context) {
        myProtocolExecutor.execute(() -> {
            try {
                // Client-side step-over: a temporary breakpoint after the
                // JSR is exact and IRQ-proof on every backend.
                int pc = currentPc();
                int opcode = myBackend.readMemory(pc, pc)[0] & 0xff;
                mySuspended = false;
                if (opcode == OPCODE_JSR) {
                    myTempStopId = myBackend.setBreakpoint(pc + 3, true);
                    myBackend.resume();
                } else {
                    myBackend.stepInstruction();
                }
            } catch (IOException e) {
                reportProtocolError(e);
            }
        });
    }

    @Override
    public void startStepOut(@Nullable XSuspendContext context) {
        myProtocolExecutor.execute(() -> {
            try {
                mySuspended = false;
                if (myBackend.supportsStepOut()) {
                    myBackend.stepOut();
                    return;
                }
                // Fallback: run to the return address on top of the hardware
                // stack. JSR pushes the return address minus one; wrong when
                // the top of stack is not a return address (inside an
                // interrupt handler, after PHA) — same class of heuristic as
                // the caller-frame scan.
                Map<String, Integer> registers = myBackend.readRegisters();
                int sp = registers.getOrDefault(Mos6502Registers.SP, 0xff) & 0xff;
                if (sp >= 0xfe) {
                    myBackend.stepInstruction();   // empty stack: nothing to return to
                    return;
                }
                int lo = readStackByte(sp + 1);
                int hi = readStackByte(sp + 2);
                int returnTo = (((hi << 8) | lo) + 1) & 0xffff;
                myTempStopId = myBackend.setBreakpoint(returnTo, true);
                myBackend.resume();
            } catch (IOException e) {
                reportProtocolError(e);
            }
        });
    }

    private int readStackByte(int offset) throws IOException {
        int address = 0x0100 + (offset & 0xff);
        return myBackend.readMemory(address, address)[0] & 0xff;
    }

    @Override
    public void runToPosition(@NotNull XSourcePosition position, @Nullable XSuspendContext context) {
        myProtocolExecutor.execute(() -> {
            Long address = addressOf(position.getFile(), position.getLine());
            if (address == null) {
                printToConsole(Asm6502Bundle.message("DEBUG.console.no.code.at.line",
                        position.getFile().getName(), position.getLine() + 1) + "\n");
                getSession().positionReached(getSession().getSuspendContext());
                return;
            }
            try {
                if (address.intValue() == currentPc()) {
                    getSession().positionReached(getSession().getSuspendContext());
                    return;
                }
                mySuspended = false;
                myTempStopId = myBackend.setBreakpoint(address.intValue(), true);
                myBackend.resume();
            } catch (IOException e) {
                reportProtocolError(e);
            }
        });
    }

    /** Must run on the protocol executor with the machine stopped. */
    private int currentPc() throws IOException {
        return myBackend.readRegisters().getOrDefault(Mos6502Registers.PC, 0);
    }

    // ---- backend events ----------------------------------------------------

    @Override
    public void onStopped(@NotNull DebuggerBackend.StopEvent event) {
        myProtocolExecutor.execute(() -> {
            XLineBreakpoint<AsmLineBreakpointProperties> breakpoint = null;
            if (event.reason() == DebuggerBackend.StopReason.BREAKPOINT) {
                if (event.breakpointId() == myTempStopId) {
                    myTempStopId = -1;
                    try {
                        // Fired temporaries are gone already; this covers the
                        // "reached without firing" path and is tolerant.
                        myBackend.removeBreakpoint(event.breakpointId());
                    } catch (IOException e) {
                        LOG.warn("Could not remove temporary breakpoint", e);
                    }
                } else {
                    breakpoint = myIdToBreakpoint.get(event.breakpointId());
                }
            } else if (event.reason() == DebuggerBackend.StopReason.JAM) {
                printToConsole(Asm6502Bundle.message("DEBUG.console.jam",
                        String.format("$%04x", event.pc())) + "\n");
            }
            surfaceStop(breakpoint);
        });
    }

    @Override
    public void onDisconnected(@Nullable Throwable cause) {
        if (!myTerminating) {
            printToConsole(Asm6502Bundle.message("DEBUG.console.disconnected") + "\n");
            getSession().stop();
        }
    }

    // ---- stop presentation -----------------------------------------------

    private void surfaceStop(@Nullable XLineBreakpoint<AsmLineBreakpointProperties> breakpoint) {
        try {
            // A stale temporary breakpoint (e.g. a user breakpoint fired
            // while stepping over a subroutine) must not trigger later.
            int staleTemp = myTempStopId;
            if (staleTemp >= 0 && breakpoint != null) {
                myTempStopId = -1;
                myBackend.removeBreakpoint(staleTemp);
            }

            Map<String, Integer> registers = myBackend.readRegisters();
            int pc = registers.getOrDefault(Mos6502Registers.PC, 0);
            XSourcePosition position = sourcePositionOf(pc);
            InstructionPresentation instruction = decodeInstructionAt(pc, registers);
            StackScanner.ScanResult scan = scanStack(registers);
            AsmStackFrame frame = new AsmStackFrame(this, position, pc, registers,
                    myDbgInfo.getLabelForAddress(pc), instruction,
                    scan.topOwnedFrom(), scan.topOwnedBytes());
            AsmSuspendContext suspendContext = new AsmSuspendContext(frame, buildCallerFrames(scan));
            mySuspended = true;
            boolean suspended = true;
            if (breakpoint != null) {
                boolean shouldSuspend = getSession().breakpointReached(breakpoint, null, suspendContext);
                if (!shouldSuspend) {
                    mySuspended = false;
                    suspended = false;
                    myBackend.resume();
                }
            } else {
                getSession().positionReached(suspendContext);
            }
            if (suspended && position != null) {
                String inlayText = buildInlayText(position, registers, instruction);
                if (inlayText != null) {
                    myInlays.show(position, inlayText);
                }
            }
        } catch (IOException e) {
            reportProtocolError(e);
        }
    }

    /** Decodes the instruction at the stop's PC for display; null-safe throughout. */
    @Nullable
    private InstructionPresentation decodeInstructionAt(int pc, Map<String, Integer> registers) {
        if (myDecoder == null) {
            return null;
        }
        try {
            byte[] bytes = myBackend.readMemory(pc, Math.min(pc + 2, 0xffff));
            InstructionDecoder.DecodedInstruction decoded = myDecoder.decode(bytes[0] & 0xff,
                    bytes.length > 1 ? bytes[1] & 0xff : 0, bytes.length > 2 ? bytes[2] & 0xff : 0);
            if (decoded == null) {
                return null;
            }
            InstructionDecoder.MemoryReader memory =
                    address -> myBackend.readMemory(address, address)[0] & 0xff;
            Integer effective = myDecoder.effectiveAddress(decoded, pc, registers, memory);
            String annotation = myDecoder.describeOperand(decoded, pc, registers, memory);
            // a control-flow target is code — presenting its byte as a value
            // would be noise
            boolean controlFlow = myDecoder.isControlFlow(decoded);
            Integer value = effective == null || controlFlow ? null
                    : myBackend.readMemory(effective, effective)[0] & 0xff;
            boolean directDataAccess = !controlFlow
                    && (decoded.mode() == AddressingMode.ZERO_PAGE
                    || decoded.mode() == AddressingMode.ABSOLUTE);
            return new InstructionPresentation(myDecoder.format(decoded, pc),
                    decoded.mode().getDisplayName(), annotation, effective, value, directDataAccess);
        } catch (IOException e) {
            LOG.warn("Could not decode instruction at $" + Integer.toHexString(pc), e);
            return null;
        }
    }

    /** Detects caller frames on the hardware stack; empty on any failure. */
    @NotNull
    private StackScanner.ScanResult scanStack(Map<String, Integer> registers) {
        try {
            int sp = registers.getOrDefault(Mos6502Registers.SP, 0xff) & 0xff;
            if (sp >= 0xfe) {
                return StackScanner.ScanResult.EMPTY;
            }
            byte[] page = myBackend.readMemory(0x0100, 0x01ff);
            return StackScanner.scan(page, sp,
                    address -> myBackend.readMemory(address, address)[0] & 0xff,
                    address -> myDbgInfo.getPositionForAddress(address) != null, 16);
        } catch (IOException e) {
            LOG.warn("Stack scan failed", e);
            return StackScanner.ScanResult.EMPTY;
        }
    }

    @NotNull
    private List<XStackFrame> buildCallerFrames(@NotNull StackScanner.ScanResult scan) {
        List<XStackFrame> frames = new ArrayList<>();
        for (StackScanner.CallerFrame caller : scan.frames()) {
            frames.add(new AsmCallerFrame(this, caller,
                    myDbgInfo.getLabelForAddress(caller.jsrAddress()),
                    myDbgInfo.getLabelForAddress(caller.returnTo()),
                    sourcePositionOf(caller.jsrAddress())));
        }
        return frames;
    }

    /** "name=$xx" parts for symbols and index registers used on the stopped line. */
    @Nullable
    private String buildInlayText(@NotNull XSourcePosition position, @NotNull Map<String, Integer> registers,
                                  @Nullable InstructionPresentation instruction) {
        String lineText = ReadAction.compute(() -> {
            Document document = FileDocumentManager.getInstance().getDocument(position.getFile());
            if (document == null || position.getLine() >= document.getLineCount()) {
                return null;
            }
            return document.getText(new TextRange(document.getLineStartOffset(position.getLine()),
                    document.getLineEndOffset(position.getLine())));
        });
        if (lineText == null) {
            return null;
        }
        int comment = lineText.indexOf(';');
        if (comment >= 0) {
            lineText = lineText.substring(0, comment);
        }
        List<String> parts = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        boolean effectiveShownViaSymbol = false;
        Matcher identifiers = INLAY_IDENTIFIER.matcher(lineText);
        while (identifiers.find() && parts.size() < 4) {
            String name = identifiers.group();
            if (!seen.add(name)) {
                continue;
            }
            DbgInfo.Symbol symbol = myDbgInfo.getSymbol(name);
            if (symbol == null || symbol.value() < 0 || symbol.value() > 0xffff) {
                continue;
            }
            int address = (int) symbol.value();
            // Dereference only when the symbol IS the accessed variable
            // (plain zp/abs data instruction hitting exactly this address).
            // In every other role — jump/branch target, indexed base,
            // immediate — the symbol stands for its 16-bit value.
            boolean variableAccess = instruction != null && instruction.directDataAccess()
                    && instruction.effectiveAddress() != null
                    && instruction.effectiveAddress() == address;
            if (variableAccess) {
                try {
                    int value = myBackend.readMemory(address, address)[0] & 0xff;
                    parts.add(String.format("%s=$%02x", name, value));
                    effectiveShownViaSymbol = true;
                } catch (IOException e) {
                    LOG.warn("Could not read " + name, e);
                }
            } else {
                parts.add(address <= 0xff
                        ? String.format("%s=$%02x", name, address)
                        : String.format("%s=$%04x", name, address));
            }
        }
        Matcher index = INLAY_INDEX_REGISTER.matcher(lineText);
        while (index.find()) {
            String register = index.group(1).toUpperCase(Locale.ROOT);
            Integer value = registers.get(register);
            String part = value == null ? null : String.format("%s=$%02x", register, value);
            if (part != null && !parts.contains(part)) {
                parts.add(part);
            }
        }
        if (instruction != null && instruction.effectiveAddress() != null
                && instruction.effectiveValue() != null && !effectiveShownViaSymbol) {
            parts.add(String.format("→ $%04x=$%02x",
                    instruction.effectiveAddress(), instruction.effectiveValue()));
        }
        return parts.isEmpty() ? null : String.join("  ", parts);
    }

    @Nullable
    private XSourcePosition sourcePositionOf(int pc) {
        DbgInfo.SourcePosition position = myDbgInfo.getPositionForAddress(pc);
        if (position == null) {
            return null;
        }
        File file = new File(position.fileName());
        if (!file.isAbsolute()) {
            file = new File(myBasePath, position.fileName());
        }
        VirtualFile virtualFile = LocalFileSystem.getInstance().findFileByIoFile(file);
        if (virtualFile == null) {
            return null;
        }
        final VirtualFile foundFile = virtualFile;
        return ReadAction.compute(() ->
                XDebuggerUtil.getInstance().createPosition(foundFile, position.line() - 1));
    }

    // ---- breakpoints ------------------------------------------------------

    private class LineBreakpointHandler
            extends XBreakpointHandler<XLineBreakpoint<AsmLineBreakpointProperties>> {
        LineBreakpointHandler() {
            super(AsmLineBreakpointType.class);
        }

        @Override
        public void registerBreakpoint(@NotNull XLineBreakpoint<AsmLineBreakpointProperties> breakpoint) {
            if (!myConnected) {
                synchronized (myPendingBreakpoints) {
                    if (!myConnected) {
                        myPendingBreakpoints.add(breakpoint);
                        return;
                    }
                }
            }
            myProtocolExecutor.execute(() -> armBreakpoint(breakpoint));
        }

        @Override
        public void unregisterBreakpoint(@NotNull XLineBreakpoint<AsmLineBreakpointProperties> breakpoint,
                                         boolean temporary) {
            synchronized (myPendingBreakpoints) {
                myPendingBreakpoints.remove(breakpoint);
            }
            Integer id = myBreakpointToId.remove(breakpoint);
            if (id == null) {
                return;
            }
            myIdToBreakpoint.remove(id);
            myProtocolExecutor.execute(() -> {
                try {
                    myBackend.removeBreakpoint(id);
                } catch (IOException e) {
                    reportProtocolError(e);
                }
            });
        }
    }

    /** Must run on the protocol executor with an established connection. */
    private void armBreakpoint(@NotNull XLineBreakpoint<AsmLineBreakpointProperties> breakpoint) {
        XSourcePosition position = breakpoint.getSourcePosition();
        Long address = position == null ? null : addressOf(position.getFile(), position.getLine());
        if (address == null) {
            getSession().updateBreakpointPresentation(breakpoint,
                    AllIcons.Debugger.Db_invalid_breakpoint,
                    Asm6502Bundle.message("DEBUG.breakpoint.no.code"));
            return;
        }
        String condition = breakpoint.getConditionExpression() == null
                ? null : breakpoint.getConditionExpression().getExpression();
        if (condition != null && !condition.isBlank() && !myBackend.supportsBreakpointConditions()) {
            getSession().updateBreakpointPresentation(breakpoint,
                    AllIcons.Debugger.Db_invalid_breakpoint,
                    Asm6502Bundle.message("DEBUG.breakpoint.conditions.unsupported",
                            myProvider.getDisplayName()));
            return;
        }
        try {
            int id = myBackend.setBreakpoint(address.intValue(), false);
            myIdToBreakpoint.put(id, breakpoint);
            myBreakpointToId.put(breakpoint, id);
            if (condition != null && !condition.isBlank()) {
                try {
                    myBackend.setBreakpointCondition(id, condition);
                } catch (IOException e) {
                    getSession().updateBreakpointPresentation(breakpoint,
                            AllIcons.Debugger.Db_invalid_breakpoint,
                            Asm6502Bundle.message("DEBUG.breakpoint.bad.condition", condition));
                    return;
                }
            }
            getSession().updateBreakpointPresentation(breakpoint,
                    AllIcons.Debugger.Db_verified_breakpoint, null);
        } catch (IOException e) {
            reportProtocolError(e);
        }
    }

    @Nullable
    private Long addressOf(@NotNull VirtualFile file, int zeroBasedLine) {
        String path = file.getPath();
        String relative = FileUtil.getRelativePath(myBasePath, path, '/');
        Long address = relative == null ? null : myDbgInfo.getAddressForLine(relative, zeroBasedLine + 1);
        if (address == null) {
            address = myDbgInfo.getAddressForLine(path, zeroBasedLine + 1);
        }
        return address;
    }

    // ---- value access for the variables view / evaluator ------------------
    // All only meaningful while suspended: reading a running machine would
    // either pause it or return garbage, depending on the backend.

    @NotNull
    DbgInfo getDbgInfo() {
        return myDbgInfo;
    }

    /** Reads one byte of memory and reports it (or an error) asynchronously. */
    void readByte(int address, @NotNull java.util.function.IntConsumer onValue,
                  @NotNull java.util.function.Consumer<String> onError) {
        if (!mySuspended) {
            onError.accept(Asm6502Bundle.message("DEBUG.value.running"));
            return;
        }
        myProtocolExecutor.execute(() -> {
            if (!mySuspended) {
                onError.accept(Asm6502Bundle.message("DEBUG.value.running"));
                return;
            }
            try {
                onValue.accept(myBackend.readMemory(address, address)[0] & 0xff);
            } catch (IOException e) {
                onError.accept(String.valueOf(e.getMessage()));
            }
        });
    }

    /** Writes one byte of memory and reports completion asynchronously. */
    void writeByte(int address, int value, @NotNull Runnable onSuccess,
                   @NotNull java.util.function.Consumer<String> onError) {
        if (!mySuspended) {
            onError.accept(Asm6502Bundle.message("DEBUG.value.running"));
            return;
        }
        myProtocolExecutor.execute(() -> {
            try {
                myBackend.writeMemory(address, new byte[]{(byte) value});
                onSuccess.run();
            } catch (IOException e) {
                onError.accept(String.valueOf(e.getMessage()));
            }
        });
    }

    /** Sets a CPU register and reports completion asynchronously. */
    void setRegisterValue(@NotNull String name, int value, @NotNull Runnable onSuccess,
                          @NotNull java.util.function.Consumer<String> onError) {
        if (!mySuspended) {
            onError.accept(Asm6502Bundle.message("DEBUG.value.running"));
            return;
        }
        myProtocolExecutor.execute(() -> {
            try {
                myBackend.writeRegister(name, value);
                onSuccess.run();
            } catch (IOException e) {
                onError.accept(String.valueOf(e.getMessage()));
            }
        });
    }

    // ---- misc -------------------------------------------------------------

    private void reportProtocolError(@NotNull IOException e) {
        if (!myTerminating) {
            LOG.warn("Debugger protocol error", e);
            printToConsole(Asm6502Bundle.message("DEBUG.console.protocol.error", String.valueOf(e.getMessage())) + "\n");
        }
    }

    private void printToConsole(@NotNull String text) {
        if (getSession().getConsoleView() instanceof ConsoleView console) {
            console.print(text, ConsoleViewContentType.SYSTEM_OUTPUT);
        }
    }
}
