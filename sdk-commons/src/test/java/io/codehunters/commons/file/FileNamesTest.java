package io.codehunters.commons.file;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

class FileNamesTest {

  private static final String ZERO_WIDTH_SPACE = String.valueOf((char) 0x200B);

  private static final String UNSAFE_BASE_NAME =
      "baseName must be a plain file name: not blank, not . or .., without separators, colons,"
          + " control or format characters, and not ending in a dot or space";

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
  @ValueSource(strings = {"a.jsp\u0000", "a.php ", "a.txt:evil", "a.p/h", "a.abcdefghijklmnopq"})
  void should_return_empty_when_the_extension_is_not_plain_letters_digits_dash_or_underscore(
      String filename) {
    assertThat(FileNames.extension(filename)).isEmpty();
  }

  @ParameterizedTest
  @CsvSource({
    "photo.jpeg_2, jpeg_2",
    "data.tar-gz, tar-gz",
    "foto.ñ, ñ",
    "a.abcdefghijklmnop, abcdefghijklmnop"
  })
  void should_accept_extensions_of_letters_digits_dash_and_underscore(
      String filename, String expected) {
    assertThat(FileNames.extension(filename)).contains(expected);
  }

  @ParameterizedTest
  @ValueSource(strings = {".", "..", "a:b", "a\u0000", "name.", "name "})
  void should_reject_base_names_that_are_not_safe_file_names(String baseName) {
    assertThatIllegalArgumentException()
        .isThrownBy(() -> FileNames.withBaseName("report.pdf", baseName))
        .withMessage(UNSAFE_BASE_NAME);
  }

  @Test
  void should_return_empty_when_the_extension_hides_a_zero_width_space() {
    assertThat(FileNames.extension("a.html" + ZERO_WIDTH_SPACE)).isEmpty();
  }

  @Test
  void should_reject_base_name_with_a_zero_width_space() {
    assertThatIllegalArgumentException()
        .isThrownBy(() -> FileNames.withBaseName("report.pdf", "a" + ZERO_WIDTH_SPACE))
        .withMessage(UNSAFE_BASE_NAME);
  }

  @Test
  void should_drop_an_unsafe_extension_when_replacing_the_base_name() {
    assertThat(FileNames.withBaseName("shell.php ", "upload")).isEqualTo("upload");
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
  @ValueSource(strings = {" ", "a/b", "a\\b"})
  void should_reject_base_names_that_are_blank_or_contain_separators(String baseName) {
    assertThatIllegalArgumentException()
        .isThrownBy(() -> FileNames.withBaseName("report.pdf", baseName))
        .withMessage(UNSAFE_BASE_NAME);
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
