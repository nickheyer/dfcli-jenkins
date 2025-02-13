package io.jenkins.plugins.dfcli;

import java.util.function.Supplier;

public class RetryUtil {
    public static <T> T withRetry(Supplier<T> task, int maxAttempts, long delayMs) {
        Exception lastException = null;

        for (int attempt = 1; attempt <= maxAttempts; attempt++) {
            try {
                return task.get();
            } catch (Exception e) {
                lastException = e;
                if (attempt < maxAttempts) {
                    try {
                        Thread.sleep(delayMs);
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        throw new RuntimeException(ie);
                    }
                }
            }
        }

        throw new RuntimeException("Failed after " + maxAttempts + " attempts", lastException);
    }
}
