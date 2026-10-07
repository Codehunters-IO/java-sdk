package io.codehunters.fileshare.config;

import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.context.annotation.Condition;
import org.springframework.context.annotation.ConditionContext;
import org.springframework.core.type.AnnotatedTypeMetadata;
import org.springframework.util.StringUtils;

/**
 * Matches when a client registration id is set and not blank, so {@code ${VAR:}} placeholders that
 * resolve to nothing leave authentication off.
 */
final class ClientRegistrationIdCondition implements Condition {

  static final String PROPERTY = "codehunters.file-share.auth.client-registration-id";

  @Override
  public boolean matches(ConditionContext context, AnnotatedTypeMetadata metadata) {
    return Binder.get(context.getEnvironment())
        .bind(PROPERTY, String.class)
        .map(StringUtils::hasText)
        .orElse(false);
  }
}
