package io.codehunters.measure.config;

import java.io.IOException;
import java.io.InputStream;

/** Builds beans whose classes reference types that are absent at runtime. */
final class OptionalDependencyFixtures {

  private static final String BEAN = "io.codehunters.measure.config.UsesOptionalType";
  private static final String MISSING = "io.codehunters.measure.config.OptionalType";

  private OptionalDependencyFixtures() {}

  static Object beanWithMissingOptionalType() throws ReflectiveOperationException {
    Class<?> type = new MissingOptionalTypeLoader().loadClass(BEAN);
    return type.getConstructor().newInstance();
  }

  /** Defines the bean class itself and reports the optional type as missing. */
  private static final class MissingOptionalTypeLoader extends ClassLoader {

    MissingOptionalTypeLoader() {
      super(OptionalDependencyFixtures.class.getClassLoader());
    }

    @Override
    protected Class<?> loadClass(String name, boolean resolve) throws ClassNotFoundException {
      if (MISSING.equals(name)) {
        throw new ClassNotFoundException(name);
      }
      if (BEAN.equals(name)) {
        return defineFromParent(name);
      }
      return super.loadClass(name, resolve);
    }

    private Class<?> defineFromParent(String name) throws ClassNotFoundException {
      String resource = name.replace('.', '/') + ".class";
      try (InputStream in = getParent().getResourceAsStream(resource)) {
        byte[] bytes = in.readAllBytes();
        return defineClass(name, bytes, 0, bytes.length);
      } catch (IOException ex) {
        throw new ClassNotFoundException(name, ex);
      }
    }
  }
}
