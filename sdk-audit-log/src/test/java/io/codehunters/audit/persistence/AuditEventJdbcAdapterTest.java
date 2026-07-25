package io.codehunters.audit.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;

import io.codehunters.audit.AuditEvent;
import io.codehunters.audit.AuditPersistenceException;
import java.time.OffsetDateTime;
import java.util.Map;
import java.util.UUID;
import javax.sql.DataSource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabaseBuilder;
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabaseType;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import tools.jackson.databind.json.JsonMapper;

@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = AuditEventJdbcAdapterTest.JdbcTestConfig.class)
class AuditEventJdbcAdapterTest {

  @Autowired private DataSource dataSource;
  @Autowired private JsonMapper jsonMapper;

  private JdbcClient jdbcClient;
  private AuditEventJdbcAdapter adapter;

  @BeforeEach
  void setUp() {
    jdbcClient = JdbcClient.create(dataSource);
    jdbcClient.sql("DELETE FROM audit_event").update();
    adapter = new AuditEventJdbcAdapter(dataSource, jsonMapper, false);
  }

  @Test
  void should_persist_audit_event_to_database() {
    var correlationId = UUID.randomUUID();
    var event = fullEvent(correlationId);

    adapter.publish(event);

    var count = jdbcClient.sql("SELECT COUNT(*) FROM audit_event").query(Integer.class).single();
    assertThat(count).isEqualTo(1);

    var row =
        jdbcClient
            .sql("SELECT * FROM audit_event")
            .query(
                (rs, rowNum) -> {
                  assertThat(rs.getString("id")).isNotNull();
                  assertThat(rs.getString("event_type")).isEqualTo("ORDER");
                  assertThat(rs.getString("action")).isEqualTo("CREATE");
                  assertThat(rs.getString("entity_id")).isEqualTo("ORD-001");
                  assertThat(rs.getString("user_id")).isEqualTo("user-1");
                  assertThat(rs.getString("role")).isEqualTo("ADMIN");
                  assertThat(rs.getString("resource")).isEqualTo("/api/orders");
                  assertThat(rs.getString("resource_id")).isEqualTo("res-001");
                  assertThat(rs.getString("http_method")).isEqualTo("POST");
                  assertThat(rs.getInt("http_status")).isEqualTo(201);
                  assertThat(rs.getString("ip_address")).isEqualTo("10.0.0.1");
                  assertThat(rs.getString("user_agent")).isEqualTo("Mozilla/5.0");
                  assertThat(rs.getBoolean("success")).isTrue();
                  assertThat(rs.getString("error_message")).isNull();
                  assertThat(rs.getInt("duration_ms")).isEqualTo(150);
                  assertThat(rs.getString("source_service")).isEqualTo("order-service");
                  assertThat(rs.getString("correlation_id")).isEqualTo(correlationId.toString());
                  assertThat(rs.getTimestamp("event_created_at")).isNotNull();
                  assertThat(rs.getString("request_body")).contains("amount");
                  assertThat(rs.getString("response_summary")).contains("status");
                  assertThat(rs.getString("metadata")).contains("env");
                  return true;
                })
            .single();
    assertThat(row).isTrue();
  }

  @Test
  void should_persist_error_event_with_null_fields() {
    var event =
        new AuditEvent(
            "PAYMENT",
            null,
            "CHARGE",
            null,
            null,
            null,
            null,
            "ERROR",
            null,
            null,
            OffsetDateTime.now(),
            null,
            null,
            null,
            500,
            null,
            null,
            false,
            "Connection timeout",
            3000,
            null);

    adapter.publish(event);

    var count = jdbcClient.sql("SELECT COUNT(*) FROM audit_event").query(Integer.class).single();
    assertThat(count).isEqualTo(1);
  }

  @Test
  void should_swallow_exception_in_silent_mode() {
    var silentAdapter = new AuditEventJdbcAdapter(brokenDataSource(), jsonMapper, false);

    assertDoesNotThrow(() -> silentAdapter.publish(fullEvent(UUID.randomUUID())));
  }

  @Test
  void should_throw_audit_persistence_exception_in_fail_mode() {
    var failAdapter = new AuditEventJdbcAdapter(brokenDataSource(), jsonMapper, true);

    assertThatThrownBy(() -> failAdapter.publish(fullEvent(UUID.randomUUID())))
        .isInstanceOf(AuditPersistenceException.class)
        .hasMessageContaining("Failed to persist audit event")
        .hasCauseInstanceOf(Exception.class);
  }

  private DataSource brokenDataSource() {
    return new EmbeddedDatabaseBuilder()
        .setType(EmbeddedDatabaseType.H2)
        .generateUniqueName(true)
        .build();
  }

  private AuditEvent fullEvent(UUID correlationId) {
    return new AuditEvent(
        "ORDER",
        "ORD-001",
        "CREATE",
        "user-1",
        "ADMIN",
        "10.0.0.1",
        Map.of("amount", 99.99),
        "SUCCESS",
        correlationId,
        "order-service",
        OffsetDateTime.now(),
        "/api/orders",
        "res-001",
        "POST",
        201,
        "Mozilla/5.0",
        Map.of("status", "ok"),
        true,
        null,
        150,
        Map.of("env", "prod"));
  }

  @Configuration
  static class JdbcTestConfig {

    @Bean
    DataSource dataSource() {
      return new EmbeddedDatabaseBuilder()
          .setType(EmbeddedDatabaseType.H2)
          .addScript("db/test-schema.sql")
          .build();
    }

    @Bean
    JsonMapper jsonMapper() {
      return JsonMapper.builder().build();
    }
  }
}
