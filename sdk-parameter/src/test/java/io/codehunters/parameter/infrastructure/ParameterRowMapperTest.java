package io.codehunters.parameter.infrastructure;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import io.codehunters.parameter.domain.exception.ParameterAccessException;
import java.sql.ResultSet;
import java.sql.SQLException;
import org.junit.jupiter.api.Test;

class ParameterRowMapperTest {

  private static final String RAW_ID = "not-a-uuid-SECRET";

  private final ResultSet row = mock(ResultSet.class);

  @Test
  void should_report_a_malformed_uuid_without_its_raw_value() throws SQLException {
    when(row.getObject("id")).thenReturn(RAW_ID);

    assertThatThrownBy(() -> new ParameterRowMapper().mapRow(row, 0))
        .isInstanceOf(ParameterAccessException.class)
        .hasMessageContaining("id")
        .hasMessageNotContaining("SECRET")
        .hasNoCause();
  }
}
