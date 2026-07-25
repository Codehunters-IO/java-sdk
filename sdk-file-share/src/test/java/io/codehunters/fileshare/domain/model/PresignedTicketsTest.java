package io.codehunters.fileshare.domain.model;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.net.URI;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class PresignedTicketsTest {

  @Test
  void upload_ticket_uses_PUT() {
    var ref =
        new FileRef(
            UUID.randomUUID(),
            "r",
            UUID.randomUUID(),
            "f",
            "ct",
            1,
            FileStatus.PENDING_UPLOAD,
            Map.of());
    var ticket =
        new PresignedUploadTicket(ref, URI.create("https://s3/example"), LocalDateTime.now());
    assertEquals("PUT", ticket.httpMethod());
  }

  @Test
  void download_ticket_uses_GET() {
    var ref =
        new FileRef(
            UUID.randomUUID(),
            "r",
            UUID.randomUUID(),
            "f",
            "ct",
            1,
            FileStatus.COMPLETED,
            Map.of());
    var ticket =
        new PresignedDownloadTicket(ref, URI.create("https://s3/example"), LocalDateTime.now());
    assertEquals("GET", ticket.httpMethod());
  }
}
