package io.codehunters.fileshare.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class FileConstraintsTest {

  @ParameterizedTest
  @ValueSource(strings = {"REF-1", "a.b_c-9", "x"})
  void should_accept_reference_id_when_safe(String referenceId) {
    assertThat(FileConstraints.requireReferenceId(referenceId)).isEqualTo(referenceId);
  }

  @ParameterizedTest
  @ValueSource(strings = {"", ".", "..", "a/b", "a b", "a%2Fb", "ref?x=1", "a\\b"})
  void should_reject_reference_id_when_unsafe(String referenceId) {
    assertThatIllegalArgumentException()
        .isThrownBy(() -> FileConstraints.requireReferenceId(referenceId))
        .withMessageContaining("referenceId");
  }

  @Test
  void should_reject_reference_id_when_null() {
    assertThatNullPointerException()
        .isThrownBy(() -> FileConstraints.requireReferenceId(null))
        .withMessageContaining("referenceId");
  }

  @ParameterizedTest
  @ValueSource(strings = {"f.pdf", "Informe final (v2).pdf", "año.txt"})
  void should_accept_filename_when_plain(String filename) {
    assertThat(FileConstraints.requireFilename(filename)).isEqualTo(filename);
  }

  @ParameterizedTest
  @ValueSource(strings = {"", " ", ".", "..", "a/b", "a\\b", "a\nb", "a\u0000b", "a\u007fb"})
  void should_reject_filename_when_unsafe(String filename) {
    assertThatIllegalArgumentException()
        .isThrownBy(() -> FileConstraints.requireFilename(filename))
        .withMessageContaining("filename");
  }

  @Test
  void should_reject_filename_when_longer_than_255() {
    assertThatIllegalArgumentException()
        .isThrownBy(() -> FileConstraints.requireFilename("a".repeat(256)))
        .withMessageContaining("filename");
  }

  @ParameterizedTest
  @ValueSource(
      strings = {"application/pdf", "text/plain; charset=UTF-8", "application/vnd.ms-excel"})
  void should_accept_content_type_when_parseable(String contentType) {
    assertThat(FileConstraints.requireContentType(contentType)).isEqualTo(contentType);
  }

  @ParameterizedTest
  @ValueSource(strings = {"", "pdf", "application/", "/pdf", "text/plain\r\nX: y", "a b/c"})
  void should_reject_content_type_when_not_parseable(String contentType) {
    assertThatIllegalArgumentException()
        .isThrownBy(() -> FileConstraints.requireContentType(contentType))
        .withMessageContaining("contentType");
  }

  @Test
  void should_accept_zero_size() {
    assertThat(FileConstraints.requireSize(0L)).isZero();
  }

  @Test
  void should_reject_negative_size() {
    assertThatIllegalArgumentException()
        .isThrownBy(() -> FileConstraints.requireSize(-1L))
        .withMessageContaining("size");
  }
}
