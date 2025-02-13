package io.jenkins.plugins.dfcli;

import hudson.model.TaskListener;
import java.util.List;
import java.util.concurrent.*;
import java.util.stream.Collectors;

public class ParallelDfExecutor {
    private final ExecutorService executor;
    private final TaskListener listener;
    private final int maxConcurrent;

    public ParallelDfExecutor(TaskListener listener, int maxConcurrent) {
        this.listener = listener;
        this.maxConcurrent = maxConcurrent;
        this.executor = Executors.newFixedThreadPool(maxConcurrent);
    }

    public List<DfTaskResult> executeParallel(List<DfTask> tasks) {
        try {
            List<Future<DfTaskResult>> futures = tasks.stream()
                    .map(task -> executor.submit(() -> executeTask(task)))
                    .collect(Collectors.toList());

            return futures.stream().map(this::getFutureResult).collect(Collectors.toList());
        } finally {
            executor.shutdown();
        }
    }

    private DfTaskResult executeTask(DfTask task) {
        try {
            // EXECUTE TASK WITH MONITORING
            long startTime = System.currentTimeMillis();
            String output = task.execute();
            long duration = System.currentTimeMillis() - startTime;

            return new DfTaskResult(task, output, duration, null);
        } catch (Exception e) {
            return new DfTaskResult(task, null, 0, e);
        }
    }

    private DfTaskResult getFutureResult(Future<DfTaskResult> future) {
        try {
            return future.get();
        } catch (Exception e) {
            return new DfTaskResult(null, null, 0, e);
        }
    }
}
