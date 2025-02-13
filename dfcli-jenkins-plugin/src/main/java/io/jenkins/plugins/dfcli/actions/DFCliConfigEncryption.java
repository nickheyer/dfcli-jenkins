package io.jenkins.plugins.dfcli.actions;

import static io.jenkins.plugins.dfcli.CliEnvConfigurator.DFCLI_CLI_HOME_DIR;

import hudson.EnvVars;
import hudson.model.Action;
import java.util.UUID;

/**
 * This action is injected to the DfStep in order to generate a random key that encrypts the DFCli CLI config.
 *
 * @author yahavi
 **/
public class DFCliConfigEncryption implements Action {
    private boolean shouldEncrypt;
    private String key;

    public DFCliConfigEncryption(EnvVars env) {
        if (env.containsKey(DFCLI_CLI_HOME_DIR)) {
            // If DFCLI_CLI_HOME_DIR exists, we assume that the user uses a permanent DFCli CLI configuration.
            // This type of configuration can not be encrypted because 2 different tasks may encrypt with 2 different
            // keys.
            return;
        }
        this.shouldEncrypt = true;
        // UUID is a cryptographically strong encryption key. Without the dashes, it contains exactly 32 characters.
        this.key = UUID.randomUUID().toString().replaceAll("-", "");
    }

    public String getKey() {
        return key;
    }

    public boolean shouldEncrypt() {
        return shouldEncrypt;
    }

    @Override
    public String getIconFileName() {
        return null;
    }

    @Override
    public String getDisplayName() {
        return "DFCli CLI config encryption";
    }

    @Override
    public String getUrlName() {
        return null;
    }
}
