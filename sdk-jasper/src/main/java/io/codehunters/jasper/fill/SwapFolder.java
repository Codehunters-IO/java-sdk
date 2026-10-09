package io.codehunters.jasper.fill;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileAttribute;
import java.nio.file.attribute.PosixFilePermissions;
import java.util.Comparator;

/**
 * A renderer's own folder for swap files, created on first use inside the configured directory.
 * Where the file system has POSIX permissions only the owner may open it, so another local user can
 * neither read swapped pages nor plant a link where a swap file will be written. Each render swaps
 * into its own subfolder, so two renders never share a file name.
 */
public final class SwapFolder implements AutoCloseable {

  private static final String PREFIX = "jasper-swap-";

  private final Path parent;
  private Path folder;

  public SwapFolder(Path parent) {
    this.parent = parent;
  }

  /**
   * A new, empty folder for one render.
   *
   * @return the folder, inside this renderer's private one
   * @throws IOException when a folder cannot be created
   */
  Path newRenderFolder() throws IOException {
    return Files.createTempDirectory(own(), "render-");
  }

  /** Deletes the folder and anything left in it. */
  @Override
  public synchronized void close() {
    if (folder == null) {
      return;
    }
    try (var paths = Files.walk(folder)) {
      paths.sorted(Comparator.reverseOrder()).forEach(SwapFolder::delete);
    } catch (IOException e) {
      throw new UncheckedIOException("the swap folder could not be deleted", e);
    }
    folder = null;
  }

  static void delete(Path path) {
    try {
      Files.deleteIfExists(path);
    } catch (IOException e) {
      throw new UncheckedIOException("a swap path could not be deleted", e);
    }
  }

  private synchronized Path own() throws IOException {
    if (folder == null) {
      folder = Files.createTempDirectory(parent, PREFIX, ownerOnly());
    }
    return folder;
  }

  private FileAttribute<?>[] ownerOnly() {
    if (!parent.getFileSystem().supportedFileAttributeViews().contains("posix")) {
      return new FileAttribute<?>[0];
    }
    return new FileAttribute<?>[] {
      PosixFilePermissions.asFileAttribute(PosixFilePermissions.fromString("rwx------"))
    };
  }
}
