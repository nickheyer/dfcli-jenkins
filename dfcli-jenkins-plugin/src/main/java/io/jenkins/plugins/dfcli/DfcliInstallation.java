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


public class DfCliInstallation extends ToolInstallation
        implements NodeSpecific<DfCliInstallation>, EnvironmentSpecific<DfCliInstallation>, Serializable {

    private static final long serialVersionUID = 1L;
    private static final Object installLock = new Object();

    private String serverUrl;
    private String username;
    private transient Secret password;
    private transient Secret accessToken;

    private String encryptedPassword;
    private String encryptedToken;

    @DataBoundConstructor
    public DfCliInstallation(String name, String home, List<? extends ToolProperty<?>> properties) {
        super(name, home, properties);
    }

    // GETTERS/SETTERS FOR CONFIG JELLY
    public String getServerUrl() {
        return serverUrl;
    }
    @DataBoundSetter
    public void setServerUrl(String serverUrl) {
        this.serverUrl = serverUrl;
    }

    public String getUsername() {
        return username;
    }
    @DataBoundSetter
    public void setUsername(String username) {
        this.username = username;
    }

    public Secret getPassword() {
        return password;
    }
    @DataBoundSetter
    public void setPassword(Secret password) {
        this.password = password;
        this.encryptedPassword = Secret.toString(password);
    }

    public Secret getAccessToken() {
        return accessToken;
    }
    @DataBoundSetter
    public void setAccessToken(Secret accessToken) {
        this.accessToken = accessToken;
        this.encryptedToken = Secret.toString(accessToken);
    }

    protected Object readResolve() {
        if (encryptedPassword != null) {
            password = Secret.fromString(encryptedPassword);
        }
        if (encryptedToken != null) {
            accessToken = Secret.fromString(encryptedToken);
        }
        return this;
    }

    @Override
    public DfCliInstallation forEnvironment(EnvVars environment) {
        String expandedHome = environment.expand(getHome());
        DfCliInstallation copy = new DfCliInstallation(getName(), expandedHome, getProperties().toList());
        copy.serverUrl = this.serverUrl;
        copy.username = this.username;
        copy.encryptedPassword = this.encryptedPassword;
        copy.encryptedToken = this.encryptedToken;
        copy.readResolve();  // RECONSTITUTE
        return copy;
    }

    @Override
    public DfCliInstallation forNode(Node node, TaskListener log) throws IOException, InterruptedException {
        FilePath rootPath = node.getRootPath();
        if (rootPath == null) {
            throw new IOException("Node root path is null");
        }

        if (getHome() != null && !getHome().trim().isEmpty()) {
            doLoginIfNeeded(node, log, getHome());
            return this;
        }

        // Otherwise, download + install to [JENKINS_ROOT]/tools/dfcli/[installation name]
        FilePath installDir = rootPath.child("tools").child("dfcli").child(getName());
        synchronized (installLock) {
            if (!installDir.exists()) {
                installDir.mkdirs();
            }

            FilePath binary = installDir.child(isWindows() ? "dfcli.exe" : "dfcli");
            if (!binary.exists()) {
                GithubInstaller.installLatest(installDir, log);
            }

            doLoginIfNeeded(node, log, installDir.getRemote());

            // HOME -> installDir
            DfCliInstallation copy = new DfCliInstallation(getName(), installDir.getRemote(), getProperties().toList());
            copy.serverUrl = this.serverUrl;
            copy.username = this.username;
            copy.password = this.password;
            copy.accessToken = this.accessToken;
            return copy;
        }
    }

    private void doLoginIfNeeded(Node node, TaskListener log, String homePath)
            throws IOException, InterruptedException {
        if (serverUrl == null || serverUrl.isEmpty()) {
            return;
        }
        if ((username == null || username.isEmpty() || password == null) && (accessToken == null)) {
            return;
        }

        FilePath nodeRoot = node.getRootPath();
        if (nodeRoot == null) {
            throw new IOException("Node root path is null, cannot do dfcli login");
        }
        FilePath dfcliDir = nodeRoot.child("tools").child("dfcli").child(getName());
        Launcher launcher = node.createLauncher(log);

        // BUILDING "dfcli login ..."
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
        } else if (accessToken != null) {
            loginCmd.add("--token", Secret.toString(accessToken));
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

    // LOADING/SAVING CONFIG
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
