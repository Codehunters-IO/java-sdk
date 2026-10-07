package io.codehunters.jasper;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.zip.ZipFile;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.text.PDFTextStripper;

/** Reads rendered documents back for assertions. */
final class Documents {

  private Documents() {}

  static String pdfText(byte[] pdf) {
    try (var document = Loader.loadPDF(pdf)) {
      return new PDFTextStripper().getText(document);
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
  }

  static int pdfPages(byte[] pdf) {
    try (var document = Loader.loadPDF(pdf)) {
      return document.getNumberOfPages();
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
  }

  static boolean hasZipEntry(byte[] zip, Path folder, String entry) {
    try {
      Path file = Files.write(folder.resolve("document.zip"), zip);
      try (var archive = new ZipFile(file.toFile())) {
        return archive.getEntry(entry) != null;
      }
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
  }

  static long filesIn(Path folder) {
    try (var files = Files.list(folder)) {
      return files.count();
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
  }
}
