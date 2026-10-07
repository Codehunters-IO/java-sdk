package io.codehunters.jasper;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.function.Supplier;
import net.sf.jasperreports.engine.JRDataSource;
import net.sf.jasperreports.engine.JREmptyDataSource;

/**
 * The rows a report is filled with. A {@link JRDataSource} is consumed as it is read, so a report
 * takes rows or a supplier of fresh sources, never a source itself.
 */
public sealed interface ReportData {

  /**
   * No rows: the report is filled once, from its parameters alone.
   *
   * @return the empty data
   */
  static ReportData empty() {
    return Empty.INSTANCE;
  }

  /**
   * One row per bean; fields are read through JavaBean getters or record components, and a dotted
   * field ({@code customer.name}) walks nested ones.
   *
   * @param rows the beans, copied; none may be null
   * @return the data
   */
  static ReportData beans(Collection<?> rows) {
    return new Beans(rows == null ? null : new ArrayList<>(rows));
  }

  /**
   * Rows from a source the supplier opens for each render.
   *
   * @param supplier opens a fresh source each time it is called
   * @return the data
   */
  static ReportData source(Supplier<? extends JRDataSource> supplier) {
    return new Source(supplier);
  }

  /**
   * Opens a source over the rows.
   *
   * @return a source read once by one fill
   */
  JRDataSource open();

  /** No rows. */
  record Empty() implements ReportData {
    private static final Empty INSTANCE = new Empty();

    @Override
    public JRDataSource open() {
      return new JREmptyDataSource();
    }

    @Override
    public String toString() {
      return "no rows";
    }
  }

  /**
   * One row per bean.
   *
   * @param rows the beans
   */
  record Beans(List<?> rows) implements ReportData {

    /** Copies the rows. */
    public Beans {
      if (rows == null || rows.stream().anyMatch(Objects::isNull)) {
        throw new IllegalArgumentException("rows must not be null or contain null");
      }
      rows = List.copyOf(rows);
    }

    @Override
    public JRDataSource open() {
      return new BeanRows(rows);
    }

    @Override
    public String toString() {
      return rows.size() == 1 ? "1 row" : rows.size() + " rows";
    }
  }

  /**
   * Rows from a supplied source.
   *
   * @param supplier opens a fresh source each time
   */
  record Source(Supplier<? extends JRDataSource> supplier) implements ReportData {

    /** Checks the supplier. */
    public Source {
      if (supplier == null) {
        throw new IllegalArgumentException("data source supplier must not be null");
      }
    }

    @Override
    public JRDataSource open() {
      JRDataSource source = supplier.get();
      if (source == null) {
        throw new ReportException("the data source supplier returned null");
      }
      return source;
    }

    @Override
    public String toString() {
      return "data source";
    }
  }
}
