package io.jenkins.plugins.dfcli;

import hudson.EnvVars;
import hudson.FilePath;
import hudson.Launcher;
import hudson.model.TaskListener;
import java.io.IOException;
import java.io.Serializable;

public class DfTask implements Serializable {
    private static final long serialVersionUID = 1L;

    private final String[] args;
    private final FilePath workspace;
    private final Launcher launcher;
    private final TaskListener listener;
    private final EnvVars env;

    public DfTask(String[] args, FilePath workspace, Launcher launcher, TaskListener listener, EnvVars env) {
        this.args = args;
        this.workspace = workspace;
        this.launcher = launcher;
        this.listener = listener;
        this.env = env;
    }

    public String execute() throws IOException, InterruptedException {
        // SETUP EXEC ENV
        DfTaskListener dfTaskListener = new DfTaskListener(listener);

        // CONFIG LAUNCHER
        Launcher.ProcStarter starter =
                launcher.launch().cmds(args).envs(env).pwd(workspace).stdout(dfTaskListener);

        // EXEC CMD
        int exitCode = starter.join();

        if (exitCode != 0) {
            throw new IOException("DFCli command failed with exit code: " + exitCode);
        }

        return dfTaskListener.getOutput();
    }

    public String[] getArgs() {
        return args;
    }

    public FilePath getWorkspace() {
        return workspace;
    }

    public Launcher getLauncher() {
        return launcher;
    }

    public TaskListener getListener() {
        return listener;
    }

    public EnvVars getEnv() {
        return env;
    }
}
