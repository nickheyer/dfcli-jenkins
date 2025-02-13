package io.jenkins.plugins.dfcli;

import hudson.model.TaskListener;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.dfcli.build.api.util.Log;

/**
 * Wrapper for Jenkins build logger, records log messages from BuildInfo
 */
public class JenkinsBuildInfoLog implements Log {
    private static final Logger logger = Logger.getLogger(JenkinsBuildInfoLog.class.getName());

    private final TaskListener listener;

    public JenkinsBuildInfoLog(TaskListener listener) {
        this.listener = listener;
    }

    public void debug(String message) {
        logger.finest(message);
    }

    public void info(String message) {
        listener.getLogger().println(message);
        logger.info(message);
    }

    public void warn(String message) {
        listener.getLogger().println(message);
        logger.warning(message);
    }

    public void error(String message) {
        listener.getLogger().println(message);
        logger.severe(message);
    }

    public void error(String message, Throwable e) {
        listener.getLogger().println(message);
        logger.log(Level.SEVERE, message, e);
    }
}
