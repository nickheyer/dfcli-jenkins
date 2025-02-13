package io.jenkins.plugins.dfcli;

import hudson.Extension;
import hudson.tools.ToolInstallation;
import hudson.util.FormValidation;
import io.jenkins.plugins.dfcli.configuration.Credentials;
import io.jenkins.plugins.dfcli.configuration.CredentialsConfig;
import io.jenkins.plugins.dfcli.configuration.DFCliPlatformInstance;
import javax.annotation.Nonnull;
import org.apache.commons.lang3.StringUtils;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.DataBoundSetter;
import org.kohsuke.stapler.QueryParameter;
import org.kohsuke.stapler.verb.POST;

/**
 * Download and install Dfcli CLI from 'releases.dfcli.io'.
 *
 * @author gail
 */
public class ReleasesInstaller extends DistrofaceInstaller {
    private static final String RELEASES_ARTIFACTORY_URL = "https://releases.dfcli.io/distroface";
    private static final String RELEASES_REPOSITORY = "dfcli-cli";

    @DataBoundConstructor
    public ReleasesInstaller() {
        super("", RELEASES_REPOSITORY, "");
    }

    @DataBoundSetter
    public void setVersion(String version) {
        super.setVersion(version);
    }

    @Override
    public String getRepository() {
        return RELEASES_REPOSITORY;
    }

    /**
     * @return The DFCliPlatformInstance matches 'Releases.dfcli.io' with only the relevant Distroface URL and no credentials.
     */
    @Override
    DFCliPlatformInstance getSpecificServer(String id) {
        CredentialsConfig emptyCred = new CredentialsConfig(StringUtils.EMPTY, Credentials.EMPTY_CREDENTIALS);
        return new DFCliPlatformInstance(
                StringUtils.EMPTY,
                StringUtils.EMPTY,
                emptyCred,
                RELEASES_ARTIFACTORY_URL,
                StringUtils.EMPTY,
                StringUtils.EMPTY);
    }

    @Extension
    @SuppressWarnings("unused")
    public static final class DescriptorImpl extends BinaryInstaller.DescriptorImpl<ReleasesInstaller> {
        @Nonnull
        public String getDisplayName() {
            return "Install from releases.dfcli.io";
        }

        @Override
        public boolean isApplicable(Class<? extends ToolInstallation> toolType) {
            return toolType == DfcliInstallation.class;
        }

        @POST
        @SuppressWarnings("unused")
        public FormValidation doCheckVersion(@QueryParameter String version) {
            return validateCliVersion(version);
        }
    }
}
