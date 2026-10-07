package io.codehunters.fileshare.domain.model;

import static io.codehunters.fileshare.domain.model.FileRefFixtures.CONTENT_TYPE;
import static io.codehunters.fileshare.domain.model.FileRefFixtures.FILENAME;
import static io.codehunters.fileshare.domain.model.FileRefFixtures.FILE_ID;
import static io.codehunters.fileshare.domain.model.FileRefFixtures.REFERENCE_ID;
import static io.codehunters.fileshare.domain.model.FileRefFixtures.WORKFLOW_ID;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;

import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;

class FileRefTest {

  @Test
  void should_hold_all_fields_when_valid() {
    FileRef ref = FileRefFixtures.fileRef();

    assertThat(ref.id()).isEqualTo(FILE_ID);
    assertThat(ref.referenceId()).isEqualTo(REFERENCE_ID);
    assertThat(ref.workflowId()).isEqualTo(WORKFLOW_ID);
    assertThat(ref.filename()).isEqualTo(FILENAME);
    assertThat(ref.contentType()).isEqualTo(CONTENT_TYPE);
    assertThat(ref.size()).isEqualTo(10L);
    assertThat(ref.status()).isEqualTo(FileStatus.COMPLETED);
    assertThat(ref.metadata()).containsEntry("author", "John");
  }

  @Test
  void should_copy_metadata_when_caller_map_changes_later() {
    Map<String, String> metadata = new HashMap<>(Map.of("k", "v"));
    FileRef ref = withMetadata(metadata);

    metadata.put("other", "x");

    assertThat(ref.metadata()).containsOnlyKeys("k");
  }

  @Test
  void should_use_empty_metadata_when_null() {
    assertThat(withMetadata(null).metadata()).isEmpty();
  }

  @Test
  void should_use_unknown_status_when_null() {
    FileRef ref =
        new FileRef(FILE_ID, REFERENCE_ID, WORKFLOW_ID, FILENAME, CONTENT_TYPE, 1L, null, Map.of());

    assertThat(ref.status()).isEqualTo(FileStatus.UNKNOWN);
  }

  @Test
  void should_reject_missing_id() {
    assertThatNullPointerException()
        .isThrownBy(
            () ->
                new FileRef(
                    null,
                    REFERENCE_ID,
                    WORKFLOW_ID,
                    FILENAME,
                    CONTENT_TYPE,
                    1L,
                    FileStatus.COMPLETED,
                    Map.of()))
        .withMessageContaining("id");
  }

  @Test
  void should_reject_reference_id_with_path_characters() {
    assertThatIllegalArgumentException()
        .isThrownBy(
            () ->
                new FileRef(
                    FILE_ID,
                    "../etc",
                    WORKFLOW_ID,
                    FILENAME,
                    CONTENT_TYPE,
                    1L,
                    FileStatus.COMPLETED,
                    Map.of()))
        .withMessageContaining("referenceId");
  }

  @Test
  void should_reject_filename_with_separator() {
    assertThatIllegalArgumentException()
        .isThrownBy(
            () ->
                new FileRef(
                    FILE_ID,
                    REFERENCE_ID,
                    WORKFLOW_ID,
                    "a/b.pdf",
                    CONTENT_TYPE,
                    1L,
                    FileStatus.COMPLETED,
                    Map.of()))
        .withMessageContaining("filename");
  }

  @Test
  void should_reject_negative_size() {
    assertThatIllegalArgumentException()
        .isThrownBy(
            () ->
                new FileRef(
                    FILE_ID,
                    REFERENCE_ID,
                    WORKFLOW_ID,
                    FILENAME,
                    CONTENT_TYPE,
                    -1L,
                    FileStatus.COMPLETED,
                    Map.of()))
        .withMessageContaining("size");
  }

  @Test
  void should_reject_unparseable_content_type() {
    assertThatIllegalArgumentException()
        .isThrownBy(
            () ->
                new FileRef(
                    FILE_ID,
                    REFERENCE_ID,
                    WORKFLOW_ID,
                    FILENAME,
                    "pdf",
                    1L,
                    FileStatus.COMPLETED,
                    Map.of()))
        .withMessageContaining("contentType");
  }

  @Test
  void should_compare_by_value() {
    assertThat(FileRefFixtures.fileRef()).isEqualTo(FileRefFixtures.fileRef());
  }

  private static FileRef withMetadata(Map<String, String> metadata) {
    return new FileRef(
        FILE_ID,
        REFERENCE_ID,
        WORKFLOW_ID,
        FILENAME,
        CONTENT_TYPE,
        1L,
        FileStatus.COMPLETED,
        metadata);
  }
}
