package io.codehunters.fileshare.testing;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.codehunters.fileshare.FileShareClient;
import io.codehunters.fileshare.FileShareConflictException;
import io.codehunters.fileshare.FileShareNotFoundException;
import io.codehunters.fileshare.UploadRequest;
import io.codehunters.fileshare.model.FileStatus;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class InMemoryFileShareClientTest {

  private static final Instant NOW = Instant.parse("2026-10-09T10:00:00Z");
  private static final UUID WORKFLOW_ID = UUID.fromString("22222222-2222-2222-2222-222222222222");
  private static final byte[] CONTENT = {1, 2, 3};
  private static final UploadRequest REQUEST =
      new UploadRequest("REF-1", WORKFLOW_ID, "f.pdf", "application/pdf", 3, Map.of("k", "v"));

  private final InMemoryFileShareClient files =
      new InMemoryFileShareClient(Clock.fixed(NOW, ZoneOffset.UTC));

  @Test
  void should_be_a_file_share_client_when_created() {
    assertThat(files).isInstanceOf(FileShareClient.class);
  }

  @Test
  void should_issue_an_https_upload_ticket_when_a_file_is_pending() {
    var ticket = files.requestUpload(REQUEST);

    assertThat(ticket.file().status()).isEqualTo(FileStatus.PENDING_UPLOAD);
    assertThat(ticket.file().referenceId()).isEqualTo("REF-1");
    assertThat(ticket.file().metadata()).containsEntry("k", "v");
    assertThat(ticket.uploadUrl().getScheme()).isEqualTo("https");
    assertThat(ticket.expiresAt()).isEqualTo(NOW.plus(Duration.ofMinutes(15)));
  }

  @Test
  void should_complete_the_file_when_its_content_is_uploaded() {
    var ticket = files.requestUpload(REQUEST);

    files.upload(ticket, new ByteArrayInputStream(CONTENT), 3);

    assertThat(files.getMetadata("REF-1", ticket.file().id()).status())
        .isEqualTo(FileStatus.COMPLETED);
    assertThat(files.download("REF-1", ticket.file().id())).containsExactly(CONTENT);
  }

  @Test
  void should_fail_when_uploaded_content_is_shorter_than_content_length() {
    var ticket = files.requestUpload(REQUEST);

    assertThatIllegalArgumentException()
        .isThrownBy(() -> files.upload(ticket, new ByteArrayInputStream(CONTENT), 4))
        .withMessageContaining("contentLength");
  }

  @Test
  void should_serve_the_content_when_a_file_is_stored() {
    var file = files.store(REQUEST, CONTENT);
    var target = new ByteArrayOutputStream();

    long written = files.download(file, target);

    assertThat(written).isEqualTo(3);
    assertThat(target.toByteArray()).containsExactly(CONTENT);
    assertThat(files.content(file.id())).hasValueSatisfying(c -> assertThat(c).isEqualTo(CONTENT));
  }

  @Test
  void should_issue_a_download_ticket_when_a_file_is_completed() {
    var file = files.store(REQUEST, CONTENT);

    var ticket = files.requestDownload("REF-1", file.id());

    assertThat(ticket.file()).isEqualTo(file);
    assertThat(ticket.downloadUrl().getScheme()).isEqualTo("https");
  }

  @Test
  void should_refuse_to_download_when_a_file_still_waits_for_its_content() {
    var ticket = files.requestUpload(REQUEST);

    assertThatThrownBy(() -> files.requestDownload("REF-1", ticket.file().id()))
        .isInstanceOf(FileShareConflictException.class);
    assertThatThrownBy(() -> files.download("REF-1", ticket.file().id()))
        .isInstanceOf(FileShareConflictException.class);
  }

  @Test
  void should_not_find_a_file_when_asked_under_another_reference() {
    var file = files.store(REQUEST, CONTENT);

    assertThatThrownBy(() -> files.getMetadata("REF-2", file.id()))
        .isInstanceOf(FileShareNotFoundException.class);
  }

  @Test
  void should_not_find_a_file_when_its_id_is_unknown() {
    assertThatThrownBy(() -> files.getMetadata("REF-1", WORKFLOW_ID))
        .isInstanceOf(FileShareNotFoundException.class);
    assertThat(files.content(WORKFLOW_ID)).isEmpty();
  }

  @Test
  void should_reject_the_call_when_reference_id_is_unsafe() {
    assertThatIllegalArgumentException().isThrownBy(() -> files.getMetadata("..", WORKFLOW_ID));
  }
}
