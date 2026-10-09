package io.codehunters.web.errors.servlet;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import org.junit.jupiter.api.Test;
import org.springframework.context.MessageSource;
import org.springframework.context.support.StaticMessageSource;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.web.context.request.ServletWebRequest;
import org.springframework.web.context.request.WebRequest;

/**
 * sdk-commons is optional: the handler, loaded where sdk-commons cannot be found, still answers.
 * sdk-web's classes are loaded afresh by a class loader that hides sdk-commons; everything else
 * (Spring, the servlet API) comes from the test class path.
 */
class WithoutCommonsTest {

  private static final String WEB_PACKAGE = "io.codehunters.web.";
  private static final String COMMONS_PACKAGE = "io.codehunters.commons.";

  @Test
  void should_hide_sdk_commons_from_the_isolated_class_loader() {
    var loader = new WithoutCommons();

    assertThatThrownBy(() -> loader.loadClass("io.codehunters.commons.error.ClientInputException"))
        .isInstanceOf(ClassNotFoundException.class);
  }

  @Test
  void should_answer_an_unmapped_exception_when_sdk_commons_is_absent() throws Exception {
    var handlerType = new WithoutCommons().loadClass(WebErrorsExceptionHandler.class.getName());

    ResponseEntity<?> response = handleUnmapped(handlerType, new IllegalArgumentException("x"));

    assertThat(handlerType.getClassLoader()).isInstanceOf(WithoutCommons.class);
    assertThat(response.getStatusCode().value()).isEqualTo(500);
  }

  private static ResponseEntity<?> handleUnmapped(Class<?> handlerType, Exception exception)
      throws ReflectiveOperationException {
    ClassLoader loader = handlerType.getClassLoader();
    Class<?> traceIdsType = loader.loadClass(WEB_PACKAGE + "errors.trace.TraceIdProvider");
    Class<?> settingsType = loader.loadClass(ProblemSettings.class.getName());
    Object traceIds =
        loader
            .loadClass(WEB_PACKAGE + "errors.trace.MdcTraceIdProvider")
            .getConstructor()
            .newInstance();
    Object settings =
        settingsType.getConstructor(URI.class, boolean.class).newInstance(null, false);
    Object handler =
        handlerType
            .getConstructor(MessageSource.class, traceIdsType, settingsType)
            .newInstance(new StaticMessageSource(), traceIds, settings);
    var request =
        new ServletWebRequest(new MockHttpServletRequest(), new MockHttpServletResponse());
    return (ResponseEntity<?>)
        handlerType
            .getMethod("handleUnmapped", Exception.class, WebRequest.class)
            .invoke(handler, exception, request);
  }

  /** Loads sdk-web's classes itself and refuses sdk-commons'. */
  private static final class WithoutCommons extends ClassLoader {

    WithoutCommons() {
      super(WithoutCommonsTest.class.getClassLoader());
    }

    @Override
    protected Class<?> loadClass(String name, boolean resolve) throws ClassNotFoundException {
      if (name.startsWith(COMMONS_PACKAGE)) {
        throw new ClassNotFoundException(name);
      }
      if (!name.startsWith(WEB_PACKAGE)) {
        return super.loadClass(name, resolve);
      }
      synchronized (getClassLoadingLock(name)) {
        Class<?> loaded = findLoadedClass(name);
        return loaded != null ? loaded : define(name);
      }
    }

    private Class<?> define(String name) throws ClassNotFoundException {
      String resource = name.replace('.', '/') + ".class";
      try (InputStream in = getParent().getResourceAsStream(resource)) {
        if (in == null) {
          throw new ClassNotFoundException(name);
        }
        byte[] bytes = in.readAllBytes();
        return defineClass(name, bytes, 0, bytes.length);
      } catch (IOException e) {
        throw new ClassNotFoundException(name, e);
      }
    }
  }
}
