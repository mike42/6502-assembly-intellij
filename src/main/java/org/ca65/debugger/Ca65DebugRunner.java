package org.ca65.debugger;

import com.intellij.execution.ExecutionException;
import com.intellij.execution.configurations.RunProfile;
import com.intellij.execution.configurations.RunProfileState;
import com.intellij.execution.executors.DefaultDebugExecutor;
import com.intellij.execution.runners.ExecutionEnvironment;
import com.intellij.execution.runners.GenericProgramRunner;
import com.intellij.execution.ui.RunContentDescriptor;
import com.intellij.xdebugger.XDebugProcess;
import com.intellij.xdebugger.XDebugProcessStarter;
import com.intellij.xdebugger.XDebugSession;
import com.intellij.xdebugger.XDebuggerManager;
import org.ca65.run.Ca65RunConfiguration;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Lights up the Debug button for emulator run configurations whose
 * emulator/target combination supports debugging (VICE binary monitor).
 */
public class Ca65DebugRunner extends GenericProgramRunner<com.intellij.execution.configurations.RunnerSettings> {
    private static final String RUNNER_ID = "Ca65DebugRunner";

    @Override
    public @NotNull String getRunnerId() {
        return RUNNER_ID;
    }

    @Override
    public boolean canRun(@NotNull String executorId, @NotNull RunProfile profile) {
        return DefaultDebugExecutor.EXECUTOR_ID.equals(executorId)
                && profile instanceof Ca65RunConfiguration configuration
                && configuration.isDebuggable();
    }

    @Override
    protected @Nullable RunContentDescriptor doExecute(@NotNull RunProfileState state,
                                                       @NotNull ExecutionEnvironment environment)
            throws ExecutionException {
        Ca65RunConfiguration configuration = (Ca65RunConfiguration) environment.getRunProfile();
        XDebugSession session = XDebuggerManager.getInstance(environment.getProject())
                .startSession(environment, new XDebugProcessStarter() {
                    @Override
                    public @NotNull XDebugProcess start(@NotNull XDebugSession session) throws ExecutionException {
                        return new AsmDebugProcess(session, configuration);
                    }
                });
        return session.getRunContentDescriptor();
    }
}
