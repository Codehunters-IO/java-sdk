package io.codehunters.fileshare.infrastructure;

import static io.codehunters.fileshare.domain.model.FileRefFixtures.FILE_ID;
import static io.codehunters.fileshare.domain.model.FileRefFixtures.REFERENCE_ID;
import static io.codehunters.fileshare.domain.model.FileRefFixtures.WORKFLOW_ID;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import io.codehunters.fileshare.application.UploadRequest;
import io.codehunters.fileshare.domain.exception.FileShareConflictException;
import io.codehunters.fileshare.domain.exception.FileShareException;
import io.codehunters.fileshare.domain.exception.FileShareNotFoundException;
import io.codehunters.fileshare.domain.exception.FileShareUnauthorizedException;
import io.codehunters.fileshare.domain.exception.FileShareUnavailableException;
import io.codehunters.fileshare.domain.exception.FileShareValidationException;
import io.codehunters.fileshare.domain.model.FileRefFixtures;
import io.codehunters.fileshare.domain.model.FileStatus;
import java.io.ByteArrayOutputStream;
import java.net.URI;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.json.JsonMapper;

class RestClientFileShareAdapterTest {

  private static final String BASE = "http://file-share/api/v1/shared/REF-1/documents/";
  private static final String DOCUMENT_URL = BASE + FILE_ID;
  private static final String CONTENT_URL = DOCUMENT_URL + "/content";
  private static final String UPLOAD_URL = BASE + WORKFLOW_ID + "/presigned-url";
  private static final String SIGNED_URL =
      "https://bucket.s3.amazonaws.com/f.pdf?X-Amz-Signature=s";
  private static final long MAX_DOWNLOAD_SIZE = 8;
  private static final JsonMapper JSON = JsonMapper.builder().build();
  private static final Map<String, Object> DOCUMENT =
      Map.of(
          "id",
          FILE_ID.toString(),
          "referenceId",
          REFERENCE_ID,
          "workflowId",
          WORKFLOW_ID.toString(),
          "metadata",
          Map.of("author", "John"),
          "contentType",
          "application/pdf",
          "size",
          10,
          "filename",
          "f.pdf",
          "status",
          "COMPLETED",
          "presignedUrl",
          SIGNED_URL,
          "presignedUrlExpiresAt",
          "2030-01-01T00:00:00");

  private MockRestServiceServer server;
  private RestClientFileShareAdapter adapter;

  @BeforeEach
  void setUp() {
    var builder = RestClient.builder().baseUrl("http://file-share");
    server = MockRestServiceServer.bindTo(builder).build();
    adapter =
        new RestClientFileShareAdapter(
            builder.build(),
            new PresignedUrlPolicy(List.of("*.s3.amazonaws.com")),
            MAX_DOWNLOAD_SIZE);
  }

  @Test
  void should_return_upload_ticket_when_service_issues_url() {
    server
        .expect(requestTo(UPLOAD_URL))
        .andExpect(method(HttpMethod.POST))
        .andExpect(
            content()
                .json(
                    """
                    {"filename":"f.pdf","contentType":"application/pdf","size":10,
                     "metadata":{"k":"v"}}
                    """))
        .andRespond(json(document("status", "PENDING_UPLOAD")));

    var ticket = adapter.requestUpload(uploadRequest());

    assertThat(ticket.uploadUrl()).isEqualTo(URI.create(SIGNED_URL));
    assertThat(ticket.expiresAt()).isEqualTo(Instant.parse("2030-01-01T00:00:00Z"));
    assertThat(ticket.file().status()).isEqualTo(FileStatus.PENDING_UPLOAD);
  }

  @Test
  void should_read_expiry_with_offset_when_service_sends_one() {
    server
        .expect(requestTo(UPLOAD_URL))
        .andRespond(json(document("presignedUrlExpiresAt", "2030-01-01T02:00:00+02:00")));

    var ticket = adapter.requestUpload(uploadRequest());

    assertThat(ticket.expiresAt()).isEqualTo(Instant.parse("2030-01-01T00:00:00Z"));
  }

  @Test
  void should_fail_when_upload_response_has_no_presigned_url() {
    server.expect(requestTo(UPLOAD_URL)).andRespond(json(documentWithout("presignedUrl")));

    assertThatThrownBy(() -> adapter.requestUpload(uploadRequest()))
        .isExactlyInstanceOf(FileShareException.class)
        .hasMessageContaining("presignedUrl");
  }

  @Test
  void should_fail_when_upload_response_has_no_expiry() {
    server.expect(requestTo(UPLOAD_URL)).andRespond(json(documentWithout("presignedUrlExpiresAt")));

    assertThatThrownBy(() -> adapter.requestUpload(uploadRequest()))
        .isExactlyInstanceOf(FileShareException.class)
        .hasMessageContaining("presignedUrlExpiresAt");
  }

  @Test
  void should_fail_when_expiry_is_not_a_timestamp() {
    server
        .expect(requestTo(UPLOAD_URL))
        .andRespond(json(document("presignedUrlExpiresAt", "tomorrow")));

    assertThatThrownBy(() -> adapter.requestUpload(uploadRequest()))
        .isExactlyInstanceOf(FileShareException.class);
  }

  @Test
  void should_fail_when_presigned_url_is_not_https() {
    server
        .expect(requestTo(UPLOAD_URL))
        .andRespond(json(document("presignedUrl", "http://bucket.s3.amazonaws.com/f.pdf")));

    assertThatThrownBy(() -> adapter.requestUpload(uploadRequest()))
        .isExactlyInstanceOf(FileShareException.class)
        .hasMessageContaining("https");
  }

  @Test
  void should_fail_when_presigned_url_host_is_not_allowed() {
    server
        .expect(requestTo(UPLOAD_URL))
        .andRespond(json(document("presignedUrl", "https://evil.com/f.pdf")));

    assertThatThrownBy(() -> adapter.requestUpload(uploadRequest()))
        .isExactlyInstanceOf(FileShareException.class)
        .hasMessageContaining("host");
  }

  @Test
  void should_fail_when_presigned_url_is_malformed() {
    server.expect(requestTo(UPLOAD_URL)).andRespond(json(document("presignedUrl", "https://a b")));

    assertThatThrownBy(() -> adapter.requestUpload(uploadRequest()))
        .isExactlyInstanceOf(FileShareException.class);
  }

  @Test
  void should_fail_when_file_id_is_not_a_uuid() {
    server.expect(requestTo(DOCUMENT_URL)).andRespond(json(document("id", "not-a-uuid")));

    assertThatThrownBy(() -> adapter.getMetadata(REFERENCE_ID, FILE_ID))
        .isExactlyInstanceOf(FileShareException.class)
        .hasMessageContaining("getMetadata");
  }

  @Test
  void should_fail_when_response_breaks_file_rules() {
    server.expect(requestTo(DOCUMENT_URL)).andRespond(json(document("filename", "../x")));

    assertThatThrownBy(() -> adapter.getMetadata(REFERENCE_ID, FILE_ID))
        .isExactlyInstanceOf(FileShareException.class)
        .hasRootCauseInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void should_return_download_ticket_when_file_is_completed() {
    server
        .expect(requestTo(DOCUMENT_URL))
        .andExpect(method(HttpMethod.GET))
        .andRespond(json(document("status", "COMPLETED")));

    var ticket = adapter.requestDownload(REFERENCE_ID, FILE_ID);

    assertThat(ticket.downloadUrl()).isEqualTo(URI.create(SIGNED_URL));
    assertThat(ticket.file()).isEqualTo(FileRefFixtures.fileRef());
  }

  @Test
  void should_fail_with_conflict_when_file_has_no_download_url() {
    server.expect(requestTo(DOCUMENT_URL)).andRespond(json(documentWithout("presignedUrl")));

    assertThatThrownBy(() -> adapter.requestDownload(REFERENCE_ID, FILE_ID))
        .isInstanceOf(FileShareConflictException.class)
        .hasMessageContaining("COMPLETED");
  }

  @Test
  void should_return_metadata_without_requiring_presigned_url() {
    server.expect(requestTo(DOCUMENT_URL)).andRespond(json(documentWithout("presignedUrl")));

    assertThat(adapter.getMetadata(REFERENCE_ID, FILE_ID)).isEqualTo(FileRefFixtures.fileRef());
  }

  @Test
  void should_map_unknown_status_to_unknown() {
    server.expect(requestTo(DOCUMENT_URL)).andRespond(json(document("status", "ARCHIVED")));

    assertThat(adapter.getMetadata(REFERENCE_ID, FILE_ID).status()).isEqualTo(FileStatus.UNKNOWN);
  }

  @Test
  void should_map_missing_status_to_unknown() {
    server.expect(requestTo(DOCUMENT_URL)).andRespond(json(documentWithout("status")));

    assertThat(adapter.getMetadata(REFERENCE_ID, FILE_ID).status()).isEqualTo(FileStatus.UNKNOWN);
  }

  @Test
  void should_fail_when_response_body_is_empty() {
    server.expect(requestTo(DOCUMENT_URL)).andRespond(withSuccess());

    assertThatThrownBy(() -> adapter.getMetadata(REFERENCE_ID, FILE_ID))
        .isExactlyInstanceOf(FileShareException.class);
  }

  @Test
  void should_fail_when_response_is_not_json() {
    server
        .expect(requestTo(DOCUMENT_URL))
        .andRespond(withSuccess("not-json", MediaType.APPLICATION_JSON));

    assertThatThrownBy(() -> adapter.getMetadata(REFERENCE_ID, FILE_ID))
        .isExactlyInstanceOf(FileShareException.class);
  }

  @Test
  void should_map_404_to_not_found_with_status() {
    server.expect(requestTo(DOCUMENT_URL)).andRespond(withStatus(HttpStatus.NOT_FOUND));

    assertThatThrownBy(() -> adapter.getMetadata(REFERENCE_ID, FILE_ID))
        .isInstanceOfSatisfying(
            FileShareNotFoundException.class, e -> assertThat(e.status()).hasValue(404));
  }

  @Test
  void should_map_401_to_unauthorized() {
    server.expect(requestTo(DOCUMENT_URL)).andRespond(withStatus(HttpStatus.UNAUTHORIZED));

    assertThatThrownBy(() -> adapter.getMetadata(REFERENCE_ID, FILE_ID))
        .isInstanceOf(FileShareUnauthorizedException.class);
  }

  @Test
  void should_map_403_to_unauthorized() {
    server.expect(requestTo(DOCUMENT_URL)).andRespond(withStatus(HttpStatus.FORBIDDEN));

    assertThatThrownBy(() -> adapter.getMetadata(REFERENCE_ID, FILE_ID))
        .isInstanceOfSatisfying(
            FileShareUnauthorizedException.class, e -> assertThat(e.status()).hasValue(403));
  }

  @Test
  void should_map_409_to_conflict() {
    server.expect(requestTo(DOCUMENT_URL)).andRespond(withStatus(HttpStatus.CONFLICT));

    assertThatThrownBy(() -> adapter.getMetadata(REFERENCE_ID, FILE_ID))
        .isInstanceOf(FileShareConflictException.class);
  }

  @Test
  void should_map_other_4xx_to_validation() {
    server.expect(requestTo(DOCUMENT_URL)).andRespond(withStatus(HttpStatus.UNPROCESSABLE_CONTENT));

    assertThatThrownBy(() -> adapter.getMetadata(REFERENCE_ID, FILE_ID))
        .isInstanceOfSatisfying(
            FileShareValidationException.class, e -> assertThat(e.status()).hasValue(422));
  }

  @Test
  void should_map_5xx_to_unavailable() {
    server.expect(requestTo(DOCUMENT_URL)).andRespond(withStatus(HttpStatus.BAD_GATEWAY));

    assertThatThrownBy(() -> adapter.getMetadata(REFERENCE_ID, FILE_ID))
        .isInstanceOfSatisfying(
            FileShareUnavailableException.class, e -> assertThat(e.status()).hasValue(502));
  }

  @Test
  void should_truncate_error_body_to_512_characters() {
    server
        .expect(requestTo(DOCUMENT_URL))
        .andRespond(withStatus(HttpStatus.BAD_REQUEST).body("é".repeat(600)));

    assertThatThrownBy(() -> adapter.getMetadata(REFERENCE_ID, FILE_ID))
        .hasMessageContaining("é".repeat(512))
        .hasMessageNotContaining("é".repeat(513));
  }

  @Test
  void should_return_content_when_within_max_download_size() {
    server
        .expect(requestTo(CONTENT_URL))
        .andExpect(method(HttpMethod.GET))
        .andRespond(withSuccess(new byte[] {1, 2, 3}, MediaType.APPLICATION_OCTET_STREAM));

    assertThat(adapter.download(REFERENCE_ID, FILE_ID)).containsExactly(1, 2, 3);
  }

  @Test
  void should_fail_when_content_exceeds_max_download_size() {
    server
        .expect(requestTo(CONTENT_URL))
        .andRespond(withSuccess(new byte[9], MediaType.APPLICATION_OCTET_STREAM));

    assertThatThrownBy(() -> adapter.download(REFERENCE_ID, FILE_ID))
        .isExactlyInstanceOf(FileShareException.class)
        .hasMessageContaining("max-download-size");
  }

  @Test
  void should_map_download_error_status() {
    server.expect(requestTo(CONTENT_URL)).andRespond(withStatus(HttpStatus.NOT_FOUND));

    assertThatThrownBy(() -> adapter.download(REFERENCE_ID, FILE_ID))
        .isInstanceOf(FileShareNotFoundException.class);
  }

  @Test
  void should_stream_content_of_any_size_to_target() {
    var target = new ByteArrayOutputStream();
    server
        .expect(requestTo(CONTENT_URL))
        .andRespond(withSuccess(new byte[20], MediaType.APPLICATION_OCTET_STREAM));

    long copied = adapter.download(REFERENCE_ID, FILE_ID, target);

    assertThat(copied).isEqualTo(20);
    assertThat(target.toByteArray()).hasSize(20);
  }

  @Test
  void should_stream_content_of_file_reference() {
    var target = new ByteArrayOutputStream();
    server
        .expect(requestTo(CONTENT_URL))
        .andRespond(withSuccess(new byte[] {7}, MediaType.APPLICATION_OCTET_STREAM));

    adapter.download(FileRefFixtures.fileRef(), target);

    assertThat(target.toByteArray()).containsExactly(7);
  }

  @Test
  void should_map_streaming_error_status() {
    server.expect(requestTo(CONTENT_URL)).andRespond(withStatus(HttpStatus.SERVICE_UNAVAILABLE));

    assertThatThrownBy(() -> adapter.download(REFERENCE_ID, FILE_ID, new ByteArrayOutputStream()))
        .isInstanceOf(FileShareUnavailableException.class);
  }

  @Test
  void should_reject_unsafe_reference_id_before_calling_service() {
    assertThatIllegalArgumentException().isThrownBy(() -> adapter.getMetadata("..", FILE_ID));
    server.verify();
  }

  @Test
  void should_map_io_error_to_unavailable() {
    var offline =
        new RestClientFileShareAdapter(
            RestClient.builder().baseUrl("http://localhost:1").build(),
            new PresignedUrlPolicy(List.of()),
            MAX_DOWNLOAD_SIZE);

    assertThatThrownBy(() -> offline.getMetadata(REFERENCE_ID, FILE_ID))
        .isInstanceOf(FileShareUnavailableException.class)
        .hasMessageContaining("getMetadata");
  }

  private static UploadRequest uploadRequest() {
    return new UploadRequest(
        REFERENCE_ID, WORKFLOW_ID, "f.pdf", "application/pdf", 10L, Map.of("k", "v"));
  }

  private static org.springframework.test.web.client.ResponseCreator json(String body) {
    return withSuccess(body, MediaType.APPLICATION_JSON);
  }

  private static String document(String field, Object value) {
    Map<String, Object> fields = new LinkedHashMap<>(DOCUMENT);
    fields.put(field, value);
    return JSON.writeValueAsString(fields);
  }

  private static String documentWithout(String field) {
    Map<String, Object> fields = new LinkedHashMap<>(DOCUMENT);
    fields.remove(field);
    return JSON.writeValueAsString(fields);
  }
}
