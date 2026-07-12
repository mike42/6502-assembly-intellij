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
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.concurrency.AppExecutorUtil;
import com.intellij.xdebugger.XDebugProcess;
import com.intellij.xdebugger.XDebugSession;
import com.intellij.xdebugger.XDebuggerUtil;
import com.intellij.xdebugger.XSourcePosition;
import com.intellij.xdebugger.breakpoints.XBreakpointHandler;
import com.intellij.xdebugger.breakpoints.XLineBreakpoint;
import com.intellij.xdebugger.evaluation.XDebuggerEditorsProvider;
import com.intellij.xdebugger.frame.XSuspendContext;
import org.ca65.Asm6502Bundle;
import org.ca65.buildcfg.Ca65BuildConfiguration;
import org.ca65.buildcfg.Ca65BuildPaths;
import org.ca65.buildcfg.Ca65TargetSystem;
import org.ca65.dbginfo.DbgInfo;
import org.ca65.debugger.vice.ViceBinaryMonitorClient;
import org.ca65.debugger.vice.ViceBinaryMonitorClient.CheckpointInfo;
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
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Debug session against an emulator's VICE binary monitor.
 *
 * <p>Threading: every protocol call happens on a dedicated sequential
 * executor — xdebugger callbacks arrive on the EDT and monitor events on
 * the client's reader thread, and neither may block on protocol requests
 * (the reader thread completes them).
 *
 * <p>Stop filtering (see debugger-research/FINDINGS.md): VICE emits
 * STOPPED/RESUMED chatter during autostart, reset and around every
 * mid-run command. A stop is surfaced to the IDE only when it is
 * (a) preceded by a checkpoint-hit event, (b) the completion of a step
 * request, or (c) an explicit user pause; everything else is transient
 * and ignored.
 */
public class AsmDebugProcess extends XDebugProcess implements ViceBinaryMonitorClient.EventListener {
    private static final Logger LOG = Logger.getInstance(AsmDebugProcess.class);
    private static final int OPCODE_JSR = 0x20;

    private final Ca65BuildConfiguration myBuildConfig;
    private final String myBasePath;
    private final DbgInfo myDbgInfo;
    private final int myPort;
    private final KillableProcessHandler myEmulatorHandler;
    private final AsmDebuggerEditorsProvider myEditorsProvider = new AsmDebuggerEditorsProvider();
    private final XBreakpointHandler<?>[] myBreakpointHandlers =
            new XBreakpointHandler<?>[]{new LineBreakpointHandler()};

    /** Serializes all protocol interaction. */
    private final ExecutorService myProtocolExecutor =
            AppExecutorUtil.createBoundedApplicationPoolExecutor("6502 debugger", 1);

    private volatile ViceBinaryMonitorClient myClient;
    private volatile boolean myConnected;
    private volatile boolean myTerminating;

    // Breakpoint bookkeeping (checkpoint number ↔ IDE breakpoint)
    private final Map<Integer, XLineBreakpoint<AsmLineBreakpointProperties>> myCheckpointToBreakpoint =
            new ConcurrentHashMap<>();
    private final Map<XLineBreakpoint<AsmLineBreakpointProperties>, Integer> myBreakpointToCheckpoint =
            new ConcurrentHashMap<>();
    /** Breakpoints registered before the connection was up. */
    private final List<XLineBreakpoint<AsmLineBreakpointProperties>> myPendingBreakpoints = new ArrayList<>();

    // Stop-filtering state
    private final AtomicInteger myLastHitCheckpoint = new AtomicInteger(-1);
    private volatile boolean myStepPending;
    private volatile int myStepCheckpoint = -1;
    private volatile boolean myPauseRequested;
    private volatile boolean mySuspended;

    public AsmDebugProcess(@NotNull XDebugSession session, @NotNull Ca65RunConfiguration runConfiguration)
            throws ExecutionException {
        super(session);
        myBuildConfig = runConfiguration.findBuildConfiguration();
        if (myBuildConfig == null) {
            throw new ExecutionException(Asm6502Bundle.message("RUN.error.no.build.config"));
        }
        Ca65TargetSystem system = myBuildConfig.getTargetSystemEnum();
        EmulatorProvider provider = EmulatorProvider.findById(runConfiguration.getEmulatorId());
        if (system == null || provider == null || !provider.supportsDebugging(system)) {
            throw new ExecutionException(Asm6502Bundle.message("RUN.error.no.emulator"));
        }
        myBasePath = runConfiguration.getProject().getBasePath();
        if (myBasePath == null) {
            throw new ExecutionException(Asm6502Bundle.message("RUN.error.no.build.config"));
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
        myPort = findFreePort();
        File labelFile = Ca65BuildPaths.labelFile(myBasePath, myBuildConfig);
        EmulatorLaunchRequest request = new EmulatorLaunchRequest(system, binary.getAbsolutePath(),
                labelFile.isFile() ? labelFile.getAbsolutePath() : null,
                runConfiguration.getEmulatorExecutable(), runConfiguration.getEmulatorExtraArgs());
        GeneralCommandLine commandLine = provider.createDebugCommandLine(request, myPort)
                .withWorkDirectory(myBasePath);
        myEmulatorHandler = new KillableProcessHandler(commandLine);
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
        myProtocolExecutor.execute(this::connectAndLaunch);
    }

    private void connectAndLaunch() {
        try {
            myClient = ViceBinaryMonitorClient.connect("127.0.0.1", myPort, this, 20_000);
            String version = myClient.getViceVersion();
            printToConsole(Asm6502Bundle.message("DEBUG.console.connected", version, myPort) + "\n");
            myClient.fetchAvailableRegisters();
            myConnected = true;
            List<XLineBreakpoint<AsmLineBreakpointProperties>> pending;
            synchronized (myPendingBreakpoints) {
                pending = new ArrayList<>(myPendingBreakpoints);
                myPendingBreakpoints.clear();
            }
            for (XLineBreakpoint<AsmLineBreakpointProperties> breakpoint : pending) {
                armBreakpoint(breakpoint);
            }
            File binary = Ca65BuildPaths.binaryFile(myBasePath, myBuildConfig);
            myClient.autostart(binary.getAbsolutePath(), true);
            myClient.exitMonitor();
        } catch (IOException e) {
            LOG.warn("Debug session launch failed", e);
            printToConsole(Asm6502Bundle.message("DEBUG.console.connect.failed", e.getMessage()) + "\n");
            getSession().stop();
        }
    }

    @Override
    public void stop() {
        myTerminating = true;
        ViceBinaryMonitorClient client = myClient;
        if (client == null || client.isClosed()) {
            return;
        }
        // Mandatory hygiene: a checkpoint firing with no client attached
        // wedges VICE in its own monitor. Delete everything, then quit.
        myProtocolExecutor.execute(() -> {
            try {
                for (Integer number : myCheckpointToBreakpoint.keySet()) {
                    client.deleteCheckpoint(number);
                }
                if (myStepCheckpoint >= 0) {
                    client.deleteCheckpoint(myStepCheckpoint);
                }
                client.quit();
            } catch (IOException ignored) {
                // the process handler will kill the emulator regardless
            } finally {
                client.close();
            }
        });
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
            mySuspended = false;
            try {
                myClient.exitMonitor();
            } catch (IOException e) {
                reportProtocolError(e);
            }
        });
    }

    @Override
    public void startPausing() {
        myProtocolExecutor.execute(() -> {
            myPauseRequested = true;
            try {
                // There is no pause command: any command traps the emulator
                // into the monitor and it stays stopped until EXIT.
                myClient.getRegisters();
            } catch (IOException e) {
                reportProtocolError(e);
            }
        });
    }

    @Override
    public void startStepInto(@Nullable XSuspendContext context) {
        myProtocolExecutor.execute(() -> {
            mySuspended = false;
            myStepPending = true;
            try {
                myClient.advanceInstructions(false, 1);
            } catch (IOException e) {
                reportProtocolError(e);
            }
        });
    }

    @Override
    public void startStepOver(@Nullable XSuspendContext context) {
        myProtocolExecutor.execute(() -> {
            mySuspended = false;
            try {
                // Client-side step-over: VICE's own SO=1 tracks JSR/RTS
                // nesting and misbehaves near autostart traps. A temporary
                // checkpoint after the JSR is exact and IRQ-proof.
                int pc = myClient.getRegisters().getOrDefault("PC", 0);
                int opcode = myClient.readMemory(pc, pc)[0] & 0xff;
                if (opcode == OPCODE_JSR) {
                    CheckpointInfo checkpoint =
                            myClient.setCheckpoint(pc + 3, pc + 3, ViceBinaryMonitorClient.OP_EXEC, true);
                    myStepCheckpoint = checkpoint.number();
                    myClient.exitMonitor();
                } else {
                    myStepPending = true;
                    myClient.advanceInstructions(false, 1);
                }
            } catch (IOException e) {
                reportProtocolError(e);
            }
        });
    }

    @Override
    public void startStepOut(@Nullable XSuspendContext context) {
        myProtocolExecutor.execute(() -> {
            mySuspended = false;
            myStepPending = true;
            try {
                myClient.executeUntilReturn();
            } catch (IOException e) {
                reportProtocolError(e);
            }
        });
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
            mySuspended = false;
            try {
                CheckpointInfo checkpoint = myClient.setCheckpoint(address.intValue(), address.intValue(),
                        ViceBinaryMonitorClient.OP_EXEC, true);
                myStepCheckpoint = checkpoint.number();
                myClient.exitMonitor();
            } catch (IOException e) {
                reportProtocolError(e);
            }
        });
    }

    // ---- monitor events (called on the client reader thread) ------------

    @Override
    public void onCheckpoint(@NotNull CheckpointInfo info) {
        if (info.hit()) {
            myLastHitCheckpoint.set(info.number());
        }
    }

    @Override
    public void onStopped(int pc) {
        int hitCheckpoint = myLastHitCheckpoint.getAndSet(-1);
        boolean stepDone = myStepPending;
        boolean pauseDone = myPauseRequested;

        XLineBreakpoint<AsmLineBreakpointProperties> breakpoint = null;
        boolean surface;
        if (hitCheckpoint >= 0 && hitCheckpoint == myStepCheckpoint) {
            myStepCheckpoint = -1;                  // step-over / run-to-cursor landed
            surface = true;
        } else if (hitCheckpoint >= 0 && myCheckpointToBreakpoint.containsKey(hitCheckpoint)) {
            breakpoint = myCheckpointToBreakpoint.get(hitCheckpoint);
            surface = true;
        } else if (stepDone) {
            myStepPending = false;
            surface = true;
        } else if (pauseDone) {
            myPauseRequested = false;
            surface = true;
        } else {
            surface = false;                        // transient stop; VICE resumes it itself
        }
        if (surface) {
            final XLineBreakpoint<AsmLineBreakpointProperties> hitBreakpoint = breakpoint;
            myProtocolExecutor.execute(() -> surfaceStop(hitBreakpoint));
        }
    }

    @Override
    public void onJam(int pc) {
        printToConsole(Asm6502Bundle.message("DEBUG.console.jam", String.format("$%04x", pc)) + "\n");
        myProtocolExecutor.execute(() -> surfaceStop(null));
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
            // A stale step checkpoint (e.g. a breakpoint fired while
            // stepping over a subroutine) must not trigger later.
            int staleStep = myStepCheckpoint;
            if (staleStep >= 0 && breakpoint != null) {
                myStepCheckpoint = -1;
                myClient.deleteCheckpoint(staleStep);
            }
            myStepPending = false;
            myPauseRequested = false;

            Map<String, Integer> registers = myClient.getRegisters();
            int pc = registers.getOrDefault("PC", 0);
            XSourcePosition position = sourcePositionOf(pc);
            AsmStackFrame frame = new AsmStackFrame(position, pc, registers);
            AsmSuspendContext suspendContext = new AsmSuspendContext(frame);
            mySuspended = true;
            if (breakpoint != null) {
                boolean shouldSuspend = getSession().breakpointReached(breakpoint, null, suspendContext);
                if (!shouldSuspend) {
                    mySuspended = false;
                    myClient.exitMonitor();
                }
            } else {
                getSession().positionReached(suspendContext);
            }
        } catch (IOException e) {
            reportProtocolError(e);
        }
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
            myProtocolExecutor.execute(() -> {
                armBreakpoint(breakpoint);
                resumeIfNotSuspended();
            });
        }

        @Override
        public void unregisterBreakpoint(@NotNull XLineBreakpoint<AsmLineBreakpointProperties> breakpoint,
                                         boolean temporary) {
            synchronized (myPendingBreakpoints) {
                myPendingBreakpoints.remove(breakpoint);
            }
            Integer number = myBreakpointToCheckpoint.remove(breakpoint);
            if (number == null) {
                return;
            }
            myCheckpointToBreakpoint.remove(number);
            myProtocolExecutor.execute(() -> {
                try {
                    myClient.deleteCheckpoint(number);
                } catch (IOException e) {
                    reportProtocolError(e);
                }
                resumeIfNotSuspended();
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
        try {
            CheckpointInfo checkpoint = myClient.setCheckpoint(address.intValue(), address.intValue(),
                    ViceBinaryMonitorClient.OP_EXEC, false);
            myCheckpointToBreakpoint.put(checkpoint.number(), breakpoint);
            myBreakpointToCheckpoint.put(breakpoint, checkpoint.number());
            String condition = breakpoint.getConditionExpression() == null
                    ? null : breakpoint.getConditionExpression().getExpression();
            if (condition != null && !condition.isBlank()) {
                try {
                    myClient.setCondition(checkpoint.number(), condition);
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

    /** After a mid-run command, EXIT keeps the machine running (gotcha 4). */
    private void resumeIfNotSuspended() {
        if (!mySuspended && myConnected) {
            try {
                myClient.exitMonitor();
            } catch (IOException e) {
                reportProtocolError(e);
            }
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

    // ---- misc -------------------------------------------------------------

    private void reportProtocolError(@NotNull IOException e) {
        if (!myTerminating) {
            LOG.warn("VICE protocol error", e);
            printToConsole(Asm6502Bundle.message("DEBUG.console.protocol.error", String.valueOf(e.getMessage())) + "\n");
        }
    }

    private void printToConsole(@NotNull String text) {
        if (getSession().getConsoleView() instanceof ConsoleView console) {
            console.print(text, ConsoleViewContentType.SYSTEM_OUTPUT);
        }
    }
}
