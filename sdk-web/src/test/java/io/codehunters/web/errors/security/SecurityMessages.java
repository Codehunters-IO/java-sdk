package io.codehunters.web.errors.security;

import java.util.Locale;
import org.springframework.context.MessageSource;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.support.StaticMessageSource;

/** Details for 401 and 403 in English and Spanish. */
@Configuration(proxyBeanMethods = false)
public class SecurityMessages {

  /** The Spanish detail of a 401. */
  public static final String SPANISH_UNAUTHORIZED = "Hace falta autenticarse";

  /** The Spanish detail of a 403. */
  public static final String SPANISH_FORBIDDEN = "Acceso denegado";

  @Bean
  MessageSource messageSource() {
    var messages = new StaticMessageSource();
    messages.addMessage("web-errors.UNAUTHORIZED", Locale.ENGLISH, "Authentication is required");
    messages.addMessage("web-errors.UNAUTHORIZED", Locale.of("es"), SPANISH_UNAUTHORIZED);
    messages.addMessage("web-errors.FORBIDDEN", Locale.ENGLISH, "Access is denied");
    messages.addMessage("web-errors.FORBIDDEN", Locale.of("es"), SPANISH_FORBIDDEN);
    return messages;
  }
}
