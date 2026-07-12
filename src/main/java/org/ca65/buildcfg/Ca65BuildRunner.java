package org.ca65.buildcfg;

import com.intellij.build.BuildViewManager;
import com.intellij.build.DefaultBuildDescriptor;
import com.intellij.build.FilePosition;
import com.intellij.build.events.MessageEvent;
import com.intellij.build.events.impl.FailureResultImpl;
import com.intellij.build.events.impl.FileMessageEventImpl;
import com.intellij.build.events.impl.FinishBuildEventImpl;
import com.intellij.build.events.impl.OutputBuildEventImpl;
import com.intellij.build.events.impl.StartBuildEventImpl;
import com.intellij.build.events.impl.SuccessResultImpl;
import com.intellij.execution.ExecutionException;
import com.intellij.execution.configurations.GeneralCommandLine;
import com.intellij.execution.process.CapturingProcessHandler;
import com.intellij.execution.process.ProcessOutput;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import org.ca65.Asm6502Bundle;
import org.ca65.toolchain.Cc65ToolchainService;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.util.List;

/**
 * Executes a build configuration's ca65/ld65 commands sequentially,
 * streaming output and clickable diagnostics to the Build tool window.
 * Blocking; run from a background task (the build action) or a
 * before-run task.
 */
public final class Ca65BuildRunner {
    private Ca65BuildRunner() {
    }

    /** Runs the build; returns true on success. */
    public static boolean runBuild(@NotNull Project project, @NotNull Ca65BuildConfiguration config,
                                   @NotNull ProgressIndicator indicator) {
        String basePath = project.getBasePath();
        if (basePath == null) {
            return false;
        }
        BuildViewManager viewManager = project.getService(BuildViewManager.class);
        Object buildId = new Object();
        String title = Asm6502Bundle.message("BUILD.title", config.name);
        DefaultBuildDescriptor descriptor =
                new DefaultBuildDescriptor(buildId, title, basePath, System.currentTimeMillis());
        descriptor.setActivateToolWindowWhenFailed(true);
        // Open the Build view (which echoes the ca65/ld65 command lines) as
        // soon as a build starts, not only when it fails.
        descriptor.setActivateToolWindowWhenAdded(true);
        viewManager.onEvent(buildId, new StartBuildEventImpl(descriptor,
                Asm6502Bundle.message("BUILD.running")));

        boolean success = false;
        String failureMessage = Asm6502Bundle.message("BUILD.failed");
        try {
            File outputDir = new File(basePath, config.getEffectiveOutputDir());
            if (!outputDir.isDirectory() && !outputDir.mkdirs()) {
                viewManager.onEvent(buildId, new OutputBuildEventImpl(buildId,
                        Asm6502Bundle.message("BUILD.cannot.create.output.dir", outputDir.getPath()) + "\n", false));
            } else {
                success = runCommands(project, config, basePath, buildId, viewManager, indicator);
            }
        } catch (ExecutionException e) {
            viewManager.onEvent(buildId, new OutputBuildEventImpl(buildId, e.getMessage() + "\n", false));
            failureMessage = e.getMessage();
        } finally {
            viewManager.onEvent(buildId, new FinishBuildEventImpl(buildId, null, System.currentTimeMillis(),
                    success ? Asm6502Bundle.message("BUILD.finished") : failureMessage,
                    success ? new SuccessResultImpl() : new FailureResultImpl()));
        }
        if (success) {
            refreshOutputDir(basePath, config);
        }
        return success;
    }

    private static boolean runCommands(Project project, Ca65BuildConfiguration config, String basePath,
                                       Object buildId, BuildViewManager viewManager,
                                       ProgressIndicator indicator) throws ExecutionException {
        String binDir = Cc65ToolchainService.getInstance().getCc65BinDir();
        List<Ca65CommandBuilder.Command> commands = Ca65CommandBuilder.build(config, binDir);
        for (Ca65CommandBuilder.Command command : commands) {
            indicator.checkCanceled();
            indicator.setText(command.title());
            GeneralCommandLine commandLine = new GeneralCommandLine(command.argv())
                    .withWorkDirectory(basePath);
            viewManager.onEvent(buildId, new OutputBuildEventImpl(buildId,
                    commandLine.getCommandLineString() + "\n", true));
            ProcessOutput output = new CapturingProcessHandler(commandLine).runProcessWithProgressIndicator(indicator);
            emitOutput(project, basePath, buildId, viewManager, output.getStdout());
            emitOutput(project, basePath, buildId, viewManager, output.getStderr());
            if (output.isCancelled()) {
                return false;
            }
            if (output.getExitCode() != 0) {
                viewManager.onEvent(buildId, new OutputBuildEventImpl(buildId,
                        Asm6502Bundle.message("BUILD.exit.code", command.title(), output.getExitCode()) + "\n",
                        false));
                return false;
            }
        }
        return true;
    }

    private static void emitOutput(Project project, String basePath, Object buildId,
                                   BuildViewManager viewManager, String text) {
        if (text.isEmpty()) {
            return;
        }
        for (String line : text.split("\n", -1)) {
            if (line.isEmpty()) {
                continue;
            }
            viewManager.onEvent(buildId, new OutputBuildEventImpl(buildId, line + "\n", false));
            Ca65OutputParser.Diagnostic diagnostic = Ca65OutputParser.parseLine(line);
            if (diagnostic == null) {
                continue;
            }
            MessageEvent.Kind kind = switch (diagnostic.kind()) {
                case ERROR -> MessageEvent.Kind.ERROR;
                case WARNING -> MessageEvent.Kind.WARNING;
                case NOTE -> MessageEvent.Kind.INFO;
            };
            String group = Asm6502Bundle.message("BUILD.messages.group");
            if (diagnostic.file() != null && diagnostic.line() != null) {
                File file = new File(diagnostic.file());
                if (!file.isAbsolute()) {
                    file = new File(basePath, diagnostic.file());
                }
                FilePosition position = new FilePosition(file, diagnostic.line() - 1, 0);
                viewManager.onEvent(buildId, new FileMessageEventImpl(buildId, kind, group,
                        diagnostic.message(), line, position));
            }
        }
    }

    /** Makes the freshly written build outputs visible to the VFS. */
    private static void refreshOutputDir(String basePath, Ca65BuildConfiguration config) {
        File outputDir = new File(basePath, config.getEffectiveOutputDir());
        VirtualFile virtualFile = LocalFileSystem.getInstance().refreshAndFindFileByIoFile(outputDir);
        if (virtualFile != null) {
            virtualFile.refresh(true, true);
        }
    }
}
