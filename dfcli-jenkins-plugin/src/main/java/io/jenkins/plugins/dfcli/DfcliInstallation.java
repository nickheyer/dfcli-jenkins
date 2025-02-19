package io.jenkins.plugins.dfcli;

import hudson.EnvVars;
import hudson.Extension;
import hudson.FilePath;
import hudson.Launcher;
import hudson.model.EnvironmentSpecific;
import hudson.model.Node;
import hudson.model.TaskListener;
import hudson.slaves.NodeSpecific;
import hudson.tools.ToolDescriptor;
import hudson.tools.ToolInstallation;
import hudson.tools.ToolProperty;
import hudson.util.ArgumentListBuilder;
import hudson.util.Secret;
import java.io.IOException;
import java.io.Serializable;
import java.util.List;
import jenkins.model.Jenkins;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.DataBoundSetter;
import org.jenkinsci.Symbol;

public class DfCliInstallation extends ToolInstallation implements NodeSpecific<DfCliInstallation>, EnvironmentSpecific<DfCliInstallation> {

    private static final long serialVersionUID = 1L;
    private static final Object installLock = new Object();

    @DataBoundConstructor
    public DfCliInstallation(String name, String home, List<? extends ToolProperty<?>> properties) {
        super(name, home, properties);
    }

    @Override
    public DfCliInstallation forEnvironment(EnvVars environment) {
        String expandedHome = environment.expand(getHome());
        return new DfCliInstallation(getName(), expandedHome, getProperties().toList());
    }

    @Override
    public DfCliInstallation forNode(Node node, TaskListener log) throws IOException, InterruptedException {
        FilePath rootPath = node.getRootPath();
        if (rootPath == null) {
            throw new IOException("Node root path is null");
        }

        // GET GLOBAL CONFIG
        DfCliConfig config = DfCliConfig.get();
        if (config == null) {
            throw new IOException("DFCli global config not found");
        }

        if (getHome() != null && !getHome().trim().isEmpty()) {
            doLoginIfNeeded(node, log, getHome(), config);
            return this;
        }

        // AUTO INSTALL TO DEFAULT LOCATION
        FilePath installDir = rootPath.child("tools").child("dfcli").child(getName());
        synchronized (installLock) {
            if (!installDir.exists()) {
                installDir.mkdirs();
            }

            FilePath binary = installDir.child(isWindows() ? "dfcli.exe" : "dfcli");
            if (!binary.exists()) {
                GithubInstaller.installLatest(installDir, log);
            }

            doLoginIfNeeded(node, log, installDir.getRemote(), config);

            return new DfCliInstallation(getName(), installDir.getRemote(), getProperties().toList());
        }
    }

    // LOGIN USING GLOBAL CONFIG
    private void doLoginIfNeeded(Node node, TaskListener log, String homePath, DfCliConfig config)
            throws IOException, InterruptedException {
        String serverUrl = config.getServerUrl();
        String username = config.getUsername();
        Secret password = config.getPassword();
        Secret token = config.getToken();

        if (serverUrl == null || serverUrl.isEmpty()) {
            return;
        }
        if ((username == null || username.isEmpty() || password == null) && (token == null)) {
            return;
        }

        FilePath nodeRoot = node.getRootPath();
        if (nodeRoot == null) {
            throw new IOException("Node root path is null, cannot do dfcli login");
        }
        
        FilePath dfcliDir = nodeRoot.child("tools").child("dfcli").child(getName());
        Launcher launcher = node.createLauncher(log);

        ArgumentListBuilder loginCmd = new ArgumentListBuilder();
        if (launcher.isUnix()) {
            loginCmd.add(homePath + "/dfcli");
        } else {
            loginCmd.add(homePath + "\\dfcli.exe");
        }
        loginCmd.add("login");
        loginCmd.add("--server", serverUrl);

        if (username != null && !username.isEmpty() && password != null) {
            loginCmd.add("--username", username);
            loginCmd.add("--password", Secret.toString(password));
        } else if (token != null) {
            loginCmd.add("--token", Secret.toString(token));
        }
        
        int code = launcher.launch()
                .cmds(loginCmd)
                .pwd(dfcliDir)
                .quiet(true)
                .join();

        if (code != 0) {
            throw new IOException("DFCli login failed with exit code " + code);
        }
    }

    private boolean isWindows() {
        return System.getProperty("os.name").toLowerCase().contains("windows");
    }

    @Symbol("dfcli")
    @Extension
    public static class DescriptorImpl extends ToolDescriptor<DfCliInstallation> {
        public DescriptorImpl() {
            load();
        }

        @Override
        public String getDisplayName() {
            return "DFCli";
        }

        @Override
        public DfCliInstallation[] getInstallations() {
            return super.getInstallations();
        }

        @Override
        public void setInstallations(DfCliInstallation... installations) {
            super.setInstallations(installations);
            save();
        }
    }
}
