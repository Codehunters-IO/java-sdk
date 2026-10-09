package io.codehunters.fileshare.model;

import java.util.Map;
import java.util.UUID;

/** Valid file references for tests; each test changes only the field it is about. */
public final class FileRefFixtures {

  public static final UUID FILE_ID = UUID.fromString("11111111-1111-1111-1111-111111111111");
  public static final UUID WORKFLOW_ID = UUID.fromString("22222222-2222-2222-2222-222222222222");
  public static final String REFERENCE_ID = "REF-1";
  public static final String FILENAME = "f.pdf";
  public static final String CONTENT_TYPE = "application/pdf";

  private FileRefFixtures() {}

  public static FileRef fileRef() {
    return new FileRef(
        FILE_ID,
        REFERENCE_ID,
        WORKFLOW_ID,
        FILENAME,
        CONTENT_TYPE,
        10L,
        FileStatus.COMPLETED,
        Map.of("author", "John"));
  }
}
