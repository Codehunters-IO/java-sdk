package io.codehunters.fileshare.infrastructure;

import io.codehunters.fileshare.domain.exception.FileShareConflictException;
import io.codehunters.fileshare.domain.exception.FileShareException;
import io.codehunters.fileshare.domain.exception.FileShareNotFoundException;
import io.codehunters.fileshare.domain.exception.FileShareUnauthorizedException;
import io.codehunters.fileshare.domain.exception.FileShareUnavailableException;
import io.codehunters.fileshare.domain.exception.FileShareValidationException;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import org.springframework.http.client.ClientHttpResponse;

final class ErrorResponses {

  private static final int MAX_BODY_CHARACTERS = 512;
  private static final int MAX_UTF8_BYTES_PER_CHARACTER = 4;

  private ErrorResponses() {}

  static FileShareException toException(ClientHttpResponse response) throws IOException {
    int status = response.getStatusCode().value();
    String message = "file-share error status=" + status + " body=" + readBody(response);
    return switch (status) {
      case 404 -> new FileShareNotFoundException(message, status);
      case 401, 403 -> new FileShareUnauthorizedException(message, status);
      case 409 -> new FileShareConflictException(message, status);
      default -> byFamily(message, status);
    };
  }

  private static FileShareException byFamily(String message, int status) {
    if (status >= 400 && status < 500) {
      return new FileShareValidationException(message, status);
    }
    if (status >= 500) {
      return new FileShareUnavailableException(message, status);
    }
    return new FileShareException(message, status);
  }

  private static String readBody(ClientHttpResponse response) throws IOException {
    byte[] head = response.getBody().readNBytes(MAX_BODY_CHARACTERS * MAX_UTF8_BYTES_PER_CHARACTER);
    String text = new String(head, StandardCharsets.UTF_8);
    int characters = Math.min(MAX_BODY_CHARACTERS, text.codePointCount(0, text.length()));
    return text.substring(0, text.offsetByCodePoints(0, characters));
  }
}
