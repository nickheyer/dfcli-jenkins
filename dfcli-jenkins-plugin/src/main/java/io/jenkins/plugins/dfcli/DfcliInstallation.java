package io.jenkins.plugins.dfcli;

import edu.umd.cs.findbugs.annotations.NonNull;
import hudson.EnvVars;
import hudson.Extension;
import hudson.Plugin;
import hudson.PluginWrapper;
import hudson.model.EnvironmentSpecific;
import hudson.model.Node;
import hudson.model.TaskListener;
import hudson.slaves.NodeSpecific;
import hudson.tools.ToolDescriptor;
import hudson.tools.ToolInstallation;
import hudson.tools.ToolInstaller;
import hudson.tools.ToolProperty;
import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import javax.annotation.Nonnull;
import jenkins.model.Jenkins;
import net.sf.json.JSONObject;
import org.jenkinsci.Symbol;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.StaplerRequest;

/**
 * @author gail
 */
public class DfcliInstallation extends ToolInstallation
        implements NodeSpecific<DfcliInstallation>, EnvironmentSpecific<DfcliInstallation> {

    public static final String DFCLI_BINARY_PATH = "DFCLI_BINARY_PATH";
    public static final String DFCLI_CLI_DEPENDENCIES_DIR = "DFCLI_CLI_DEPENDENCIES_DIR";
    public static final String DFCLI_CLI_USER_AGENT = "DFCLI_CLI_USER_AGENT";
    public static final String DfcliDependenciesDirName = "dependencies";

    @DataBoundConstructor
    public DfcliInstallation(String name, String home, List<? extends ToolProperty<?>> properties) {
        super(name, home, properties);
    }

    public DfcliInstallation forEnvironment(EnvVars environment) {
        return new DfcliInstallation(
                getName(), environment.expand(getHome()), getProperties().toList());
    }

    public DfcliInstallation forNode(@NonNull Node node, TaskListener log) throws IOException, InterruptedException {
        return new DfcliInstallation(
                getName(), translateFor(node, log), getProperties().toList());
    }

    @Override
    public void buildEnvVars(EnvVars env) {
        String home = getHome();
        if (home == null) {
            return;
        }
        env.put(DFCLI_BINARY_PATH, home);
        if (env.get(DFCLI_CLI_DEPENDENCIES_DIR) == null) {
            // Dfcli CLI dependencies directory is a sibling of all the other tools directories.
            // By doing this, we avoid downloading dependencies separately for each job in its temporary Dfcli home
            // directory.
            Path path = Paths.get(home).getParent();
            if (path != null) {
                env.put(
                        DFCLI_CLI_DEPENDENCIES_DIR,
                        path.resolve(DfcliDependenciesDirName).toString());
            }
        }
        env.putIfAbsent(DFCLI_CLI_USER_AGENT, "jenkins-dfcli-plugin" + getPluginVersion());
    }

    private String getPluginVersion() {
        Jenkins jenkins = Jenkins.getInstanceOrNull();
        if (jenkins == null) {
            return "";
        }
        Plugin plugin = jenkins.getPlugin("dfcli");
        if (plugin == null) {
            return "";
        }
        PluginWrapper wrapper = plugin.getWrapper();
        if (wrapper == null) {
            return "";
        }
        String version = wrapper.getVersion();
        // Return only the version prefix, without the agent information.
        return "/" + version.split(" ")[0];
    }

    @Symbol("dfcli")
    @Extension
    public static final class DescriptorImpl extends ToolDescriptor<DfcliInstallation> {

        public DescriptorImpl() {
            super(DfcliInstallation.class);
            load();
        }

        @Nonnull
        @Override
        public String getDisplayName() {
            return "DFCli CLI";
        }

        @Override
        public DfcliInstallation newInstance(StaplerRequest req, JSONObject formData) throws FormException {
            return (DfcliInstallation) super.newInstance(req, formData.getJSONObject("dfcli"));
        }

        @Override
        public List<? extends ToolInstaller> getDefaultInstallers() {
            List<ToolInstaller> installersList = new ArrayList<>();
            // The default installation will be from 'releases.dfcli.io'
            installersList.add(new ReleasesInstaller());
            return installersList;
        }

        @Override
        public boolean configure(StaplerRequest req, JSONObject o) throws FormException {
            Jenkins jenkins = Jenkins.getInstanceOrNull();
            if (jenkins != null && jenkins.hasPermission(Jenkins.ADMINISTER)) {
                super.configure(req, o);
                save();
                return true;
            }
            throw new FormException("User doesn't have permissions to save", "Server ID");
        }
    }
}
