package io.codehunters.barcode.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

import io.codehunters.barcode.BarcodeFormat;
import io.codehunters.barcode.BarcodeReader;
import io.codehunters.barcode.BarcodeRenderer;
import io.codehunters.barcode.BarcodeRequest;
import java.io.ByteArrayInputStream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

class BarcodeAutoConfigurationTest {

  private final ApplicationContextRunner runner =
      new ApplicationContextRunner()
          .withConfiguration(AutoConfigurations.of(BarcodeAutoConfiguration.class));

  @Configuration(proxyBeanMethods = false)
  static class OwnBeans {
    static final BarcodeRenderer RENDERER = new BarcodeRenderer();
    static final BarcodeReader READER = new BarcodeReader();

    @Bean
    BarcodeRenderer ownRenderer() {
      return RENDERER;
    }

    @Bean
    BarcodeReader ownReader() {
      return READER;
    }
  }

  @Test
  void should_register_a_renderer_and_a_reader_that_work_together() {
    runner.run(
        context -> {
          byte[] png =
              context
                  .getBean(BarcodeRenderer.class)
                  .toPng(BarcodeRequest.of(BarcodeFormat.QR_CODE, "42", 200, 200));

          assertThat(context.getBean(BarcodeReader.class).read(new ByteArrayInputStream(png)))
              .hasValueSatisfying(decoded -> assertThat(decoded.content()).isEqualTo("42"));
        });
  }

  @Test
  void should_apply_the_configured_size_limits() {
    runner
        .withPropertyValues("codehunters.barcode.max-width=100")
        .run(
            context ->
                assertThatIllegalArgumentException()
                    .isThrownBy(
                        () ->
                            context
                                .getBean(BarcodeRenderer.class)
                                .toPng(BarcodeRequest.of(BarcodeFormat.QR_CODE, "42", 200, 200)))
                    .withMessage("the requested size exceeds the limit of 100 x 4096 pixels"));
  }

  @Test
  void should_apply_the_configured_decode_limit() {
    runner
        .withPropertyValues("codehunters.barcode.max-decode-pixels=100")
        .run(
            context -> {
              byte[] png =
                  context
                      .getBean(BarcodeRenderer.class)
                      .toPng(BarcodeRequest.of(BarcodeFormat.QR_CODE, "42", 200, 200));

              assertThatIllegalArgumentException()
                  .isThrownBy(
                      () ->
                          context.getBean(BarcodeReader.class).read(new ByteArrayInputStream(png)))
                  .withMessage("the image exceeds the limit of 100 pixels");
            });
  }

  @Test
  void should_back_off_when_the_application_defines_its_own_beans() {
    runner
        .withUserConfiguration(OwnBeans.class)
        .run(
            context -> {
              assertThat(context).getBean(BarcodeRenderer.class).isSameAs(OwnBeans.RENDERER);
              assertThat(context).getBean(BarcodeReader.class).isSameAs(OwnBeans.READER);
            });
  }

  @Test
  void should_register_nothing_when_disabled() {
    runner
        .withPropertyValues("codehunters.barcode.enabled=false")
        .run(
            context -> {
              assertThat(context).doesNotHaveBean(BarcodeRenderer.class);
              assertThat(context).doesNotHaveBean(BarcodeReader.class);
            });
  }

  @ParameterizedTest
  @CsvSource({
    "max-width=0, codehunters.barcode.max-width must be between 1 and 16384",
    "max-height=20000, codehunters.barcode.max-height must be between 1 and 16384",
    "max-decode-pixels=0, codehunters.barcode.max-decode-pixels must be between 1 and 33554432",
  })
  void should_fail_startup_on_limits_outside_their_range(String property, String message) {
    runner
        .withPropertyValues("codehunters.barcode." + property)
        .run(
            context ->
                assertThat(context)
                    .getFailure()
                    .rootCause()
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessage(message));
  }
}
