package io.jenkins.plugins.dfcli;

import hudson.FilePath;
import hudson.model.TaskListener;
import java.io.IOException;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class ArtifactProcessor {
    private final TaskListener listener;
    private final Map<String, Long> artifactSizes;
    private final Map<String, String> artifactChecksums;

    public ArtifactProcessor(TaskListener listener) {
        this.listener = listener;
        this.artifactSizes = new ConcurrentHashMap<>();
        this.artifactChecksums = new ConcurrentHashMap<>();
    }

    public void processArtifact(FilePath artifact) throws IOException, InterruptedException {
        // CALCULATE SIZE AND CHECKSUM
        long size = artifact.length();
        String checksum = calculateChecksum(artifact);

        // STORE METADATA
        artifactSizes.put(artifact.getName(), size);
        artifactChecksums.put(artifact.getName(), checksum);

        // LOG PROCESSING
        listener.getLogger()
                .printf("Processed artifact %s: size=%d, checksum=%s%n", artifact.getName(), size, checksum);
    }

    private String calculateChecksum(FilePath artifact) throws IOException, InterruptedException {
        return artifact.act(new ChecksumCalculator());
    }

    public Map<String, Long> getArtifactSizes() {
        return artifactSizes;
    }

    public Map<String, String> getArtifactChecksums() {
        return artifactChecksums;
    }
}
