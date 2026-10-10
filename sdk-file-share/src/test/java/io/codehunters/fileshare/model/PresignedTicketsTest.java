package io.codehunters.fileshare.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;

import java.net.URI;
import java.time.Instant;
import java.util.Map;
import org.junit.jupiter.api.Test;

class PresignedTicketsTest {

  private static final URI SIGNED_URL =
      URI.create("https://bucket.s3.amazonaws.com/key.pdf?X-Amz-Signature=secret");
  private static final Instant EXPIRES_AT = Instant.parse("2030-01-01T00:00:00Z");

  @Test
  void should_use_put_when_uploading() {
    var ticket = new PresignedUploadTicket(FileRefFixtures.fileRef(), SIGNED_URL, EXPIRES_AT);

    assertThat(ticket.httpMethod()).isEqualTo("PUT");
    assertThat(ticket.expiresAt()).isEqualTo(EXPIRES_AT);
  }

  @Test
  void should_require_the_signed_content_type_when_uploading() {
    var ticket = new PresignedUploadTicket(FileRefFixtures.fileRef(), SIGNED_URL, EXPIRES_AT);

    assertThat(ticket.requiredHeaders())
        .containsExactly(Map.entry("Content-Type", FileRefFixtures.CONTENT_TYPE));
  }

  @Test
  void should_use_get_when_downloading() {
    var ticket = new PresignedDownloadTicket(FileRefFixtures.fileRef(), SIGNED_URL, EXPIRES_AT);

    assertThat(ticket.httpMethod()).isEqualTo("GET");
    assertThat(ticket.expiresAt()).isEqualTo(EXPIRES_AT);
  }

  @Test
  void should_hide_signature_when_upload_ticket_is_printed() {
    var ticket = new PresignedUploadTicket(FileRefFixtures.fileRef(), SIGNED_URL, EXPIRES_AT);

    assertThat(ticket.toString())
        .doesNotContain("secret")
        .contains("https://bucket.s3.amazonaws.com/key.pdf")
        .contains(EXPIRES_AT.toString());
  }

  @Test
  void should_hide_signature_when_download_ticket_is_printed() {
    var ticket = new PresignedDownloadTicket(FileRefFixtures.fileRef(), SIGNED_URL, EXPIRES_AT);

    assertThat(ticket.toString())
        .doesNotContain("secret")
        .contains("https://bucket.s3.amazonaws.com/key.pdf");
  }

  @Test
  void should_print_whole_url_when_it_has_no_query() {
    var ticket =
        new PresignedUploadTicket(
            FileRefFixtures.fileRef(), URI.create("https://files.example.com/key"), EXPIRES_AT);

    assertThat(ticket.toString()).contains("uploadUrl=https://files.example.com/key,");
  }

  @Test
  void should_hide_user_info_and_fragment_when_ticket_is_printed() {
    var ticket =
        new PresignedDownloadTicket(
            FileRefFixtures.fileRef(),
            URI.create("https://user:pass@files.example.com:8443/key?sig=secret#frag"),
            EXPIRES_AT);

    assertThat(ticket.toString())
        .doesNotContain("user")
        .doesNotContain("pass")
        .doesNotContain("frag")
        .contains("downloadUrl=https://files.example.com:8443/key?<redacted>,");
  }

  @Test
  void should_reject_upload_ticket_without_url() {
    assertThatNullPointerException()
        .isThrownBy(() -> new PresignedUploadTicket(FileRefFixtures.fileRef(), null, EXPIRES_AT));
  }

  @Test
  void should_reject_download_ticket_without_expiry() {
    assertThatNullPointerException()
        .isThrownBy(() -> new PresignedDownloadTicket(FileRefFixtures.fileRef(), SIGNED_URL, null));
  }
}
