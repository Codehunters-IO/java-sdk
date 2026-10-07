package io.codehunters.jasper;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import net.sf.jasperreports.engine.JREmptyDataSource;
import net.sf.jasperreports.engine.data.JRBeanCollectionDataSource;
import org.junit.jupiter.api.Test;

class ReportDataTest {

  @Test
  void should_open_one_empty_record_for_an_empty_report() {
    assertThat(ReportData.empty().open()).isInstanceOf(JREmptyDataSource.class);
  }

  @Test
  void should_open_a_bean_source_over_the_rows() {
    assertThat(ReportData.beans(List.of("a")).open())
        .isInstanceOf(JRBeanCollectionDataSource.class);
  }

  @Test
  void should_copy_the_rows_so_later_changes_do_not_leak_in() {
    var rows = new ArrayList<Object>(List.of("a"));
    var data = ReportData.beans(rows);

    rows.add("b");

    assertThat(data).isEqualTo(ReportData.beans(List.of("a")));
  }

  @Test
  void should_open_a_fresh_source_from_the_supplier_each_time() {
    var data = ReportData.source(JREmptyDataSource::new);

    assertThat(data.open()).isNotSameAs(data.open());
  }

  @Test
  void should_reject_rows_containing_null() {
    assertThatIllegalArgumentException()
        .isThrownBy(() -> ReportData.beans(Arrays.asList("a", null)))
        .withMessage("rows must not be null or contain null");
  }

  @Test
  void should_reject_a_missing_supplier() {
    assertThatIllegalArgumentException()
        .isThrownBy(() -> ReportData.source(null))
        .withMessage("data source supplier must not be null");
  }

  @Test
  void should_fail_when_the_supplier_returns_null() {
    assertThatThrownBy(() -> ReportData.source(() -> null).open())
        .isInstanceOf(ReportException.class)
        .hasMessage("the data source supplier returned null");
  }

  @Test
  void should_count_the_rows_without_showing_them() {
    assertThat(ReportData.beans(List.of("secret-1", "secret-2")).toString()).isEqualTo("2 rows");
    assertThat(ReportData.empty().toString()).isEqualTo("no rows");
    assertThat(ReportData.source(JREmptyDataSource::new).toString()).isEqualTo("data source");
  }
}
