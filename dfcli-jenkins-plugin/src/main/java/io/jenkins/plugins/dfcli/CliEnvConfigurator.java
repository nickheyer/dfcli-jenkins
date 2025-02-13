package io.jenkins.plugins.dfcli;

import hudson.EnvVars;
import io.jenkins.plugins.dfcli.actions.DFCliConfigEncryption;
import io.jenkins.plugins.dfcli.configuration.JenkinsProxyConfiguration;
import org.apache.commons.lang3.StringUtils;

/**
 * Configures DFCli CLI environment variables for the job.
 *
 * @author yahavi
 **/
public class CliEnvConfigurator {
    static final String DFCLI_CLI_DEFAULT_EXCLUSIONS = "*password*;*psw*;*secret*;*key*;*token*;*auth*";
    static final String DFCLI_CLI_ENCRYPTION_KEY = "DFCLI_CLI_ENCRYPTION_KEY";
    static final String DFCLI_CLI_BUILD_NUMBER = "DFCLI_CLI_BUILD_NUMBER";
    public static final String DFCLI_CLI_HOME_DIR = "DFCLI_CLI_HOME_DIR";
    static final String DFCLI_CLI_ENV_EXCLUDE = "DFCLI_CLI_ENV_EXCLUDE";
    static final String DFCLI_CLI_BUILD_NAME = "DFCLI_CLI_BUILD_NAME";
    static final String DFCLI_CLI_BUILD_URL = "DFCLI_CLI_BUILD_URL";
    static final String HTTPS_PROXY_ENV = "HTTPS_PROXY";
    static final String HTTP_PROXY_ENV = "HTTP_PROXY";
    static final String NO_PROXY = "NO_PROXY";

    /**
     * Configure the DFCli CLI environment variables, according to the input job's env.
     *
     * @param env              - Job's environment variables
     * @param dfcliHomeTempDir - Calculated DFCli CLI home dir
     * @param encryptionKey    - Random encryption key to encrypt the CLI config
     */
    static void configureCliEnv(EnvVars env, String dfcliHomeTempDir, DFCliConfigEncryption encryptionKey) {
        // Setting Jenkins job name as the default build-info name
        env.putIfAbsent(DFCLI_CLI_BUILD_NAME, env.get("JOB_NAME"));
        // Setting Jenkins build number as the default build-info number
        env.putIfAbsent(DFCLI_CLI_BUILD_NUMBER, env.get("BUILD_NUMBER"));
        // Setting the specific build URL
        env.putIfAbsent(DFCLI_CLI_BUILD_URL, env.get("BUILD_URL"));
        // Set up a temporary Dfcli CLI home directory for a specific run
        env.put(DFCLI_CLI_HOME_DIR, dfcliHomeTempDir);
        if (StringUtils.isAllBlank(env.get(HTTP_PROXY_ENV), env.get(HTTPS_PROXY_ENV))) {
            // Set up HTTP/S proxy
            setupProxy(env);
        }
        if (encryptionKey.shouldEncrypt()) {
            // Set up a random encryption key to make sure no raw text secrets are stored in the file system
            env.putIfAbsent(DFCLI_CLI_ENCRYPTION_KEY, encryptionKey.getKey());
        }
    }

    @SuppressWarnings("HttpUrlsUsage")
    private static void setupProxy(EnvVars env) {
        JenkinsProxyConfiguration proxyConfiguration = new JenkinsProxyConfiguration();
        if (!proxyConfiguration.isProxyConfigured()) {
            return;
        }

        // Add HTTP or HTTPS protocol according to the port
        String proxyUrl = proxyConfiguration.getPort() == 443 ? "https://" : "http://";
        if (!StringUtils.isAnyBlank(proxyConfiguration.getUsername(), proxyConfiguration.getPassword())) {
            // Add username and password, if provided
            proxyUrl += proxyConfiguration.getUsername() + ":" + proxyConfiguration.getPassword() + "@";
            excludeProxyEnvFromPublishing(env);
        }
        proxyUrl += proxyConfiguration.getHost() + ":" + proxyConfiguration.getPort();
        env.put(HTTP_PROXY_ENV, proxyUrl);
        env.put(HTTPS_PROXY_ENV, proxyUrl);
        if (StringUtils.isNotBlank(proxyConfiguration.getNoProxy())) {
            env.put(NO_PROXY, createNoProxyValue(proxyConfiguration.getNoProxy()));
        }
    }

    /**
     * Exclude the HTTP_PROXY and HTTPS_PROXY environment variable from build-info if they contain credentials.
     *
     * @param env - Job's environment variables
     */
    private static void excludeProxyEnvFromPublishing(EnvVars env) {
        String dfCliEnvExclude = env.getOrDefault(DFCLI_CLI_ENV_EXCLUDE, DFCLI_CLI_DEFAULT_EXCLUSIONS);
        env.put(DFCLI_CLI_ENV_EXCLUDE, String.join(";", dfCliEnvExclude, HTTP_PROXY_ENV, HTTPS_PROXY_ENV));
    }

    /**
     * Converts a list of No Proxy Hosts received by Jenkins into a comma-separated string format expected by DFCli CLI.
     *
     * @param noProxy - A string representing the list of No Proxy Hosts.
     * @return A comma-separated string of No Proxy Hosts.
     */
    static String createNoProxyValue(String noProxy) {
        // Trim leading and trailing spaces, Replace '|' and ';' with spaces and normalize whitespace
        String noProxyListRemoveSpaceAndPipe = noProxy.trim().replaceAll("[\\s|;]+", ",");
        // Replace multiple commas with a single comma, and remove the last one if present
        return noProxyListRemoveSpaceAndPipe.replaceAll(",+", ",").replaceAll("^,|,$", "");
    }
}
