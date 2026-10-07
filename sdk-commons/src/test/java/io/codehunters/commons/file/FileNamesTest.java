package io.codehunters.commons.file;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

class FileNamesTest {

  @ParameterizedTest
  @CsvSource({
    "report.PDF, PDF",
    "archive.tar.gz, gz",
    "docs/v1.2/notes.md, md",
    "C:\\dir.v2\\a.txt, txt"
  })
  void should_return_the_last_extension(String filename, String expected) {
    assertThat(FileNames.extension(filename)).contains(expected);
  }

  @ParameterizedTest
  @ValueSource(strings = {"README", ".bashrc", "file.", "dir.v2/README", ""})
  void should_return_empty_when_there_is_no_extension(String filename) {
    assertThat(FileNames.extension(filename)).isEmpty();
  }

  @ParameterizedTest
  @CsvSource({
    "report.pdf, invoice-42, invoice-42.pdf",
    "README, notes, notes",
    "uploads/photo.JPG, avatar, avatar.JPG"
  })
  void should_replace_the_base_name_and_keep_the_extension(
      String filename, String baseName, String expected) {
    assertThat(FileNames.withBaseName(filename, baseName)).isEqualTo(expected);
  }

  @ParameterizedTest
  @ValueSource(strings = {" ", "a/b", "a\\b", ".."})
  void should_reject_base_names_that_are_blank_or_contain_separators(String baseName) {
    assertThatIllegalArgumentException()
        .isThrownBy(() -> FileNames.withBaseName("report.pdf", baseName))
        .withMessage("baseName must be a non-blank name without path separators");
  }

  @Test
  void should_reject_null_file_name() {
    assertThatIllegalArgumentException()
        .isThrownBy(() -> FileNames.extension(null))
        .withMessage("filename must not be null");
  }

  @Test
  void should_reject_null_base_name() {
    assertThatIllegalArgumentException()
        .isThrownBy(() -> FileNames.withBaseName("report.pdf", null));
  }
}
