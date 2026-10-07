package io.codehunters.measure;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalStateException;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;

@ExtendWith(OutputCaptureExtension.class)
class MeasuredBeanValidatorTest {

  private final MeasuredBeanValidator validator = new MeasuredBeanValidator();

  @Test
  void should_fail_when_beans_share_a_name_with_different_tag_keys() {
    validator.postProcessAfterInitialization(new ByRegion(), "byRegion");

    assertThatIllegalStateException()
        .isThrownBy(() -> validator.postProcessAfterInitialization(new ByChannel(), "byChannel"))
        .withMessageContaining("shared.name")
        .withMessageContaining("explicit name");
  }

  @Test
  void should_accept_beans_that_share_a_name_with_the_same_tag_keys() {
    validator.postProcessAfterInitialization(new ByRegion(), "byRegion");
    var sameKeys = new SameKeys();

    assertThat(validator.postProcessAfterInitialization(sameKeys, "sameKeys")).isSameAs(sameKeys);
  }

  @Test
  void should_fail_when_two_default_named_classes_declare_different_tags() {
    validator.postProcessAfterInitialization(new PlainClass(), "plain");

    assertThatIllegalStateException()
        .isThrownBy(() -> validator.postProcessAfterInitialization(new TaggedClass(), "tagged"))
        .withMessageContaining(MeasuredDefinition.DEFAULT_NAME);
  }

  @Test
  void should_validate_a_class_annotation_inherited_from_a_superclass() {
    assertThatIllegalStateException()
        .isThrownBy(() -> validator.postProcessAfterInitialization(new ChildOfOdd(), "child"))
        .withMessageContaining("key/value pairs");
  }

  @Test
  void should_validate_an_inherited_method_annotation() {
    assertThatIllegalStateException()
        .isThrownBy(() -> validator.postProcessAfterInitialization(new ChildOfOddMethod(), "child"))
        .withMessageContaining("key/value pairs");
  }

  @Test
  void should_warn_when_a_measured_method_cannot_be_proxied(CapturedOutput output) {
    validator.postProcessAfterInitialization(new Unproxyable(), "unproxyable");

    assertThat(output).contains("Unproxyable.finalCall").contains("never measured");
  }

  @Test
  void should_warn_when_only_an_interface_is_annotated(CapturedOutput output) {
    validator.postProcessAfterInitialization(new AnnotatedInterfaceImpl(), "impl");

    assertThat(output).contains("AnnotatedInterface").contains("never measured");
  }

  @Test
  void should_skip_a_bean_whose_class_cannot_be_introspected() throws Exception {
    Object bean = OptionalDependencyFixtures.beanWithMissingOptionalType();

    assertThat(validator.postProcessAfterInitialization(bean, "partial")).isSameAs(bean);
  }

  static class ByRegion {
    @Measured(
        name = "shared.name",
        tags = {"region", "eu"})
    void call() {}
  }

  static class ByChannel {
    @Measured(
        name = "shared.name",
        tags = {"channel", "web"})
    void call() {}
  }

  static class SameKeys {
    @Measured(
        name = "shared.name",
        tags = {"region", "us"})
    void call() {}
  }

  @Measured
  static class PlainClass {
    void call() {}
  }

  @Measured(tags = {"layer", "repository"})
  static class TaggedClass {
    void call() {}
  }

  @Measured(
      name = "odd",
      tags = {"region"})
  static class OddBase {
    void call() {}
  }

  static class ChildOfOdd extends OddBase {}

  static class OddMethodBase {
    @Measured(
        name = "odd.method",
        tags = {"region"})
    void call() {}
  }

  static class ChildOfOddMethod extends OddMethodBase {}

  static class Unproxyable {
    @Measured(name = "unproxyable")
    final void finalCall() {}
  }

  interface AnnotatedInterface {
    @Measured(name = "interface.only")
    void call();
  }

  static class AnnotatedInterfaceImpl implements AnnotatedInterface {
    @Override
    public void call() {}
  }
}
