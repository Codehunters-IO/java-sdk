package io.codehunters.jasper;

import java.util.regex.Pattern;

/**
 * Names a report may load from the classpath: relative, made of safe segments, never absolute, a
 * URL or a walk up the tree. Templates and their resources (images, subreports, styles) are
 * application code; these rules keep a name that reaches a report from anywhere else from leaving
 * the base folder.
 */
final class TemplateNames {

  static final String TEMPLATE_RULE =
      "template must be a relative classpath name ending in .jasper or .jrxml, made of letters,"
          + " digits, '.', '_', '-' and '/'";

  private static final int MAX_LENGTH = 255;
  private static final String SEGMENT = "[A-Za-z0-9_][A-Za-z0-9._-]*";
  private static final Pattern PATH = Pattern.compile(SEGMENT + "(/" + SEGMENT + ")*");

  private TemplateNames() {}

  static boolean isResource(String name) {
    return name != null && name.length() <= MAX_LENGTH && PATH.matcher(name).matches();
  }

  static boolean isTemplate(String name) {
    return isResource(name) && (name.endsWith(".jasper") || name.endsWith(".jrxml"));
  }
}
