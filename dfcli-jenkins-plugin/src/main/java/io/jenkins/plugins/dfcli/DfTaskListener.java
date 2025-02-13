package io.jenkins.plugins.dfcli;

import hudson.model.TaskListener;
import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import org.apache.commons.io.output.TeeOutputStream;

public class DfTaskListener implements TaskListener {
    private final TaskListener delegate;
    private final ByteArrayOutputStream outputStream;
    private final PrintStream logger;

    public DfTaskListener(TaskListener delegate) {
        this.delegate = delegate;
        this.outputStream = new ByteArrayOutputStream();
        this.logger = new PrintStream(outputStream);
    }

    @Override
    public PrintStream getLogger() {
        return new PrintStream(new TeeOutputStream(delegate.getLogger(), outputStream));
    }

    public String getOutput() {
        return outputStream.toString(StandardCharsets.UTF_8);
    }
}
