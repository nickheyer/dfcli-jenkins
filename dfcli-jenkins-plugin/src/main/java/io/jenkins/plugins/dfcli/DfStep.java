package io.jenkins.plugins.dfcli;

import hudson.EnvVars;
import hudson.Extension;
import hudson.FilePath;
import hudson.Launcher;
import hudson.model.TaskListener;
import hudson.util.ArgumentListBuilder;
import hudson.util.Secret;
import org.jenkinsci.plugins.workflow.steps.Step;
import org.jenkinsci.plugins.workflow.steps.StepContext;
import org.jenkinsci.plugins.workflow.steps.StepDescriptor;
import org.jenkinsci.plugins.workflow.steps.StepExecution;
import org.jenkinsci.plugins.workflow.steps.SynchronousNonBlockingStepExecution;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.Serializable;
import java.nio.charset.StandardCharsets;
import java.util.Set;
import java.util.Map;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.DataBoundSetter;

public class DfStep extends Step implements Serializable {
    private static final long serialVersionUID = 1L;
    
    private final String command;
    private String version;
    private Map<String, String> properties;
    private String cacheKey;
    private boolean createCacheKey;

    @DataBoundConstructor
    public DfStep(String command) {
        this.command = command;
    }

    public String getCommand() { return command; }
    public String getVersion() { return version; }
    public Map<String, String> getProperties() { return properties; }
    public String getCacheKey() { return cacheKey; }
    public boolean isCreateCacheKey() { return createCacheKey; }

    @DataBoundSetter
    public void setVersion(String version) {
        this.version = version;
    }

    @DataBoundSetter
    public void setProperties(Map<String, String> properties) {
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
    public StepExecution start(StepContext context) {
        return new Execution(this, context);
    }

    private static class Execution extends SynchronousNonBlockingStepExecution<String> implements Serializable {
        private static final long serialVersionUID = 1L;
        private final transient String command;
        private final transient String version;
        private final transient Map<String, String> properties;
        private final transient String cacheKey;
        private final transient boolean createCacheKey;

        protected Execution(DfStep step, StepContext context) {
            super(context);
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

            // HANDLE CACHE KEY GENERATION
            if (createCacheKey) {
                String key = generateCacheKey();
                if (key != null) {
                    listener.getLogger().println("[DFCli] Generated cache key: " + key);
                }
                return key;
            }

            // BUILD COMMAND
            ArgumentListBuilder cmd = new ArgumentListBuilder();
            cmd.add(env.get(DfCliInstallation.DFCLI_BINARY, "dfcli"));

            // SERVER CONFIG
            DfCliConfig config = DfCliConfig.get();
            if (config != null && config.getServerUrl() != null) {
                if (command.startsWith("login")) {
                    cmd.add("--server", config.getServerUrl());
                }
                
                if (config.getUsername() != null && config.getPassword() != null) {
                    env.put("DFCLI_USER", config.getUsername());
                    env.put("DFCLI_PASS", Secret.toString(config.getPassword()));
                }
            }

            // ADD COMMAND
            if (command != null && !command.isEmpty()) {
                for (String arg : command.split("(?=(?:[^\"]*\"[^\"]*\")*[^\"]*$)\\s+")) {
                    if (arg.startsWith("\"") && arg.endsWith("\"")) {
                        arg = arg.substring(1, arg.length() - 1);
                    }
                    cmd.add(arg);
                }
            }

            // ADD CACHE KEY AS-IS IF PROVIDED
            if (cacheKey != null && !cacheKey.isEmpty()) {
                cmd.addTokenized(cacheKey);
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

            String stdoutStr = stdout.toString(StandardCharsets.UTF_8);
            String stderrStr = stderr.toString(StandardCharsets.UTF_8);

            if (!stdoutStr.isEmpty()) {
                for (String line : stdoutStr.split("\n")) {
                    listener.getLogger().println("[DFCli] " + line);
                }
            }

            if (!stderrStr.isEmpty()) {
                for (String line : stderrStr.split("\n")) {
                    listener.error("[DFCli] " + line);
                }
            }

            if (exitCode != 0) {
                throw new IOException("[DFCli] Process failed with exit code " + exitCode);
            }

            return stdoutStr.trim();
        }

        private String generateCacheKey() {
            if (version == null) {
                return null;
            }

            StringBuilder key = new StringBuilder("--version ").append(version);
            
            if (properties != null && !properties.isEmpty()) {
                key.append(" --property ");
                StringBuilder props = new StringBuilder();
                properties.forEach((k, v) -> props.append(k).append("=").append(v).append(","));
                props.setLength(props.length() - 1); // REMOVE LAST COMMA
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
            return "Execute DFCli Command";
        }

        @Override
        public Set<? extends Class<?>> getRequiredContext() {
            return Set.of(TaskListener.class, Launcher.class, EnvVars.class, FilePath.class);
        }
    }
}
