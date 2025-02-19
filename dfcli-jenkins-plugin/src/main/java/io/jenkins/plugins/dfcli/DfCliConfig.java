package io.jenkins.plugins.dfcli;

import hudson.Extension;
import hudson.util.Secret;
import jenkins.model.GlobalConfiguration;
import org.kohsuke.stapler.DataBoundSetter;

@Extension
public class DfCliConfig extends GlobalConfiguration {
    // STORE GLOBAL SERVER CONFIG
    private String serverUrl;
    private String username;
    private Secret password;
    private Secret token;

    public DfCliConfig() {
        load();
    }

    public String getServerUrl() {
        return serverUrl;
    }

    @DataBoundSetter
    public void setServerUrl(String url) {
        this.serverUrl = url;
        save();
    }

    public String getUsername() {
        return username;
    }

    @DataBoundSetter 
    public void setUsername(String username) {
        this.username = username;
        save();
    }

    public Secret getPassword() {
        return password;
    }

    @DataBoundSetter
    public void setPassword(Secret password) {
        this.password = password;
        save();
    }

    public Secret getToken() {
        return token;
    }

    @DataBoundSetter
    public void setToken(Secret token) {
        this.token = token;
        save();
    }

    // GET SINGLETON INSTANCE
    public static DfCliConfig get() {
        return GlobalConfiguration.all().get(DfCliConfig.class);
    }
}
