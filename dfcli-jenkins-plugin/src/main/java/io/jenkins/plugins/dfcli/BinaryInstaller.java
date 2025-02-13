package io.jenkins.plugins.dfcli;

import hudson.FilePath;
import hudson.model.Node;
import hudson.model.TaskListener;
import hudson.tools.ToolInstallation;
import hudson.tools.ToolInstaller;
import hudson.tools.ToolInstallerDescriptor;
import io.jenkins.plugins.dfcli.callables.DFCliDownloader;
import io.jenkins.plugins.dfcli.configuration.DFCliPlatformInstance;
import io.jenkins.plugins.dfcli.configuration.JenkinsProxyConfiguration;
import java.io.IOException;

/**
 * Installer for DFCli CLI binary.
 *
 * @author gail
 */
public abstract class BinaryInstaller extends ToolInstaller {
    protected BinaryInstaller(String label) {
        super(label);
    }

    /**
     * @param tool the tool being installed.
     * @param node the computer on which to install the tool.
     * @return Node's filesystem location where a tool should be installed.
     */
    protected FilePath getToolLocation(ToolInstallation tool, Node node) throws IOException, InterruptedException {
        FilePath location = preferredLocation(tool, node);
        if (!location.exists()) {
            location.mkdirs();
        }
        return location;
    }

    public abstract static class DescriptorImpl<T extends BinaryInstaller> extends ToolInstallerDescriptor<T> {
        /**
         * This ID needs to be unique, and needs to match the ID token in the JSON update file.
         * <p>
         * By default, we use the fully-qualified class name of the {@link BinaryInstaller} subtype.
         */
        @Override
        public String getId() {
            return clazz.getName().replace('$', '.');
        }
    }

    @Override
    public FilePath performInstallation(ToolInstallation tool, Node node, TaskListener log)
            throws IOException, InterruptedException {

        FilePath installLocation = getToolLocation(tool, node);
        if (!installLocation.exists()) {
            installLocation.mkdirs();
        }

        return installLocation;
    }

    public static FilePath performDfCliInstallation(
            FilePath toolLocation,
            TaskListener log,
            String version,
            DFCliPlatformInstance instance,
            String repository,
            String binaryName)
            throws IOException, InterruptedException {
        JenkinsProxyConfiguration proxyConfiguration = new JenkinsProxyConfiguration();
        // Download Dfcli CLI binary
        toolLocation.act(new DFCliDownloader(proxyConfiguration, version, instance, log, repository, binaryName));
        return toolLocation;
    }
}
