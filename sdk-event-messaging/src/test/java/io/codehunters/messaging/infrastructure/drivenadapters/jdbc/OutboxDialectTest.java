package io.codehunters.messaging.infrastructure.drivenadapters.jdbc;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class OutboxDialectTest {

  private static final String PER_KEY_GUARD = "NOT EXISTS";
  private static final String SEQ_ORDER_GUARD = "p.seq < o.seq";
  private static final String SAME_TOPIC_GUARD = "p.topic = o.topic";
  private static final String SAME_KEY_GUARD = "p.message_key = o.message_key";
  private static final String BLOCKING_STATUSES = "p.status IN ('PENDING', 'IN_FLIGHT', 'FAILED')";

  private final OutboxDialect postgres = new PostgresOutboxDialect();
  private final OutboxDialect sqlServer = new SqlServerOutboxDialect();
  private final OutboxDialect generic = new GenericOutboxDialect();

  @Test
  void should_lock_and_skip_candidate_rows_when_dialect_is_postgres() {
    assertThat(postgres.claimSql())
        .contains("FOR UPDATE OF o SKIP LOCKED")
        .contains("RETURNING")
        .contains("ORDER BY o.seq")
        .contains("LIMIT :limit");
  }

  @Test
  void should_guard_per_key_ordering_when_dialect_is_postgres() {
    assertThat(postgres.claimSql())
        .contains(PER_KEY_GUARD)
        .contains(SEQ_ORDER_GUARD)
        .contains(SAME_TOPIC_GUARD)
        .contains(SAME_KEY_GUARD)
        .contains(BLOCKING_STATUSES);
  }

  @Test
  void should_return_seq_column_when_dialect_is_postgres() {
    assertThat(postgres.claimSql()).contains("m.seq");
  }

  @Test
  void should_hint_outer_scan_exactly_once_when_dialect_is_sql_server() {
    String sql = sqlServer.claimSql();

    assertThat(sql.split("WITH \\(UPDLOCK, READPAST, ROWLOCK\\)", -1)).hasSize(2);
    assertThat(sql).contains("OUTPUT inserted.").contains("TOP (:limit)").contains("ORDER BY o.seq");
  }

  @Test
  void should_keep_lock_hints_out_of_guard_when_dialect_is_sql_server() {
    String sql = sqlServer.claimSql();
    String guard = sql.substring(sql.indexOf(PER_KEY_GUARD));

    assertThat(guard).doesNotContain("READPAST").doesNotContain("UPDLOCK");
  }

  @Test
  void should_guard_per_key_ordering_when_dialect_is_sql_server() {
    assertThat(sqlServer.claimSql())
        .contains(PER_KEY_GUARD)
        .contains(SEQ_ORDER_GUARD)
        .contains(SAME_TOPIC_GUARD)
        .contains(SAME_KEY_GUARD)
        .contains(BLOCKING_STATUSES)
        .contains("inserted.seq");
  }

  @Test
  void should_guard_per_key_ordering_when_dialect_is_generic() {
    assertThat(generic.claimSql())
        .contains(PER_KEY_GUARD)
        .contains(SEQ_ORDER_GUARD)
        .contains(SAME_TOPIC_GUARD)
        .contains(SAME_KEY_GUARD)
        .contains(BLOCKING_STATUSES)
        .contains("ORDER BY o.seq");
  }

  @Test
  void should_bind_only_named_parameters_when_any_dialect_builds_claim() {
    assertThat(postgres.claimSql()).contains(":origin", ":now", ":ttlBoundary", ":claimedBy");
    assertThat(sqlServer.claimSql()).contains(":origin", ":now", ":ttlBoundary", ":claimedBy");
    assertThat(generic.claimSql())
        .contains(":origin", ":now", ":ttlBoundary", ":claimedBy", ":limit");
  }

  @Test
  void should_return_claimed_rows_when_dialect_is_postgres_or_sql_server() {
    assertThat(postgres.claimReturnsRows()).isTrue();
    assertThat(sqlServer.claimReturnsRows()).isTrue();
  }

  @Test
  void should_not_return_claimed_rows_when_dialect_is_generic() {
    assertThat(generic.claimReturnsRows()).isFalse();
  }

  @Test
  void should_expose_configuration_name_when_dialect_is_queried() {
    assertThat(postgres.name()).isEqualTo("postgresql");
    assertThat(sqlServer.name()).isEqualTo("sqlserver");
    assertThat(generic.name()).isEqualTo("generic");
  }
}
