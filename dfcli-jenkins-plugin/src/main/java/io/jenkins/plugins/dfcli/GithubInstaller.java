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
  private static final String RELEASE_URL = "https://github.com/nickheyer/distroface/releases/latest/download/";

  public static synchronized void installLatest(FilePath toolDir, TaskListener log)
      throws IOException, InterruptedException {
    // CHECK IF ALREADY INSTALLED
    FilePath marker = toolDir.child(".installed");
    FilePath binary = toolDir.child(isWindows() ? "dfcli.exe" : "dfcli");

    if (marker.exists() && binary.exists()) {
      log.getLogger().println("DFCli already installed at " + toolDir);
      return;
    }

    // CREATE TOOL DIR IF NEEDED
    if (!toolDir.exists()) {
      toolDir.mkdirs();
    }

    // DETERMINE PLATFORM
    String os = System.getProperty("os.name").toLowerCase();
    String arch = System.getProperty("os.arch").toLowerCase();

    // MAP ARCH TO GITHUB NAMES
    if (arch.equals("x86_64"))
      arch = "amd64";
    if (arch.equals("aarch64"))
      arch = "arm64";

    // GET PLATFORM SPECIFIC ARCHIVE NAME
    String platform = String.format("dfcli-%s-%s",
        os.contains("windows") ? "windows" : os.contains("mac") ? "darwin" : "linux",
        arch);

    String archiveExt = os.contains("windows") ? ".zip" : ".tar.gz";
    String archiveName = platform + archiveExt;

    // DOWNLOAD ARCHIVE
    String url = RELEASE_URL + archiveName;
    log.getLogger().println("Downloading DFCli from " + url);

    // CREATE TEMP DIR FOR DOWNLOAD
    FilePath tempDir = toolDir.createTempDir("dfcli-download", "");
    FilePath archiveFile = tempDir.child(archiveName);

    // DOWNLOAD
    try (InputStream in = new URL(url).openStream()) {
      archiveFile.copyFrom(in);
    }

    log.getLogger().println("Extracting " + archiveName);

    // EXTRACT BASED ON TYPE
    if (os.contains("windows")) {
      extractZip(archiveFile, toolDir, log);
    } else {
      extractTarGz(archiveFile, toolDir, log);
    }

    // CLEANUP
    tempDir.deleteRecursive();

    // VERIFY AND SET PERMISSIONS
    binary = toolDir.child(os.contains("windows") ? "dfcli.exe" : "dfcli");
    if (!binary.exists()) {
      throw new IOException("Binary not found after extraction: " + binary.getRemote());
    }

    if (!os.contains("windows")) {
      binary.chmod(0755);
    }

    log.getLogger().println("DFCli installed successfully");
  }

  private static boolean isWindows() {
    return System.getProperty("os.name").toLowerCase().contains("windows");
  }

  private static void extractZip(FilePath zip, FilePath destDir, TaskListener log)
      throws IOException, InterruptedException {
    zip.act(new ExtractZipArchive(destDir));
  }

  private static void extractTarGz(FilePath tarGz, FilePath destDir, TaskListener log)
      throws IOException, InterruptedException {
    tarGz.act(new ExtractTarGzArchive(destDir));
  }
}

class ExtractTarGzArchive extends MasterToSlaveFileCallable<Void> {
  private final FilePath destDir;

  ExtractTarGzArchive(FilePath destDir) {
    this.destDir = destDir;
  }

  @Override
  public Void invoke(File tarGzFile, VirtualChannel channel) throws IOException {
    Path destPath = Paths.get(destDir.getRemote());

    try (InputStream fi = Files.newInputStream(tarGzFile.toPath());
        GzipCompressorInputStream gzi = new GzipCompressorInputStream(fi);
        TarArchiveInputStream tis = new TarArchiveInputStream(gzi)) {

      var entry = tis.getNextTarEntry();
      while (entry != null) {
        // ONLY CARE ABOUT THE BINARY FILE
        String name = entry.getName();
        if (name.endsWith("/dfcli") || name.equals("dfcli")) {
          // STRIP ANY DIRECTORY PREFIX
          Path targetPath = destPath.resolve("dfcli");

          Files.createDirectories(targetPath.getParent());
          Files.copy(tis, targetPath, StandardCopyOption.REPLACE_EXISTING);
          break; // Found what we need
        }
        entry = tis.getNextTarEntry();
      }
    }
    return null;
  }
}

class ExtractZipArchive extends MasterToSlaveFileCallable<Void> {
  private final FilePath destDir;

  ExtractZipArchive(FilePath destDir) {
    this.destDir = destDir;
  }

  @Override
  public Void invoke(File zipFile, VirtualChannel channel) throws IOException {
    Path destPath = Paths.get(destDir.getRemote());

    try (ZipInputStream zis = new ZipInputStream(new FileInputStream(zipFile))) {
      var entry = zis.getNextEntry();
      while (entry != null) {
        String name = entry.getName();
        if (name.endsWith("/dfcli.exe") || name.equals("dfcli.exe")) {
          // STRIP ANY DIRECTORY PREFIX
          Path targetPath = destPath.resolve("dfcli.exe");

          Files.createDirectories(targetPath.getParent());
          Files.copy(zis, targetPath, StandardCopyOption.REPLACE_EXISTING);
          break; // Found what we need
        }
        entry = zis.getNextEntry();
      }
    }
    return null;
  }
}
