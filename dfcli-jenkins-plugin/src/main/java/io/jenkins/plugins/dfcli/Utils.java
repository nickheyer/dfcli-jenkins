package io.jenkins.plugins.dfcli;

import hudson.FilePath;
import hudson.model.Job;
import hudson.model.TaskListener;
import io.jenkins.plugins.dfcli.callables.TempDirCreator;
import java.io.IOException;
import org.apache.commons.lang3.exception.ExceptionUtils;

/**
 * @author gail
 */
public class Utils {
    public static final String BINARY_NAME = "dfcli";

    public static FilePath getWorkspace(Job<?, ?> project) {
        FilePath projectJob = new FilePath(project.getRootDir());
        FilePath workspace = projectJob.getParent();
        if (workspace == null) {
            throw new RuntimeException("Failed to get job workspace.");
        }
        workspace = workspace.sibling("workspace");
        if (workspace == null) {
            throw new RuntimeException("Failed to get job workspace.");
        }
        return workspace.child(project.getName());
    }

    public static String getDfCliBinaryName(boolean isWindows) {
        if (isWindows) {
            return BINARY_NAME + ".exe";
        }
        return BINARY_NAME;
    }

    /**
     * Delete temp dfcli cli home directory associated with the build number.
     *
     * @param ws           - The workspace
     * @param buildNumber  - The build number
     * @param taskListener - The logger
     */
    public static void deleteBuildDfcliHomeDir(FilePath ws, String buildNumber, TaskListener taskListener) {
        try {
            FilePath dfCliHomeDir = createAndGetDfCliHomeTempDir(ws, buildNumber);
            dfCliHomeDir.deleteRecursive();
        } catch (IOException | InterruptedException e) {
            taskListener
                    .getLogger()
                    .println("Failed while attempting to delete the DFCli CLI home dir \n"
                            + ExceptionUtils.getRootCauseMessage(e));
        }
    }

    /**
     * Create a temporary dfcli cli home directory under a given workspace
     */
    public static FilePath createAndGetTempDir(final FilePath ws) throws IOException, InterruptedException {
        // The token that combines the project name and unique number to create unique workspace directory.
        String workspaceList = System.getProperty("hudson.slaves.WorkspaceList");
        return ws.act(new TempDirCreator(workspaceList, ws));
    }

    public static FilePath createAndGetDfCliHomeTempDir(final FilePath ws, String buildNumber)
            throws IOException, InterruptedException {
        return createAndGetTempDir(ws).child(buildNumber).child(".dfcli");
    }
}
