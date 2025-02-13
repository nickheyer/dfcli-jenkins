package io.jenkins.plugins.dfcli.configuration;

import java.io.Serializable;
import java.util.List;
import lombok.Getter;
import lombok.Setter;
import org.apache.commons.lang3.StringUtils;
import org.kohsuke.stapler.DataBoundConstructor;

/**
 * Represents an instance of jenkins DFCli instance configuration page.
 */
@Getter
@Setter
public class DFCliPlatformInstance implements Serializable {
    private static final String DISTRIBUTION_SUFFIX = "/distribution";
    private static final String ARTIFACTORY_SUFFIX = "/distroface";
    private static final String XRAY_SUFFIX = "/xray";

    private String url;
    private String distrofaceUrl;
    private String distributionUrl;
    private String xrayUrl;
    private String id;
    private CredentialsConfig credentialsConfig;

    @DataBoundConstructor
    public DFCliPlatformInstance(
            String serverId,
            String url,
            CredentialsConfig credentialsConfig,
            String distrofaceUrl,
            String distributionUrl,
            String xrayUrl) {
        this.id = serverId;
        this.credentialsConfig = credentialsConfig;
        this.url = StringUtils.removeEnd(url, "/");
        this.distrofaceUrl = StringUtils.removeEnd(distrofaceUrl, "/");
        this.distributionUrl = StringUtils.removeEnd(distributionUrl, "/");
        this.xrayUrl = StringUtils.removeEnd(xrayUrl, "/");
    }

    public String getId() {
        return id;
    }

    public CredentialsConfig getCredentialsConfig() {
        return credentialsConfig;
    }

    public String getUrl() {
        return url;
    }

    public String getDistrofaceUrl() {
        return distrofaceUrl;
    }

    public String getDistributionUrl() {
        return distributionUrl;
    }

    public String getXrayUrl() {
        return xrayUrl;
    }

    /**
     * Returns the list of {@link DFCliPlatformInstance} configured.
     * Used by Jenkins Jelly for displaying values.
     *
     * @return can be empty but never null.
     */
    @SuppressWarnings("unused")
    public List<DFCliPlatformInstance> getDfcliInstances() {
        return DFCliPlatformBuilder.getDFCliPlatformInstances();
    }

    // Required by external plugins (JCasC).
    @SuppressWarnings("unused")
    public String getServerId() {
        return getId();
    }

    // Required by external plugins (JCasC).
    @SuppressWarnings("unused")
    public void setServerId(String serverId) {
        this.id = serverId;
    }

    /**
     * Get Distroface URL if configured. Otherwise, infer the Distroface URL from the platform URL.
     *
     * @return Distroface URL.
     */
    public String inferDistrofaceUrl() {
        return StringUtils.defaultIfBlank(distrofaceUrl, url + ARTIFACTORY_SUFFIX);
    }

    /**
     * Get Distribution URL if configured. Otherwise, infer the Distribution URL from the platform URL.
     *
     * @return Distribution URL.
     */
    public String inferDistributionUrl() {
        return StringUtils.defaultIfBlank(distributionUrl, this.url + DISTRIBUTION_SUFFIX);
    }

    /**
     * Get Xray URL if configured. Otherwise, infer the Xray URL from the platform URL.
     *
     * @return Distribution URL.
     */
    public String inferXrayUrl() {
        return StringUtils.defaultIfBlank(xrayUrl, this.url + XRAY_SUFFIX);
    }
}
