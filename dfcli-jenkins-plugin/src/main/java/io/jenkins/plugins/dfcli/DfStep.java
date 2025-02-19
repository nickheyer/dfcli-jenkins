package io.jenkins.plugins.dfcli;

import hudson.EnvVars;
import hudson.Extension;
import hudson.FilePath;
import hudson.Launcher;
import hudson.model.Node;
import hudson.model.TaskListener;
import hudson.util.ArgumentListBuilder;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.Serializable;
import java.util.Map;
import java.util.Set;
import org.jenkinsci.plugins.workflow.steps.Step;
import org.jenkinsci.plugins.workflow.steps.StepContext;
import org.jenkinsci.plugins.workflow.steps.StepDescriptor;
import org.jenkinsci.plugins.workflow.steps.StepExecution;
import org.jenkinsci.plugins.workflow.steps.SynchronousNonBlockingStepExecution;
import jenkins.model.Jenkins;
import java.io.File;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.DataBoundSetter;

public class DfStep extends Step implements Serializable {
    private static final long serialVersionUID = 1L;

    private final String command;
    private String version;
    private Map<String,String> properties;
    private String cacheKey;
    private boolean createCacheKey;

    @DataBoundConstructor
    public DfStep(String command) {
        this.command = command;
    }

    public String getCommand() {
        return command;
    }

    public String getVersion() {
        return version;
    }

    public Map<String, String> getProperties() {
        return properties;
    }

    public String getCacheKey() {
        return cacheKey;
    }

    public boolean isCreateCacheKey() {
        return createCacheKey;
    }

    @DataBoundSetter
    public void setVersion(String version) {
        this.version = version;
    }

    @DataBoundSetter
    public void setProperties(Map<String,String> properties) {
        this.properties = properties;
    }

    @DataBoundSetter
    public void setCacheKey(String cacheKey) {
        this.cacheKey = cacheKey;
    }

    @DataBoundSetter
    public void setCreateCacheKey(boolean createCacheKey) {
        this.createCacheKey = createCacheKey;
    }

    @Override
    public StepExecution start(StepContext context) throws Exception {
        return new Execution(this, context);
    }

    private static class Execution extends SynchronousNonBlockingStepExecution<String> {
        private static final long serialVersionUID = 1L;

        private final transient String command;
        private final transient String version;
        private final transient Map<String, String> properties;
        private final transient String cacheKey;
        private final transient boolean createCacheKey;

        protected Execution(DfStep step, StepContext ctx) {
            super(ctx);
            this.command = step.getCommand();
            this.version = step.getVersion();
            this.properties = step.getProperties();
            this.cacheKey = step.getCacheKey();
            this.createCacheKey = step.isCreateCacheKey();
        }

        @Override
        protected String run() throws Exception {
            TaskListener listener = getContext().get(TaskListener.class);
            Launcher launcher = getContext().get(Launcher.class);
            EnvVars env = getContext().get(EnvVars.class);
            FilePath workspace = getContext().get(FilePath.class);
            Node node = getContext().get(Node.class);

            // IF CREATE CACHE KEY, MAKE AND RETURN ONLY
            if (createCacheKey) {
                String key = generateCacheKey();
                if (key != null) {
                    listener.getLogger().println("Generated cache key: " + key);
                }
                return key;
            }
            if (command == null || command.trim().isEmpty()) {
                listener.getLogger().println("No dfcli command specified.");
                return "";
            }
            DfCliInstallation[] installations = Jenkins.get()
                .getDescriptorByType(DfCliInstallation.DescriptorImpl.class)
                .getInstallations();
            if (installations == null || installations.length == 0) {
                throw new IOException("No DfCliInstallation configured in Jenkins global tools.");
            }
            DfCliInstallation tool = installations[0].forNode(node, listener).forEnvironment(env);

            // BUILD CMD
            ArgumentListBuilder cmd = new ArgumentListBuilder();
            // HANDLE DOCKER VS REGULAR AGENTS
            boolean isDockerAgent = isDockerAgent(launcher);
            if (isDockerAgent) {
                // FOR DOCKER, JUST USE THE BINARY NAME
                cmd.add("dfcli");
            } else {
              String exePath = launcher.isUnix()
                      ? tool.getHome() + "/dfcli"
                      : tool.getHome() + "\\dfcli.exe";
              cmd.add(exePath);
            }

            for (String arg : command.split("\\s+")) {
                cmd.add(arg);
            }
            if (cacheKey != null && !cacheKey.isEmpty()) {
                StringBuilder keyBuf = new StringBuilder(cacheKey);
                if (properties != null && !properties.isEmpty()) {
                    keyBuf.append(" --property ");
                    StringBuilder propsBuf = new StringBuilder();
                    properties.forEach((k,v) -> propsBuf.append(k).append("=").append(v).append(","));

                    propsBuf.setLength(propsBuf.length() - 1);
                    keyBuf.append(propsBuf);
                }
                cmd.addTokenized(keyBuf.toString());
            } else {
                String genKey = generateCacheKey();
                if (genKey != null && !genKey.isEmpty()) {
                    cmd.addTokenized(genKey);
                }
            }

            // UPDATE PATH FOR DOCKER AGENTS
            if (isDockerAgent) {
              String path = env.get("PATH", "");
              path = tool.getHome() + File.pathSeparator + path;
              env.put("PATH", path);
              listener.getLogger().println("Updated PATH to include dfcli directory: " + path);
            }
            ByteArrayOutputStream stdout = new ByteArrayOutputStream();
            ByteArrayOutputStream stderr = new ByteArrayOutputStream();

            int exitCode = launcher.launch()
                .cmds(cmd)
                .envs(env)
                .pwd(workspace)
                .stdout(stdout)
                .stderr(stderr)
                .join();

            String outStr = stdout.toString("UTF-8").trim();
            String errStr = stderr.toString("UTF-8").trim();

            if (!outStr.isEmpty()) {
                for (String line : outStr.split("\n")) {
                    listener.getLogger().println(line);
                }
            }
            if (!errStr.isEmpty()) {
                listener.error(errStr);
            }

            if (exitCode != 0) {
                throw new IOException("dfcli command failed with exit code " + exitCode);
            }

            return outStr;
        }

        private boolean isDockerAgent(Launcher launcher) {
          return launcher.toString().toLowerCase().contains("docker");
        }

        private String generateCacheKey() {
            if (version == null || version.trim().isEmpty()) {
                return null;
            }

            // EX: "--version 1.0.0 --property KEY1=VAL1,KEY2=VAL2"
            StringBuilder key = new StringBuilder("--version ").append(version);

            if (properties != null && !properties.isEmpty()) {
                key.append(" --property ");
                StringBuilder props = new StringBuilder();
                properties.forEach((k,v) -> props.append(k).append("=").append(v).append(","));
                props.setLength(props.length() - 1);
                key.append(props);
            }
            return key.toString();
        }
    }

    @Extension
    public static class DescriptorImpl extends StepDescriptor {
        @Override
        public String getFunctionName() {
            return "df";
        }

        @Override
        public String getDisplayName() {
            return "Run dfcli Command (Auto-Login, Supports Cache Key)";
        }

        @Override
        public Set<? extends Class<?>> getRequiredContext() {
            return Set.of(TaskListener.class, Launcher.class, EnvVars.class, FilePath.class, Node.class);
        }
    }
}
