package io.jenkins.plugins.dfcli;

import java.io.Serializable;

public class DfTaskResult implements Serializable {
    private static final long serialVersionUID = 1L;

    private final DfTask task;
    private final String output;
    private final long duration;
    private final Exception error;

    public DfTaskResult(DfTask task, String output, long duration, Exception error) {
        this.task = task;
        this.output = output;
        this.duration = duration;
        this.error = error;
    }

    public DfTask getTask() {
        return task;
    }

    public String getOutput() {
        return output;
    }

    public long getDuration() {
        return duration;
    }

    public Exception getError() {
        return error;
    }

    public boolean isSuccess() {
        return error == null;
    }
}
