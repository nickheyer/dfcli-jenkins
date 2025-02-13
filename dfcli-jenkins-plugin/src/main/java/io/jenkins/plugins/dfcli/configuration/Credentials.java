package io.jenkins.plugins.dfcli.configuration;

import com.cloudbees.plugins.credentials.BaseCredentials;
import hudson.util.Secret;
import java.io.Serializable;
import org.apache.commons.lang3.StringUtils;

/**
 * Credentials model object
 */
public class Credentials extends BaseCredentials implements Serializable {
    public static final Secret EMPTY_SECRET = Secret.fromString(StringUtils.EMPTY);
    public static final Credentials EMPTY_CREDENTIALS = new Credentials(EMPTY_SECRET, EMPTY_SECRET, EMPTY_SECRET);
    private Secret username;
    private Secret password;
    private Secret accessToken;

    /**
     * Main constructor
     *
     * @param username    Secret username
     * @param password    Secret password.
     * @param accessToken Secret accessToken.
     */
    public Credentials(Secret username, Secret password, Secret accessToken) {
        this.username = username;
        this.password = password;
        this.accessToken = accessToken;
    }

    public Secret getUsername() {
        return username;
    }

    public Secret getPassword() {
        return password;
    }

    public Secret getAccessToken() {
        return accessToken;
    }

    public String getPlainTextUsername() {
        return Secret.toString(username);
    }

    public String getPlainTextPassword() {
        return Secret.toString(password);
    }

    public String getPlainTextAccessToken() {
        return Secret.toString(accessToken);
    }

    @SuppressWarnings("unused")
    public void setAccessToken(Secret accessToken) {
        this.accessToken = accessToken;
    }

    @SuppressWarnings("unused")
    public void setUsername(Secret username) {
        this.username = username;
    }

    @SuppressWarnings("unused")
    public void setPassword(Secret password) {
        this.password = password;
    }
}
