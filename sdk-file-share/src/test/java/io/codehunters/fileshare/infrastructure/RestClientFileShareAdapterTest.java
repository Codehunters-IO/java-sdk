package io.codehunters.fileshare.infrastructure;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
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
import io.codehunters.fileshare.domain.model.FileStatus;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

class RestClientFileShareAdapterTest {

  private static final UUID FILE_ID = UUID.fromString("11111111-1111-1111-1111-111111111111");
  private static final UUID WORKFLOW_ID = UUID.fromString("22222222-2222-2222-2222-222222222222");
  private static final String REFERENCE_ID = "REF-1";

  private RestClient mainClient;
  private MockRestServiceServer mainServer;
  private RestClientFileShareAdapter adapter;

  @BeforeEach
  void setup() {
    var mainBuilder = RestClient.builder().baseUrl("http://file-share");
    mainServer = MockRestServiceServer.bindTo(mainBuilder).build();
    mainClient = mainBuilder.build();
    adapter = new RestClientFileShareAdapter(mainClient);
  }

  @Test
  void requestUpload_returns_ticket() {
    mainServer
        .expect(
            requestTo(
                "http://file-share/api/v1/shared/REF-1/documents/"
                    + WORKFLOW_ID
                    + "/presigned-url"))
        .andExpect(method(HttpMethod.POST))
        .andRespond(withSuccess(uploadResponseJson(), MediaType.APPLICATION_JSON));

    var ticket =
        adapter.requestUpload(
            new UploadRequest(
                REFERENCE_ID, WORKFLOW_ID, "f.pdf", "application/pdf", 10L, Map.of()));

    assertEquals("https://s3/put", ticket.uploadUrl().toString());
    assertEquals(FileStatus.PENDING_UPLOAD, ticket.file().status());
    assertEquals("f.pdf", ticket.file().filename());
  }

  @Test
  void getMetadata_returns_fileRef() {
    mainServer
        .expect(requestTo("http://file-share/api/v1/shared/REF-1/documents/" + FILE_ID))
        .andExpect(method(HttpMethod.GET))
        .andRespond(
            withSuccess(documentResponseJson("https://s3/get"), MediaType.APPLICATION_JSON));

    var ref = adapter.getMetadata(REFERENCE_ID, FILE_ID);
    assertEquals(FILE_ID, ref.id());
    assertEquals("f.pdf", ref.filename());
  }

  @Test
  void requestDownload_throws_when_not_completed() {
    mainServer
        .expect(requestTo("http://file-share/api/v1/shared/REF-1/documents/" + FILE_ID))
        .andExpect(method(HttpMethod.GET))
        .andRespond(withSuccess(documentResponseJsonNoUrl(), MediaType.APPLICATION_JSON));

    assertThrows(
        FileShareConflictException.class, () -> adapter.requestDownload(REFERENCE_ID, FILE_ID));
  }

  @Test
  void download_calls_server_side_content_endpoint() {
    mainServer
        .expect(
            requestTo("http://file-share/api/v1/shared/REF-1/documents/" + FILE_ID + "/content"))
        .andExpect(method(HttpMethod.GET))
        .andRespond(withSuccess(new byte[] {1, 2, 3}, MediaType.APPLICATION_OCTET_STREAM));

    byte[] data = adapter.download(REFERENCE_ID, FILE_ID);
    assertArrayEquals(new byte[] {1, 2, 3}, data);
  }

  @Test
  void download_maps_404_to_not_found() {
    mainServer
        .expect(
            requestTo("http://file-share/api/v1/shared/REF-1/documents/" + FILE_ID + "/content"))
        .andRespond(
            withStatus(HttpStatus.NOT_FOUND).body("{}").contentType(MediaType.APPLICATION_JSON));

    assertThrows(FileShareNotFoundException.class, () -> adapter.download(REFERENCE_ID, FILE_ID));
  }

  @Test
  void download_maps_409_to_conflict() {
    mainServer
        .expect(
            requestTo("http://file-share/api/v1/shared/REF-1/documents/" + FILE_ID + "/content"))
        .andRespond(
            withStatus(HttpStatus.CONFLICT).body("{}").contentType(MediaType.APPLICATION_JSON));

    assertThrows(FileShareConflictException.class, () -> adapter.download(REFERENCE_ID, FILE_ID));
  }

  @Test
  void maps_404_to_not_found() {
    mainServer
        .expect(requestTo("http://file-share/api/v1/shared/REF-1/documents/" + FILE_ID))
        .andRespond(
            withStatus(HttpStatus.NOT_FOUND).body("{}").contentType(MediaType.APPLICATION_JSON));
    assertThrows(
        FileShareNotFoundException.class, () -> adapter.getMetadata(REFERENCE_ID, FILE_ID));
  }

  @Test
  void maps_401_to_unauthorized() {
    mainServer
        .expect(requestTo("http://file-share/api/v1/shared/REF-1/documents/" + FILE_ID))
        .andRespond(
            withStatus(HttpStatus.UNAUTHORIZED).body("{}").contentType(MediaType.APPLICATION_JSON));
    assertThrows(
        FileShareUnauthorizedException.class, () -> adapter.getMetadata(REFERENCE_ID, FILE_ID));
  }

  @Test
  void maps_400_to_validation() {
    mainServer
        .expect(requestTo("http://file-share/api/v1/shared/REF-1/documents/" + FILE_ID))
        .andRespond(
            withStatus(HttpStatus.BAD_REQUEST).body("{}").contentType(MediaType.APPLICATION_JSON));
    assertThrows(
        FileShareValidationException.class, () -> adapter.getMetadata(REFERENCE_ID, FILE_ID));
  }

  @Test
  void maps_5xx_to_unavailable() {
    mainServer
        .expect(requestTo("http://file-share/api/v1/shared/REF-1/documents/" + FILE_ID))
        .andRespond(
            withStatus(HttpStatus.BAD_GATEWAY).body("{}").contentType(MediaType.APPLICATION_JSON));
    assertThrows(
        FileShareUnavailableException.class, () -> adapter.getMetadata(REFERENCE_ID, FILE_ID));
  }

  @Test
  void requestDownload_returns_ticket_when_url_present() {
    mainServer
        .expect(requestTo("http://file-share/api/v1/shared/REF-1/documents/" + FILE_ID))
        .andExpect(method(HttpMethod.GET))
        .andRespond(
            withSuccess(documentResponseJson("https://s3/get"), MediaType.APPLICATION_JSON));

    var ticket = adapter.requestDownload(REFERENCE_ID, FILE_ID);

    assertEquals("https://s3/get", ticket.downloadUrl().toString());
    assertEquals(FILE_ID, ticket.file().id());
    assertEquals(FileStatus.COMPLETED, ticket.file().status());
  }

  @Test
  void getMetadata_throws_when_response_body_is_empty() {
    mainServer
        .expect(requestTo("http://file-share/api/v1/shared/REF-1/documents/" + FILE_ID))
        .andExpect(method(HttpMethod.GET))
        .andRespond(withSuccess());

    assertThrows(FileShareException.class, () -> adapter.getMetadata(REFERENCE_ID, FILE_ID));
  }

  @Test
  void getMetadata_tolerates_null_id_metadata_and_status() {
    mainServer
        .expect(requestTo("http://file-share/api/v1/shared/REF-1/documents/" + FILE_ID))
        .andExpect(method(HttpMethod.GET))
        .andRespond(withSuccess(documentResponseJsonNullFields(), MediaType.APPLICATION_JSON));

    var ref = adapter.getMetadata(REFERENCE_ID, FILE_ID);

    assertNull(ref.id());
    assertNull(ref.status());
    assertEquals(Map.of(), ref.metadata());
  }

  @Test
  void getMetadata_maps_unknown_status_to_null() {
    mainServer
        .expect(requestTo("http://file-share/api/v1/shared/REF-1/documents/" + FILE_ID))
        .andExpect(method(HttpMethod.GET))
        .andRespond(withSuccess(documentResponseJsonUnknownStatus(), MediaType.APPLICATION_JSON));

    var ref = adapter.getMetadata(REFERENCE_ID, FILE_ID);

    assertNull(ref.status());
  }

  @Test
  void getMetadata_maps_io_error_to_unavailable() {
    var offlineAdapter =
        new RestClientFileShareAdapter(RestClient.builder().baseUrl("http://localhost:1").build());

    assertThrows(
        FileShareUnavailableException.class,
        () -> offlineAdapter.getMetadata(REFERENCE_ID, FILE_ID));
  }

  @Test
  void getMetadata_maps_unexpected_error_to_generic_exception() {
    mainServer
        .expect(requestTo("http://file-share/api/v1/shared/REF-1/documents/" + FILE_ID))
        .andExpect(method(HttpMethod.GET))
        .andRespond(withSuccess("not-json", MediaType.APPLICATION_JSON));

    assertThrows(FileShareException.class, () -> adapter.getMetadata(REFERENCE_ID, FILE_ID));
  }

  private String documentResponseJsonNullFields() {
    return """
        {
          "referenceId": "REF-1",
          "workflowId": "%s",
          "contentType": "application/pdf",
          "size": 10,
          "filename": "f.pdf",
          "presignedUrlExpiresAt": "2030-01-01T00:00:00"
        }
        """
        .formatted(WORKFLOW_ID);
  }

  private String documentResponseJsonUnknownStatus() {
    return """
        {
          "id": "%s",
          "referenceId": "REF-1",
          "workflowId": "%s",
          "metadata": {},
          "contentType": "application/pdf",
          "size": 10,
          "filename": "f.pdf",
          "status": "TOTALLY_UNKNOWN",
          "presignedUrlExpiresAt": "2030-01-01T00:00:00"
        }
        """
        .formatted(FILE_ID, WORKFLOW_ID);
  }

  private String uploadResponseJson() {
    return """
        {
          "id": "%s",
          "referenceId": "REF-1",
          "workflowId": "%s",
          "metadata": {},
          "contentType": "application/pdf",
          "size": 10,
          "filename": "f.pdf",
          "status": "PENDING_UPLOAD",
          "presignedUrl": "https://s3/put",
          "presignedUrlExpiresAt": "2030-01-01T00:00:00"
        }
        """
        .formatted(FILE_ID, WORKFLOW_ID);
  }

  private String documentResponseJson(String presignedUrl) {
    return """
        {
          "id": "%s",
          "referenceId": "REF-1",
          "workflowId": "%s",
          "metadata": {"author": "John"},
          "contentType": "application/pdf",
          "size": 10,
          "filename": "f.pdf",
          "status": "COMPLETED",
          "presignedUrl": "%s",
          "presignedUrlExpiresAt": "2030-01-01T00:00:00"
        }
        """
        .formatted(FILE_ID, WORKFLOW_ID, presignedUrl);
  }

  private String documentResponseJsonNoUrl() {
    return """
        {
          "id": "%s",
          "referenceId": "REF-1",
          "workflowId": "%s",
          "metadata": {},
          "contentType": "application/pdf",
          "size": 10,
          "filename": "f.pdf",
          "status": "PENDING_UPLOAD",
          "presignedUrlExpiresAt": "2030-01-01T00:00:00"
        }
        """
        .formatted(FILE_ID, WORKFLOW_ID);
  }
}
