package io.codehunters.web.errors.servlet.fixture;

import java.util.Locale;
import org.springframework.context.MessageSource;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.support.StaticMessageSource;

/** The sample application's message bundle, in English and Spanish. */
@Configuration(proxyBeanMethods = false)
public class SampleMessages {

  @Bean
  MessageSource messageSource() {
    var messages = new StaticMessageSource();
    messages.addMessage("error.ORDER_NOT_FOUND", Locale.ENGLISH, "Order {0} was not found");
    messages.addMessage("error.ORDER_NOT_FOUND", Locale.of("es"), "No existe el pedido {0}");
    messages.addMessage("web-errors.NOT_FOUND", Locale.of("es"), "Recurso inexistente");
    messages.addMessage("typeMismatch", Locale.of("es"), "Valor con formato incorrecto");
    return messages;
  }
}
