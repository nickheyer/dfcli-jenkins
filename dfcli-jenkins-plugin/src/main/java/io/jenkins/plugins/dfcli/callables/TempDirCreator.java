package io.jenkins.plugins.dfcli.callables;

import hudson.FilePath;
import hudson.remoting.VirtualChannel;
import java.io.File;
import java.io.IOException;
import java.util.Objects;
import jenkins.MasterToSlaveFileCallable;
import lombok.AllArgsConstructor;

/**
 * Create a temporary directory inside the agent.
 */
@AllArgsConstructor
public class TempDirCreator extends MasterToSlaveFileCallable<FilePath> {
    private String workspaceList;
    private FilePath ws;

    @Override
    public FilePath invoke(File f, VirtualChannel channel) throws IOException, InterruptedException {
        FilePath tempDir = ws.sibling(ws.getName() + Objects.toString(workspaceList, "@") + "tmp");
        if (tempDir == null) {
            throw new RuntimeException("Failed to create DFCLI temporary directory");
        }
        tempDir = tempDir.child("dfcli");
        File tempDirFile = new File(tempDir.getRemote());
        if (tempDirFile.mkdirs()) {
            tempDirFile.deleteOnExit();
        }
        return tempDir;
    }
}
