package io.codehunters.jasper;

/** A JavaBean row: JasperReports reads fields through its getters. */
public class Order {

  private final String name;
  private final Integer amount;

  public Order(String name, Integer amount) {
    this.name = name;
    this.amount = amount;
  }

  /** Rows named {@code row1} to {@code row<count>}, each with its number as the amount. */
  static java.util.List<Order> numbered(int count) {
    return java.util.stream.IntStream.rangeClosed(1, count)
        .mapToObj(i -> new Order("row" + i, i))
        .toList();
  }

  public String getName() {
    return name;
  }

  public Integer getAmount() {
    return amount;
  }
}
