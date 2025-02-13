package io.jenkins.plugins.dfcli;

import hudson.EnvVars;
import hudson.Extension;
import hudson.FilePath;
import hudson.model.EnvironmentSpecific;
import hudson.model.Node;
import hudson.model.TaskListener;
import hudson.slaves.NodeSpecific;
import hudson.tools.ToolDescriptor;
import hudson.tools.ToolInstallation;
import hudson.tools.ToolProperty;
import java.io.IOException;
import java.util.Collections;
import java.util.List;
import jenkins.model.Jenkins;
import org.kohsuke.stapler.DataBoundConstructor;
import org.jenkinsci.Symbol;

public class DfCliInstallation extends ToolInstallation implements NodeSpecific<DfCliInstallation>, EnvironmentSpecific<DfCliInstallation> {
    
    public static final String DFCLI_BINARY = "DFCLI_BINARY";
    private final boolean installFromGithub;
    private static final Object installLock = new Object();
    
    @DataBoundConstructor
    public DfCliInstallation(String name, String home, List<? extends ToolProperty<?>> properties, boolean installFromGithub) {
        super(name, home, properties);
        this.installFromGithub = installFromGithub;
    }

    public boolean isInstallFromGithub() {
        return installFromGithub;
    }

    @Override
    public DfCliInstallation forEnvironment(EnvVars environment) {
        return new DfCliInstallation(getName(), environment.expand(getHome()), getProperties().toList(), installFromGithub);
    }

    @Override
    public DfCliInstallation forNode(Node node, TaskListener log) throws IOException, InterruptedException {
        if (!installFromGithub) {
            return this;
        }

        synchronized (installLock) {
            // CREATE TOOL DIRECTORY UNDER JENKINS HOME
            FilePath rootPath = node.getRootPath();
            if (rootPath == null) {
                throw new IOException("Node root path is null");
            }

            FilePath toolsDir = rootPath.child("tools");
            FilePath installDir = toolsDir.child("dfcli").child(getName());
            
            // CHECK FOR EXISTING INSTALLATION
            FilePath marker = installDir.child(".installed");
            FilePath binary = installDir.child(System.getProperty("os.name").toLowerCase().contains("windows") ? "dfcli.exe" : "dfcli");

            if (!marker.exists() || !binary.exists()) {
                if (!toolsDir.exists()) {
                    toolsDir.mkdirs();
                }
                if (!installDir.exists()) {
                    installDir.mkdirs();
                }
                
                GithubInstaller.installLatest(installDir, log);
                marker.write("", "UTF-8");
                log.getLogger().println("DFCli installed to: " + installDir.getRemote());
            } else {
                log.getLogger().println("Using existing DFCli installation at: " + installDir.getRemote());
            }

            return new DfCliInstallation(getName(), installDir.getRemote(), getProperties().toList(), true);
        }
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
            DfCliInstallation[] installations = super.getInstallations();
            if (installations.length == 0) {
                installations = new DfCliInstallation[]{
                    new DfCliInstallation("dfcli", "", Collections.emptyList(), true)
                };
                setInstallations(installations);
                save();
            }
            return installations;
        }
    }
}
