package io.codehunters.audit;

import java.io.File;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Path;
import java.util.Arrays;

/**
 * Loads the test classpath as if Reactor were absent: the SDK's classes are loaded afresh, and any
 * {@code reactor.*} class is reported missing.
 */
final class ReactorHidingClassLoader extends URLClassLoader {

  ReactorHidingClassLoader() {
    super(classpath(), ClassLoader.getPlatformClassLoader());
  }

  @Override
  protected Class<?> loadClass(String name, boolean resolve) throws ClassNotFoundException {
    if (name.startsWith("reactor.")) {
      throw new ClassNotFoundException(name);
    }
    return super.loadClass(name, resolve);
  }

  private static URL[] classpath() {
    return Arrays.stream(System.getProperty("java.class.path").split(File.pathSeparator))
        .map(ReactorHidingClassLoader::toUrl)
        .toArray(URL[]::new);
  }

  private static URL toUrl(String entry) {
    try {
      return Path.of(entry).toUri().toURL();
    } catch (MalformedURLException ex) {
      throw new IllegalStateException(ex);
    }
  }
}
