package io.codehunters.measure;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

import java.util.List;
import org.junit.jupiter.api.Test;

class TagCardinalityLimiterTest {

  private static final int THREADS = 16;

  @Test
  void should_reject_a_tag_value_cap_below_one() {
    assertThatIllegalArgumentException().isThrownBy(() -> new TagCardinalityLimiter(0));
  }

  @Test
  void should_admit_no_more_values_than_the_cap_under_concurrent_calls() throws Exception {
    var limiter = new TagCardinalityLimiter(1);

    List<String> recorded =
        Concurrently.run(THREADS, i -> limiter.limit("orders.place", "customer", "CUST-" + i));

    assertThat(recorded).filteredOn(value -> !value.equals("other")).hasSize(1);
  }

  @Test
  void should_keep_separate_budgets_per_series() {
    var limiter = new TagCardinalityLimiter(1);
    limiter.limit("orders.place#north", "region", "R1");

    assertThat(limiter.limit("orders.place#south", "region", "R2")).isEqualTo("R2");
  }
}
