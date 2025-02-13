package org.dfcli.build.client;

public class ProxyConfiguration {
    protected String host;
    protected int port;
    protected String username;
    protected String password;
    protected String noProxy;
    
    public String getHost() {
        return host;
    }

    public void setHost(String host) {
        this.host = host;
    }

    public int getPort() {
        return port;
    }

    public void setPort(int port) {
        this.port = port;
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public String getPassword() {
        return password;
    }

    public void setPassword(String password) {
        this.password = password;
    }

    public String getNoProxy() {
        return noProxy;
    }

    public void setNoProxy(String noProxy) {
        this.noProxy = noProxy;
    }
    
    public void setProxyConfiguration(ProxyConfiguration configuration) {
        this.host = configuration.getHost();
        this.port = configuration.getPort();
        this.username = configuration.getUsername();
        this.password = configuration.getPassword();
        this.noProxy = configuration.getNoProxy();
    }
}
