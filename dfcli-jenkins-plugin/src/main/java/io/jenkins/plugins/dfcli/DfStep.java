package io.jenkins.plugins.dfcli;

import static io.jenkins.plugins.dfcli.DfcliInstallation.DFCLI_BINARY_PATH;
import static org.apache.commons.lang3.StringUtils.*;
import static org.dfcli.build.extractor.BuildInfoExtractorUtils.createMapper;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import hudson.*;
import hudson.EnvVars;
import hudson.Launcher;
import hudson.model.Job;
import hudson.model.Run;
import hudson.model.TaskListener;
import hudson.util.ArgumentListBuilder;
import io.jenkins.plugins.dfcli.actions.BuildInfoBuildBadgeAction;
import io.jenkins.plugins.dfcli.actions.DFCliConfigEncryption;
import io.jenkins.plugins.dfcli.configuration.Credentials;
import io.jenkins.plugins.dfcli.configuration.DFCliPlatformBuilder;
import io.jenkins.plugins.dfcli.configuration.DFCliPlatformInstance;
import io.jenkins.plugins.dfcli.models.BuildInfoOutputModel;
import io.jenkins.plugins.dfcli.plugins.PluginsUtils;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Paths;
import java.util.List;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.regex.Pattern;
import javax.annotation.Nonnull;
import lombok.Getter;
import org.apache.commons.io.FilenameUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.exception.ExceptionUtils;
import org.dfcli.build.api.util.Log;
import org.dfcli.build.client.Version;
import org.jenkinsci.plugins.plaincredentials.StringCredentials;
import org.jenkinsci.plugins.workflow.steps.*;
import org.jenkinsci.plugins.workflow.steps.Step;
import org.jenkinsci.plugins.workflow.steps.StepContext;
import org.jenkinsci.plugins.workflow.steps.StepExecution;
import org.jenkinsci.plugins.workflow.steps.SynchronousNonBlockingStepExecution;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.DataBoundSetter;

@Getter
@SuppressWarnings("unused")
public class DfStep extends Step {
    private static final Pattern VALID_CMD_PATTERN = Pattern.compile("^[a-zA-Z0-9\\-_\\s]+$");
    private static final Pattern VALID_ARGS_PATTERN = Pattern.compile("^[\\w\\-\\.\\s/=:@]+$");
    private static final long DEFAULT_TIMEOUT = TimeUnit.MINUTES.toMillis(10);
    private static final long MAX_TIMEOUT = TimeUnit.HOURS.toMillis(24);

    private static final ObjectMapper mapper = createMapper();
    protected String[] args;
    private long timeout = DEFAULT_TIMEOUT;
    private boolean failFast = true;
    private boolean debugMode = false;
    static final Version MIN_CLI_VERSION_PASSWORD_STDIN = new Version("2.31.3");

    @DataBoundConstructor
    public DfStep(Object args) {
        if (args instanceof List) {
            this.args = ((List<String>) args).toArray(String[]::new);
        } else {
            this.args = split(args.toString());
        }
        validateArgs(this.args);
    }

    // TIMEOUT
    @DataBoundSetter
    public void setTimeout(long timeout) {
        this.timeout = Math.min(timeout, MAX_TIMEOUT);
    }

    // FAIL FAST
    @DataBoundSetter
    public void setFailFast(boolean failFast) {
        this.failFast = failFast;
    }

    // DEBUG MODE
    @DataBoundSetter
    public void setDebugMode(boolean debugMode) {
        this.debugMode = debugMode;
    }

    private void validateArgs(String[] args) {
        if (args == null || args.length == 0) {
            throw new IllegalArgumentException("No command provided");
        }

        // VALIDATE COMMAND
        if (!VALID_CMD_PATTERN.matcher(args[0]).matches()) {
            throw new IllegalArgumentException("Invalid command format: " + args[0]);
        }

        // VALIDATE ARGS
        for (int i = 1; i < args.length; i++) {
            if (!VALID_ARGS_PATTERN.matcher(args[i]).matches()) {
                throw new IllegalArgumentException("Invalid argument format: " + args[i]);
            }
        }
    }

    /**
     * Retrieves the version of the DFCli CLI.
     *
     * @param launcher        The process launcher used to execute the DFCli CLI command.
     * @param dfcliBinaryPath The path to the DFCli CLI binary.
     * @return The version of the DFCli CLI.
     * @throws IOException          If an I/O error occurs while executing the command or reading the output.
     * @throws InterruptedException If the process is interrupted while waiting for the command to complete.
     */
    public static Version getDfCliVersion(Launcher.ProcStarter launcher, String dfcliBinaryPath)
            throws IOException, InterruptedException {
        try (ByteArrayOutputStream outputStream = new ByteArrayOutputStream()) {
            ArgumentListBuilder builder = new ArgumentListBuilder();
            builder.add(dfcliBinaryPath).add("version");
            int exitCode = launcher.cmds(builder)
                    .pwd(launcher.pwd())
                    .stdout(outputStream)
                    .join();
            if (exitCode != 0) {
                throw new IOException(
                        "Failed to get DFCli CLI version: " + outputStream.toString(StandardCharsets.UTF_8));
            }
            String versionOutput = outputStream.toString(StandardCharsets.UTF_8).trim();
            String version = StringUtils.substringAfterLast(versionOutput, " ");
            return new Version(version);
        }
    }

    @Override
    public StepExecution start(StepContext context) {
        return new Execution(args, timeout, failFast, debugMode, context);
    }

    public static class Execution extends SynchronousNonBlockingStepExecution<String> {
        private final String[] args;
        private final long timeout;
        private final boolean failFast;
        private final boolean debugMode;

        protected Execution(String[] args, long timeout, boolean failFast, boolean debugMode, StepContext context) {
            super(context);
            this.args = args;
            this.timeout = timeout;
            this.failFast = failFast;
            this.debugMode = debugMode;
        }

        @Override
        protected String run() throws Exception {
            Launcher launcher = getContext().get(Launcher.class);
            FilePath workspace = getContext().get(FilePath.class);
            TaskListener listener = getContext().get(TaskListener.class);
            EnvVars env = getContext().get(EnvVars.class);
            Run<?, ?> run = getContext().get(Run.class);

            if (debugMode) {
                listener.getLogger().println("Running dfcli command with args: " + String.join(" ", args));
                listener.getLogger().println("Environment variables: " + env);
            }
            workspace.mkdirs();
            // Build the 'df' command
            ArgumentListBuilder builder = new ArgumentListBuilder();
            boolean isWindows = !launcher.isUnix();
            String dfcliBinaryPath = getDFCliPath(env, isWindows);
            boolean passwordStdinSupported = isPasswordStdinSupported(workspace, env, launcher, dfcliBinaryPath);

            builder.add(dfcliBinaryPath).add(args);
            if (isWindows) {
                builder = builder.toWindowsCommand();
            }

            String output;
            try (ByteArrayOutputStream taskOutputStream = new ByteArrayOutputStream()) {
                DfTaskListener dfTaskListener = new DfTaskListener(listener);
                Launcher.ProcStarter dfLauncher = setupDFCliEnvironment(
                        run,
                        env,
                        launcher,
                        dfTaskListener,
                        workspace,
                        dfcliBinaryPath,
                        isWindows,
                        passwordStdinSupported);
                // Running the 'df' command
                int exitValue = dfLauncher.cmds(builder).join();
                output = taskOutputStream.toString(StandardCharsets.UTF_8);
                if (exitValue != 0) {
                    throw new RuntimeException("Running 'df' command failed with exit code " + exitValue);
                }
                addBuildInfoActionIfNeeded(args, new JenkinsBuildInfoLog(listener), run, taskOutputStream);
            } catch (Exception e) {
                String errorMessage = "Couldn't execute 'df' command. " + ExceptionUtils.getRootCauseMessage(e);
                throw new RuntimeException(errorMessage, e);
            }
            return output;
        }

        /**
         * Get DFCli CLI path in agent, according to the DFCLI_BINARY_PATH environment variable.
         * The DFCLI_BINARY_PATH also can be set implicitly in Declarative Pipeline by choosing the DFCli CLI tool or
         * explicitly in Scripted Pipeline.
         *
         * @param env       - Job's environment variables
         * @param isWindows - True if the agent's OS is windows
         * @return DFCli CLI path in agent.
         */
        static String getDFCliPath(EnvVars env, boolean isWindows) {
            // DFCLI_BINARY_PATH is set according to the master OS. If not configured, the value of dfcliBinaryPath will
            // eventually be 'df' or 'df.exe'. In that case, the DFCli CLI from the system path is used.
            String dfcliBinaryPath = Paths.get(env.get(DFCLI_BINARY_PATH, ""), Utils.getDfCliBinaryName(isWindows))
                    .toString();

            // Modify dfcliBinaryPath according to the agent's OS
            return isWindows
                    ? FilenameUtils.separatorsToWindows(dfcliBinaryPath)
                    : FilenameUtils.separatorsToUnix(dfcliBinaryPath);
        }

        /**
         * Log if the DFCli CLI binary path doesn't exist in job's environment variable.
         * This environment variable exists in one of the following scenarios:
         * 1. Declarative Pipeline: A 'dfcli' tool was set
         * 2. Scripted Pipeline: Using the "withEnv(["DFCLI_BINARY_PATH=${tool 'dfcli-cli'}"])" syntax
         *
         * @param env      - Job's environment variables
         * @param listener - Job's logger
         */
        private void logIfNoToolProvided(EnvVars env, TaskListener listener) {
            if (env.containsKey(DFCLI_BINARY_PATH)) {
                return;
            }
            JenkinsBuildInfoLog buildInfoLog = new JenkinsBuildInfoLog(listener);
            buildInfoLog.info("A 'dfcli' tool was not set. Using DFCli CLI from the system path.");
        }

        /**
         * Configure all DFCli relevant environment variables and all servers (if they haven't been configured yet).
         *
         * @param run                    running as part of a specific build
         * @param env                    environment variables applicable to this step
         * @param launcher               a way to start processes
         * @param listener               a place to send output
         * @param workspace              a workspace to use for any file operations
         * @param dfcliBinaryPath        path to dfcli cli binary on the filesystem
         * @param isWindows              is Windows the applicable OS
         * @param passwordStdinSupported indicates if the password can be securely passed via stdin to the CLI
         * @return launcher applicable to this step.
         * @throws InterruptedException if the step is interrupted
         * @throws IOException          in case of any I/O error, or we failed to run the 'df' command
         */
        public Launcher.ProcStarter setupDFCliEnvironment(
                Run<?, ?> run,
                EnvVars env,
                Launcher launcher,
                TaskListener listener,
                FilePath workspace,
                String dfcliBinaryPath,
                boolean isWindows,
                boolean passwordStdinSupported)
                throws IOException, InterruptedException {
            DFCliConfigEncryption dfCliConfigEncryption = run.getAction(DFCliConfigEncryption.class);
            if (dfCliConfigEncryption == null) {
                // Set up the config encryption action to allow encrypting the DFCli CLI configuration and make sure we
                // only create one key
                dfCliConfigEncryption = new DFCliConfigEncryption(env);
                run.addAction(dfCliConfigEncryption);
            }
            FilePath dfcliHomeTempDir = Utils.createAndGetDfCliHomeTempDir(workspace, String.valueOf(run.getNumber()));
            CliEnvConfigurator.configureCliEnv(env, dfcliHomeTempDir.getRemote(), dfCliConfigEncryption);
            Launcher.ProcStarter dfLauncher =
                    launcher.launch().envs(env).pwd(workspace).stdout(listener);
            // Configure all servers, skip if all server ids have already been configured.
            if (shouldConfig(dfcliHomeTempDir)) {
                logIfNoToolProvided(env, listener);
                configAllServers(dfLauncher, dfcliBinaryPath, isWindows, run.getParent(), passwordStdinSupported);
            }
            return dfLauncher;
        }

        /**
         * Determines if the password can be securely passed via stdin to the CLI,
         * rather than using the --password flag. This depends on two factors:
         * 1. The DFCli CLI version on the agent (minimum supported version is 2.31.3).
         * 2. Whether the launcher is a custom (plugin) launcher.
         * <p>
         * Note: The primary reason for this limitation is that Docker plugin which is widely used
         * does not support stdin input, because it is a custom launcher.
         *
         * @param workspace The workspace file path.
         * @param env       The environment variables.
         * @param launcher  The command launcher.
         * @return true if stdin-based password handling is supported; false otherwise.
         */
        public boolean isPasswordStdinSupported(
                FilePath workspace, EnvVars env, Launcher launcher, String dfcliBinaryPath)
                throws IOException, InterruptedException {
            TaskListener listener = getContext().get(TaskListener.class);
            JenkinsBuildInfoLog buildInfoLog = new JenkinsBuildInfoLog(listener);

            boolean isPluginLauncher = launcher.getClass().getName().contains("org.jenkinsci.plugins");
            if (isPluginLauncher) {
                buildInfoLog.debug("Password stdin is not supported,Launcher is a plugin launcher.");
                return false;
            }
            Launcher.ProcStarter procStarter = launcher.launch().envs(env).pwd(workspace);
            Version currentCliVersion = getDfCliVersion(procStarter, dfcliBinaryPath);
            buildInfoLog.debug("Password stdin is supported");
            return currentCliVersion.isAtLeast(MIN_CLI_VERSION_PASSWORD_STDIN);
        }

        /**
         * Before we run a 'df' command for the first time, we want to configure all servers first.
         * We know that all servers have already been configured if there is a "dfcli-cli.conf" file in the ".dfcli" home directory.
         *
         * @param dfcliHomeTempDir - The temp ".dfcli" directory path.
         */
        private boolean shouldConfig(FilePath dfcliHomeTempDir) throws IOException, InterruptedException {
            List<FilePath> filesList = dfcliHomeTempDir.list();
            for (FilePath file : filesList) {
                if (file.getName().contains("dfcli-cli.conf")) {
                    return false;
                }
            }
            return true;
        }

        /**
         * Locally configure all servers that was configured in the Jenkins UI.
         */
        private void configAllServers(
                Launcher.ProcStarter launcher,
                String dfcliBinaryPath,
                boolean isWindows,
                Job<?, ?> job,
                boolean passwordStdinSupported)
                throws IOException, InterruptedException {
            // Config all servers using the 'df c add' command.
            List<DFCliPlatformInstance> dfcliInstances = DFCliPlatformBuilder.getDFCliPlatformInstances();
            if (dfcliInstances != null && !dfcliInstances.isEmpty()) {
                for (DFCliPlatformInstance dfcliPlatformInstance : dfcliInstances) {
                    // Build 'df' command
                    ArgumentListBuilder builder = new ArgumentListBuilder();
                    addConfigArguments(
                            builder,
                            dfcliPlatformInstance,
                            job,
                            dfcliBinaryPath,
                            job,
                            launcher,
                            passwordStdinSupported);
                    if (isWindows) {
                        builder = builder.toWindowsCommand();
                    }
                    // Running 'df' command
                    int exitValue = launcher.cmds(builder).join();
                    if (exitValue != 0) {
                        throw new RuntimeException("Running 'df' command failed with exit code " + exitValue);
                    }
                }
            }
        }

        private void addConfigArguments(
                ArgumentListBuilder builder,
                DFCliPlatformInstance dfcliPlatformInstance,
                Job<?, ?> job1,
                String dfcliBinaryPath,
                Job<?, ?> job,
                Launcher.ProcStarter launcher,
                boolean passwordStdinSupported) {
            builder.add(dfcliBinaryPath).add("c").add("add").add(dfcliPlatformInstance.getId());
            addCredentialsArguments(builder, dfcliPlatformInstance, job, launcher, passwordStdinSupported);
            addUrlArguments(builder, dfcliPlatformInstance);
            builder.add("--interactive=false").add("--overwrite=true");
        }
    }

    static void addCredentialsArguments(
            ArgumentListBuilder builder,
            DFCliPlatformInstance dfcliPlatformInstance,
            Job<?, ?> job,
            Launcher.ProcStarter launcher,
            boolean passwordStdinSupported) {
        String credentialsId = dfcliPlatformInstance.getCredentialsConfig().getCredentialsId();
        StringCredentials accessTokenCredentials = PluginsUtils.accessTokenCredentialsLookup(credentialsId, job);

        if (accessTokenCredentials != null) {
            builder.addMasked(
                    "--access-token=" + accessTokenCredentials.getSecret().getPlainText());
        } else {
            Credentials credentials = PluginsUtils.credentialsLookup(credentialsId, job);
            builder.add("--user=" + credentials.getUsername());
            addPasswordArgument(builder, credentials, launcher, passwordStdinSupported);
        }
    }

    /**
     * Configures the CLI command to provide a password via stdin if supported; otherwise,
     * uses the default `--password` argument. Stdin support requires a minimum CLI version
     * and does not work with certain plugin launchers, as they may lose stdin input,
     * potentially causing command failures.
     * <p>
     * This method ensures compatibility by switching to the default password argument when
     * stdin is unsupported or unsuitable for the launcher being used.
     *
     * @param builder                The {@link ArgumentListBuilder} used to construct the CLI command arguments.
     * @param credentials            The {@link Credentials} object containing the user's password.
     * @param launcher               The {@link Launcher.ProcStarter} used to execute the command.
     * @param passwordStdinSupported A boolean flag indicating whether the CLI supports password input via stdin.
     */
    private static void addPasswordArgument(
            ArgumentListBuilder builder,
            Credentials credentials,
            Launcher.ProcStarter launcher,
            boolean passwordStdinSupported) {
        if (passwordStdinSupported) {
            // Add argument to read password from stdin
            builder.add("--password-stdin");
            ByteArrayInputStream inputStream = new ByteArrayInputStream(
                    credentials.getPassword().getPlainText().getBytes(StandardCharsets.UTF_8));
            launcher.stdin(inputStream);
        } else {
            // Add masked password argument directly to the command
            builder.addMasked("--password=" + credentials.getPassword());
        }
    }

    private static void addUrlArguments(ArgumentListBuilder builder, DFCliPlatformInstance dfcliPlatformInstance) {
        builder.add("--url=" + dfcliPlatformInstance.getUrl());
        builder.add("--distroface-url=" + dfcliPlatformInstance.inferDistrofaceUrl());
        builder.add("--distribution-url=" + dfcliPlatformInstance.inferDistributionUrl());
        builder.add("--xray-url=" + dfcliPlatformInstance.inferXrayUrl());
    }

    /**
     * Add build-info Action if the command is 'df rt bp' or 'df rt build-publish'.
     *
     * @param log              - Task logger
     * @param run              - The Jenkins project
     * @param taskOutputStream - Task's output stream
     */
    static void addBuildInfoActionIfNeeded(
            String[] args, Log log, Run<?, ?> run, ByteArrayOutputStream taskOutputStream) {
        if (args.length < 2 || !args[0].equals("rt") || !equalsAny(args[1], "bp", "build-publish")) {
            return;
        }

        // Search for '{' and '}' in the output of 'df rt build-publish'
        String taskOutput = taskOutputStream.toString(StandardCharsets.UTF_8);
        taskOutput = substringBetween(taskOutput, "{", "}");
        if (taskOutput == null) {
            logIllegalBuildPublishOutput(log, taskOutputStream);
            return;
        }

        // Parse the output into BuildInfoOutputModel to extract the build-info URL
        BuildInfoOutputModel buildInfoOutputModel;
        try {
            buildInfoOutputModel = mapper.readValue("{" + taskOutput + "}", BuildInfoOutputModel.class);
            if (buildInfoOutputModel == null) {
                logIllegalBuildPublishOutput(log, taskOutputStream);
                return;
            }
        } catch (JsonProcessingException e) {
            logIllegalBuildPublishOutput(log, taskOutputStream);
            log.warn(ExceptionUtils.getRootCauseMessage(e));
            return;
        }
        String buildInfoUrl = buildInfoOutputModel.getBuildInfoUiUrl();

        // Add the BuildInfoBuildBadgeAction action into the job to show the build-info button
        if (isNotBlank(buildInfoUrl)) {
            run.addAction(new BuildInfoBuildBadgeAction(buildInfoUrl));
        }
    }

    private static void logIllegalBuildPublishOutput(Log log, ByteArrayOutputStream taskOutputStream) {
        log.warn("Illegal build-publish output: " + taskOutputStream.toString(StandardCharsets.UTF_8));
    }

    @Extension
    public static final class DescriptorImpl extends StepDescriptor {
        @Override
        public String getFunctionName() {
            return "df";
        }

        @Nonnull
        @Override
        public String getDisplayName() {
            return "df command";
        }

        @Override
        public Set<? extends Class<?>> getRequiredContext() {
            return Set.of(Launcher.class, FilePath.class, TaskListener.class, EnvVars.class);
        }
    }
}
