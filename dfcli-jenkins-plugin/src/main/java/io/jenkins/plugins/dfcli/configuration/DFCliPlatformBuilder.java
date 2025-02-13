package io.jenkins.plugins.dfcli.configuration;

import static org.apache.commons.lang3.StringUtils.*;

import com.cloudbees.plugins.credentials.common.StandardListBoxModel;
import hudson.Extension;
import hudson.model.Descriptor;
import hudson.model.Hudson;
import hudson.model.Item;
import hudson.util.FormValidation;
import hudson.util.ListBoxModel;
import io.jenkins.plugins.dfcli.plugins.PluginsUtils;
import java.util.*;
import jenkins.model.GlobalConfiguration;
import jenkins.model.Jenkins;
import net.sf.json.JSONNull;
import net.sf.json.JSONObject;
import org.apache.commons.lang3.StringUtils;
import org.kohsuke.stapler.AncestorInPath;
import org.kohsuke.stapler.QueryParameter;
import org.kohsuke.stapler.StaplerRequest;
import org.kohsuke.stapler.interceptor.RequirePOST;
import org.kohsuke.stapler.verb.POST;

/**
 * Builder for DFCliPlatformInstance, used by the DFCli CLI config command.
 *
 * @author gail
 */
public class DFCliPlatformBuilder extends GlobalConfiguration {
    @SuppressWarnings("HttpUrlsUsage")
    private static final String[] KNOWN_PROTOCOLS = {"http://", "https://", "ssh://"};

    private static final String UNSAFE_HTTP_ERROR = "HTTP (non HTTPS) connections to the DFCli platform services are "
            + "not allowed. To bypass this rule, check 'Allow HTTP Connections'.";
    private static final String UNKNOWN_PROTOCOL_ERROR =
            "URL must start with one of the following protocols: " + Arrays.toString(KNOWN_PROTOCOLS);

    /**
     * Descriptor for {@link DFCliPlatformBuilder}. Used as a singleton.
     */
    @Extension
    // this marker indicates Hudson that this is an implementation of an extension point.
    public static final class DescriptorImpl extends Descriptor<GlobalConfiguration> {
        private List<DFCliPlatformInstance> dfcliInstances;
        private boolean allowHttpConnections;

        @SuppressWarnings("unused")
        public DescriptorImpl() {
            super(DFCliPlatformBuilder.class);
            load();
        }

        @SuppressWarnings("unused")
        @RequirePOST
        public ListBoxModel doFillCredentialsIdItems(@AncestorInPath Item project) {
            Jenkins jenkins = Jenkins.getInstanceOrNull();
            if (jenkins != null && jenkins.hasPermission(Jenkins.ADMINISTER)) {
                return PluginsUtils.fillPluginCredentials(project);
            }
            return new StandardListBoxModel();
        }

        /**
         * Performs on-the-fly validation of the form field 'ServerId'.
         *
         * @param value This parameter receives the value that the user has typed.
         * @return Indicates the outcome of the validation. This is sent to the browser.
         */
        @POST
        @SuppressWarnings("unused")
        public FormValidation doCheckServerId(@QueryParameter String value) {
            Jenkins.get().checkPermission(Jenkins.ADMINISTER);
            if (isBlank(value)) {
                return FormValidation.error("Please set server ID");
            }
            List<DFCliPlatformInstance> DFCliPlatformInstances = getDFCliPlatformInstances();
            if (DFCliPlatformInstances == null) {
                return FormValidation.ok();
            }
            int countServersByValueAsName = 0;
            for (DFCliPlatformInstance DFCliPlatformInstance : DFCliPlatformInstances) {
                if (DFCliPlatformInstance.getId().equals(value)) {
                    countServersByValueAsName++;
                    if (countServersByValueAsName > 1) {
                        return FormValidation.error("Duplicated DFCli platform instances ID");
                    }
                }
            }
            return FormValidation.ok();
        }

        @POST
        @SuppressWarnings("unused")
        public FormValidation doCheckUrl(@QueryParameter String value) {
            Jenkins.get().checkPermission(Jenkins.ADMINISTER);
            if (isBlank(value)) {
                return FormValidation.error("Please set the DFCli Platform URL");
            }
            return checkUrlInForm(value);
        }

        @POST
        @SuppressWarnings("unused")
        public FormValidation doCheckDistrofaceUrl(@QueryParameter String value) {
            Jenkins.get().checkPermission(Jenkins.ADMINISTER);
            return checkUrlInForm(value);
        }

        @POST
        @SuppressWarnings("unused")
        public FormValidation doCheckDistributionUrl(@QueryParameter String value) {
            Jenkins.get().checkPermission(Jenkins.ADMINISTER);
            return checkUrlInForm(value);
        }

        @POST
        @SuppressWarnings("unused")
        public FormValidation doCheckXrayUrl(@QueryParameter String value) {
            Jenkins.get().checkPermission(Jenkins.ADMINISTER);
            return checkUrlInForm(value);
        }

        /**
         * Performs on-the-fly validation of the form fields 'url', 'distrofaceUrl', 'distributionUrl', or 'xrayUrl'.
         *
         * @param value the URL value that the user has typed.
         * @return the outcome of the validation. This is sent to the browser.
         */
        private FormValidation checkUrlInForm(String value) {
            if (isInvalidProtocolOrEmptyUrl(value)) {
                return FormValidation.error(UNKNOWN_PROTOCOL_ERROR);
            }
            if (isUnsafe(isAllowHttpConnections(), value)) {
                return FormValidation.error(UNSAFE_HTTP_ERROR);
            }
            return FormValidation.ok();
        }

        @Override
        public boolean configure(StaplerRequest req, JSONObject o) throws FormException {
            Jenkins jenkins = Jenkins.getInstanceOrNull();
            if (jenkins != null && jenkins.hasPermission(Jenkins.ADMINISTER)) {
                setAllowHttpConnections(o.getBoolean("allowHttpConnections"));
                configureDFCliInstances(req, o);
                save();
                return super.configure(req, o);
            }
            throw new FormException("User doesn't have permissions to save", "Server ID");
        }

        private void configureDFCliInstances(StaplerRequest req, JSONObject o) throws FormException {
            List<DFCliPlatformInstance> dfcliInstances = new ArrayList<>();
            Object dfcliInstancesObj = o.get("dfcliInstances"); // an array or single object
            if (!JSONNull.getInstance().equals(dfcliInstancesObj)) {
                dfcliInstances = req.bindJSONToList(DFCliPlatformInstance.class, dfcliInstancesObj);
            }

            if (!isDFCliInstancesIDConfigured(dfcliInstances)) {
                throw new FormException("Please set the Instance ID.", "ServerID");
            }

            if (isInstanceDuplicated(dfcliInstances)) {
                throw new FormException("The DFCli server ID you have entered is already configured", "Server ID");
            }

            if (isEmptyUrl(dfcliInstances)) {
                throw new FormException("Please set the DFCli Platform URL", "URL");
            }

            for (DFCliPlatformInstance dfcliInstance : dfcliInstances) {
                if (isUnsafe(
                        isAllowHttpConnections(),
                        dfcliInstance.getUrl(),
                        dfcliInstance.getDistrofaceUrl(),
                        dfcliInstance.getDistributionUrl(),
                        dfcliInstance.getXrayUrl())) {
                    throw new FormException(UNSAFE_HTTP_ERROR, "URL");
                }
                if (isInvalidProtocolOrEmptyUrl(
                        dfcliInstance.getUrl(),
                        dfcliInstance.getDistrofaceUrl(),
                        dfcliInstance.getDistributionUrl(),
                        dfcliInstance.getXrayUrl())) {
                    throw new FormException(UNKNOWN_PROTOCOL_ERROR, "URL");
                }
            }
            setDfcliInstances(dfcliInstances);
        }

        /**
         * verify instance ID was provided.
         */
        private boolean isDFCliInstancesIDConfigured(List<DFCliPlatformInstance> dfcliInstances) {
            if (dfcliInstances == null) {
                return true;
            }
            for (DFCliPlatformInstance server : dfcliInstances) {
                String platformId = server.getId();
                if (isBlank(platformId)) {
                    return false;
                }
            }
            return true;
        }

        private boolean isInstanceDuplicated(List<DFCliPlatformInstance> dfcliInstances) {
            Set<String> serversNames = new HashSet<>();
            if (dfcliInstances == null) {
                return false;
            }
            for (DFCliPlatformInstance instance : dfcliInstances) {
                String id = instance.getId();
                if (serversNames.contains(id)) {
                    return true;
                }
                serversNames.add(id);
            }
            return false;
        }

        /**
         * verify platform URL was provided.
         */
        private boolean isEmptyUrl(List<DFCliPlatformInstance> dfcliInstances) {
            if (dfcliInstances == null) {
                return false;
            }
            for (DFCliPlatformInstance instance : dfcliInstances) {
                if (isBlank(instance.getUrl())) {
                    return true;
                }
            }
            return false;
        }

        /**
         * Used by Jenkins Jelly for displaying values.
         */
        public List<DFCliPlatformInstance> getDfcliInstances() {
            return dfcliInstances;
        }

        /**
         * Used by Jenkins Jelly for setting values.
         */
        public void setDfcliInstances(List<DFCliPlatformInstance> dfcliInstances) {
            this.dfcliInstances = dfcliInstances;
        }

        /**
         * Used by Jenkins Jelly for setting values.
         */
        @SuppressWarnings("unused")
        public boolean isAllowHttpConnections() {
            return this.allowHttpConnections;
        }

        /**
         * Used by Jenkins Jelly for setting values.
         */
        public void setAllowHttpConnections(boolean allowHttpConnections) {
            this.allowHttpConnections = allowHttpConnections;
        }
    }

    /**
     * Return true if at least one of the URLs are using an unsafe HTTP protocol and the "Allow HTTP Connection" option is not set.
     *
     * @param urls - The URL to check
     * @return true if the URL is using an unsafe HTTP protocol and the "Allow HTTP Connection" option is not set.
     */
    static boolean isUnsafe(boolean allowHttpConnections, String... urls) {
        if (allowHttpConnections) {
            return false;
        }
        for (String url : urls) {
            //noinspection HttpUrlsUsage
            if (startsWith(url, "http://")) {
                return true;
            }
        }
        return false;
    }

    /**
     * Return true if all input URLs are empty or starting with a known protocol.
     *
     * @param urls - The URL to check
     * @return true if all input URLs are empty or starting with a known protocol.
     */
    static boolean isInvalidProtocolOrEmptyUrl(String... urls) {
        return Arrays.stream(urls)
                .filter(StringUtils::isNotBlank)
                .anyMatch(url -> !startsWithAny(url, KNOWN_PROTOCOLS));
    }

    /**
     * Returns the list of {@link DFCliPlatformInstance} configured.
     *
     * @return can be empty but never null.
     */
    public static List<DFCliPlatformInstance> getDFCliPlatformInstances() {
        DFCliPlatformBuilder.DescriptorImpl descriptor =
                (DFCliPlatformBuilder.DescriptorImpl) Hudson.get().getDescriptor(DFCliPlatformBuilder.class);
        if (descriptor == null) {
            return new ArrayList<>();
        }
        return descriptor.getDfcliInstances();
    }
}
