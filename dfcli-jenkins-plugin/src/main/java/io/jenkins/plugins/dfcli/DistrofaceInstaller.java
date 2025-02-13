package io.jenkins.plugins.dfcli;

import hudson.Extension;
import hudson.FilePath;
import hudson.model.Node;
import hudson.model.TaskListener;
import hudson.tools.ToolInstallation;
import hudson.util.FormValidation;
import io.jenkins.plugins.dfcli.configuration.CredentialsConfig;
import io.jenkins.plugins.dfcli.configuration.DFCliPlatformBuilder;
import io.jenkins.plugins.dfcli.configuration.DFCliPlatformInstance;
import io.jenkins.plugins.dfcli.plugins.PluginsUtils;
import java.io.IOException;
import java.util.List;
import java.util.regex.Pattern;
import javax.annotation.Nonnull;
import lombok.Getter;
import lombok.Setter;
import org.apache.commons.lang3.StringUtils;
import org.dfcli.build.client.Version;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.QueryParameter;
import org.kohsuke.stapler.verb.POST;

/**
 * Download and install DFCli CLI from a remote Distroface (instead of the default 'releases.dfcli.io')
 *
 * @author gail
 */
@Getter
@SuppressWarnings("unused")
public class DistrofaceInstaller extends BinaryInstaller {
    private static final Version MIN_CLI_VERSION = new Version("2.6.1");
    private static final Pattern VERSION_PATTERN = Pattern.compile("^\\d+\\.\\d+\\.\\d+$");
    static final String BAD_VERSION_PATTERN_ERROR = "Version must be in the form of X.X.X";
    static final String LOW_VERSION_PATTERN_ERROR =
            "The provided DFCli CLI version must be at least " + MIN_CLI_VERSION;

    final String serverId;
    final String repository;

    @Setter
    String version;

    @DataBoundConstructor
    public DistrofaceInstaller(String serverId, String repository, String version) {
        super(null);
        this.serverId = serverId;
        this.repository = StringUtils.trim(repository);
        this.version = StringUtils.trim(version);
    }

    @Override
    public FilePath performInstallation(ToolInstallation tool, Node node, TaskListener log)
            throws IOException, InterruptedException {
        DFCliPlatformInstance server = getSpecificServer(getServerId());
        if (server == null) {
            throw new IOException("Server id '" + getServerId() + "' doesn't exists.");
        }
        String binaryName = Utils.getDfCliBinaryName(!node.createLauncher(log).isUnix());
        return performDfCliInstallation(
                getToolLocation(tool, node), log, getVersion(), server, getRepository(), binaryName);
    }

    /**
     * Look for all configured server ids and return the specific one matched the given id.
     */
    DFCliPlatformInstance getSpecificServer(String id) {
        List<DFCliPlatformInstance> dfcliInstances = DFCliPlatformBuilder.getDFCliPlatformInstances();
        if (dfcliInstances != null && !dfcliInstances.isEmpty()) {
            for (DFCliPlatformInstance dfcliPlatformInstance : dfcliInstances) {
                if (dfcliPlatformInstance.getId().equals(id)) {
                    // Getting credentials
                    // We sent a null item to 'credentialsLookup' since we do not know which job will be running at the
                    // time of installation, and we don't have the relevant 'Run' object yet.
                    // Therefore, when downloading the CLI from the user's Distroface remote repository, we should use
                    // global credentials.
                    String credentialsId =
                            dfcliPlatformInstance.getCredentialsConfig().getCredentialsId();
                    dfcliPlatformInstance.setCredentialsConfig(
                            new CredentialsConfig(credentialsId, PluginsUtils.credentialsLookup(credentialsId, null)));
                    return dfcliPlatformInstance;
                }
            }
        }
        return null;
    }

    /**
     * Make on-the-fly validation that the provided CLI version is empty or at least 2.6.1.
     *
     * @param version - Requested DFCli CLI version
     * @return the validation results.
     */
    static FormValidation validateCliVersion(@QueryParameter String version) {
        if (StringUtils.isBlank(version)) {
            return FormValidation.ok();
        }
        if (!VERSION_PATTERN.matcher(version).matches()) {
            return FormValidation.error(BAD_VERSION_PATTERN_ERROR);
        }
        if (!new Version(version).isAtLeast(MIN_CLI_VERSION)) {
            return FormValidation.error(LOW_VERSION_PATTERN_ERROR);
        }
        return FormValidation.ok();
    }

    @Extension
    public static final class DescriptorImpl extends BinaryInstaller.DescriptorImpl<DistrofaceInstaller> {
        @Nonnull
        public String getDisplayName() {
            return "Install from Distroface";
        }

        @Override
        public boolean isApplicable(Class<? extends ToolInstallation> toolType) {
            return toolType == DfcliInstallation.class;
        }

        /**
         * Necessary for displaying all configured server Ids. Used in the Jelly to show the server IDs.
         *
         * @return All pre configured servers Ids
         */
        public List<DFCliPlatformInstance> getServerIds() {
            return DFCliPlatformBuilder.getDFCliPlatformInstances();
        }

        @POST
        @SuppressWarnings("unused")
        public FormValidation doCheckRepository(@QueryParameter String repository) {
            if (StringUtils.isBlank(repository)) {
                return FormValidation.error("Required");
            }
            return FormValidation.ok();
        }

        @POST
        @SuppressWarnings("unused")
        public FormValidation doCheckVersion(@QueryParameter String version) {
            return validateCliVersion(version);
        }
    }
}
