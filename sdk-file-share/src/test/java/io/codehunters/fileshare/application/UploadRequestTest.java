package io.codehunters.fileshare.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class UploadRequestTest {

  private static final UUID WORKFLOW_ID = UUID.fromString("22222222-2222-2222-2222-222222222222");

  @Test
  void should_copy_metadata_when_caller_map_changes_later() {
    Map<String, String> metadata = new HashMap<>(Map.of("k", "v"));
    var request = new UploadRequest("REF-1", WORKFLOW_ID, "f.pdf", "application/pdf", 1L, metadata);

    metadata.put("other", "x");

    assertThat(request.metadata()).containsOnlyKeys("k");
  }

  @Test
  void should_use_empty_metadata_when_null() {
    var request = new UploadRequest("REF-1", WORKFLOW_ID, "f.pdf", "application/pdf", 1L, null);

    assertThat(request.metadata()).isEmpty();
  }

  @Test
  void should_reject_unsafe_reference_id() {
    assertThatIllegalArgumentException()
        .isThrownBy(
            () -> new UploadRequest("..", WORKFLOW_ID, "f.pdf", "application/pdf", 1L, Map.of()))
        .withMessageContaining("referenceId");
  }

  @Test
  void should_reject_missing_workflow_id() {
    assertThatNullPointerException()
        .isThrownBy(() -> new UploadRequest("REF-1", null, "f.pdf", "application/pdf", 1L, null))
        .withMessageContaining("workflowId");
  }

  @Test
  void should_reject_filename_with_separator() {
    assertThatIllegalArgumentException()
        .isThrownBy(
            () ->
                new UploadRequest(
                    "REF-1", WORKFLOW_ID, "../f.pdf", "application/pdf", 1L, Map.of()))
        .withMessageContaining("filename");
  }

  @Test
  void should_reject_negative_size() {
    assertThatIllegalArgumentException()
        .isThrownBy(
            () -> new UploadRequest("REF-1", WORKFLOW_ID, "f.pdf", "application/pdf", -5L, null))
        .withMessageContaining("size");
  }

  @Test
  void should_reject_unparseable_content_type() {
    assertThatIllegalArgumentException()
        .isThrownBy(() -> new UploadRequest("REF-1", WORKFLOW_ID, "f.pdf", "pdf", 1L, null))
        .withMessageContaining("contentType");
  }
}
