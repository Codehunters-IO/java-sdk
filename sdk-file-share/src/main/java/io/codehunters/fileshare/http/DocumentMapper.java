package io.codehunters.fileshare.http;

import io.codehunters.fileshare.FileShareException;
import io.codehunters.fileshare.model.FileRef;
import io.codehunters.fileshare.model.FileStatus;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeParseException;
import java.util.Arrays;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

final class DocumentMapper {

  private static final Logger log = LoggerFactory.getLogger(DocumentMapper.class);

  private DocumentMapper() {}

  static FileRef toFileRef(DocumentHttpResponse response) {
    if (response == null) {
      throw new FileShareException("Empty response from file-share");
    }
    return new FileRef(
        UUID.fromString(response.id()),
        response.referenceId(),
        UUID.fromString(response.workflowId()),
        response.filename(),
        response.contentType(),
        response.size(),
        toStatus(response.status()),
        response.metadata());
  }

  // The service has sent local timestamps; one without an offset is read as UTC.
  static Instant toExpiry(String value) {
    if (value == null) {
      throw new FileShareException("presignedUrlExpiresAt is missing from the response");
    }
    try {
      return OffsetDateTime.parse(value).toInstant();
    } catch (DateTimeParseException withoutOffset) {
      return LocalDateTime.parse(value).toInstant(ZoneOffset.UTC);
    }
  }

  private static FileStatus toStatus(String value) {
    return Arrays.stream(FileStatus.values())
        .filter(status -> status.name().equals(value))
        .findFirst()
        .orElseGet(() -> unknown(value));
  }

  private static FileStatus unknown(String value) {
    if (value != null) {
      log.warn("Unknown file status received from file-share; mapped to UNKNOWN");
    }
    return FileStatus.UNKNOWN;
  }
}
