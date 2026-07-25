package io.codehunters.fileshare.domain.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class FileRefTest {

  @Test
  void should_hold_all_fields() {
    UUID id = UUID.randomUUID();
    UUID workflowId = UUID.randomUUID();
    FileRef ref =
        new FileRef(
            id,
            "REF-1",
            workflowId,
            "doc.pdf",
            "application/pdf",
            1_024L,
            FileStatus.COMPLETED,
            Map.of("k", "v"));

    assertEquals(id, ref.id());
    assertEquals("REF-1", ref.referenceId());
    assertEquals(workflowId, ref.workflowId());
    assertEquals("doc.pdf", ref.filename());
    assertEquals(1_024L, ref.size());
    assertEquals(FileStatus.COMPLETED, ref.status());
    assertEquals("v", ref.metadata().get("k"));
  }

  @Test
  void should_compare_by_value() {
    UUID id = UUID.randomUUID();
    FileRef a = new FileRef(id, "r", null, "f", "ct", 1L, FileStatus.PENDING_UPLOAD, Map.of());
    FileRef b = new FileRef(id, "r", null, "f", "ct", 1L, FileStatus.PENDING_UPLOAD, Map.of());
    FileRef c =
        new FileRef(UUID.randomUUID(), "r", null, "f", "ct", 1L, FileStatus.COMPLETED, Map.of());
    assertEquals(a, b);
    assertNotEquals(a, c);
  }
}
