package io.jenkins.plugins.dfcli;

import hudson.FilePath;
import hudson.model.TaskListener;
import hudson.remoting.VirtualChannel;
import java.io.*;
import java.net.URL;
import java.nio.file.*;
import java.util.zip.ZipInputStream;
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream;
import org.apache.commons.compress.compressors.gzip.GzipCompressorInputStream;
import jenkins.MasterToSlaveFileCallable;

public class GithubInstaller {
    private static final String RELEASE_URL =
        "https://github.com/nickheyer/distroface/releases/latest/download/";

    public static synchronized void installLatest(FilePath toolDir, TaskListener log)
            throws IOException, InterruptedException {
        FilePath bin = toolDir.child(isWindows() ? "dfcli.exe" : "dfcli");
        if (bin.exists()) {
            log.getLogger().println("[dfcli] Already installed, skipping download.");
            return;
        }

        String os = System.getProperty("os.name").toLowerCase();
        String arch = System.getProperty("os.arch").toLowerCase();
        if ("x86_64".equals(arch)) arch = "amd64";
        if ("aarch64".equals(arch)) arch = "arm64";

        String platform = (os.contains("windows") ? "windows" :
                           os.contains("mac")     ? "darwin"  : "linux") + "-" + arch;
        String ext = os.contains("windows") ? ".zip" : ".tar.gz";
        String archiveName = "dfcli-" + platform + ext;
        String url = RELEASE_URL + archiveName;

        log.getLogger().println("[dfcli] Downloading from: " + url);

        FilePath tempDir = toolDir.createTempDir("dfcli-download-", null);
        FilePath archiveFile = tempDir.child(archiveName);

        // Download
        try (InputStream in = new URL(url).openStream()) {
            archiveFile.copyFrom(in);
        }

        // Extract
        if (os.contains("windows")) {
            extractZip(archiveFile, toolDir);
        } else {
            extractTarGz(archiveFile, toolDir);
        }
        tempDir.deleteRecursive();

        if (!bin.exists()) {
            throw new IOException("DFCli binary not found after extraction!");
        }
        if (!os.contains("windows")) {
            bin.chmod(0755);
        }
        log.getLogger().println("[dfcli] Installed successfully at " + bin.getRemote());
    }

    private static boolean isWindows() {
        return System.getProperty("os.name").toLowerCase().contains("windows");
    }

    private static void extractZip(FilePath zip, FilePath dest) throws IOException, InterruptedException {
        zip.act(new ExtractZipArchive(dest));
    }
    private static void extractTarGz(FilePath tgz, FilePath dest) throws IOException, InterruptedException {
        tgz.act(new ExtractTarGzArchive(dest));
    }

    private static class ExtractTarGzArchive extends MasterToSlaveFileCallable<Void> {
        private final FilePath destDir;
        ExtractTarGzArchive(FilePath destDir) {
            this.destDir = destDir;
        }
        @Override
        public Void invoke(File f, VirtualChannel channel) throws IOException {
            try (InputStream fi = Files.newInputStream(f.toPath());
                 GzipCompressorInputStream gzi = new GzipCompressorInputStream(fi);
                 TarArchiveInputStream tis = new TarArchiveInputStream(gzi)) {
                var entry = tis.getNextTarEntry();
                while (entry != null) {
                    if (!entry.isDirectory() && entry.getName().endsWith("dfcli")) {
                        Path target = Paths.get(destDir.getRemote()).resolve("dfcli");
                        Files.copy(tis, target, StandardCopyOption.REPLACE_EXISTING);
                        break;
                    }
                    entry = tis.getNextTarEntry();
                }
            }
            return null;
        }
    }

    private static class ExtractZipArchive extends MasterToSlaveFileCallable<Void> {
        private final FilePath destDir;
        ExtractZipArchive(FilePath destDir) {
            this.destDir = destDir;
        }
        @Override
        public Void invoke(File f, VirtualChannel channel) throws IOException {
            try (ZipInputStream zis = new ZipInputStream(new FileInputStream(f))) {
                var entry = zis.getNextEntry();
                while (entry != null) {
                    if (!entry.isDirectory() && entry.getName().endsWith("dfcli.exe")) {
                        Path target = Paths.get(destDir.getRemote()).resolve("dfcli.exe");
                        Files.copy(zis, target, StandardCopyOption.REPLACE_EXISTING);
                        break;
                    }
                    entry = zis.getNextEntry();
                }
            }
            return null;
        }
    }
}
