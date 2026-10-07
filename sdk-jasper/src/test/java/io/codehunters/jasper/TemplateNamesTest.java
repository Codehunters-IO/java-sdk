package io.codehunters.jasper;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class TemplateNamesTest {

  @ParameterizedTest
  @ValueSource(
      strings = {"orders.jrxml", "sales/q1.jasper", "a_b-c.1.jrxml", "x/y/z/report.jasper"})
  void should_accept_a_relative_classpath_template(String name) {
    assertThat(TemplateNames.isTemplate(name)).isTrue();
  }

  @ParameterizedTest
  @ValueSource(
      strings = {
        "/orders.jrxml",
        "../orders.jrxml",
        "a/../orders.jrxml",
        "./orders.jrxml",
        "a//orders.jrxml",
        "a/./orders.jrxml",
        ".hidden.jrxml",
        "file:orders.jrxml",
        "classpath:orders.jrxml",
        "http://host/orders.jrxml",
        "a\\orders.jrxml",
        "orders.txt",
        "orders",
        "orders.jrxml/",
        "orders .jrxml",
        "orders.jrxml\u0000",
        "",
        " "
      })
  void should_reject_anything_but_a_relative_classpath_template(String name) {
    assertThat(TemplateNames.isTemplate(name)).isFalse();
  }

  @ParameterizedTest
  @ValueSource(strings = {"logo.png", "sub/detail.jasper", "styles.jrtx"})
  void should_accept_a_relative_classpath_resource(String name) {
    assertThat(TemplateNames.isResource(name)).isTrue();
  }

  @ParameterizedTest
  @ValueSource(strings = {"/etc/hosts", "../logo.png", "file:/tmp/logo.png", "https://h/logo.png"})
  void should_reject_a_resource_outside_the_classpath_base(String name) {
    assertThat(TemplateNames.isResource(name)).isFalse();
  }

  @org.junit.jupiter.api.Test
  void should_reject_a_null_name() {
    assertThat(TemplateNames.isResource(null)).isFalse();
  }

  @org.junit.jupiter.api.Test
  void should_reject_a_name_longer_than_255_characters() {
    assertThat(TemplateNames.isTemplate("a".repeat(250) + ".jrxml")).isFalse();
  }
}
