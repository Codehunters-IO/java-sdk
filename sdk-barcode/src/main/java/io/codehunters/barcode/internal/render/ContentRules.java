package io.codehunters.barcode.internal.render;

import io.codehunters.barcode.BarcodeFormat;
import io.codehunters.barcode.BarcodeRejectedException;
import java.util.regex.Pattern;

/**
 * The content each symbology accepts. Messages describe the rule broken and never repeat the
 * content, which may be a ticket, a token or personal data.
 *
 * <p>The 2D maxima are the longest all-digit content each symbol holds (QR Code at error correction
 * L); text and non-ASCII content fill a symbol sooner, which only encoding can tell.
 */
public final class ContentRules {

  private static final Pattern DIGITS = Pattern.compile("[0-9]+");
  private static final Pattern CODE_39 = Pattern.compile("[0-9A-Z \\-.$/+%]+");
  private static final Pattern PRINTABLE_ASCII = Pattern.compile("[\\x20-\\x7E]+");
  private static final int LINEAR_MAX = 80;

  private ContentRules() {}

  public static void check(BarcodeFormat format, String content) {
    switch (format) {
      case EAN_13 -> gtin("EAN-13", content, 12);
      case UPC_A -> gtin("UPC-A", content, 11);
      case ITF -> itf(content);
      case CODE_39 -> linear("CODE 39", content, CODE_39, "0-9, A-Z, space and - . $ / + %");
      case CODE_128 -> linear("CODE 128", content, PRINTABLE_ASCII, "printable ASCII");
      case QR_CODE -> maxLength("QR_CODE", content, 7089);
      case DATA_MATRIX -> maxLength("DATA_MATRIX", content, 3112);
      case PDF_417 -> maxLength("PDF_417", content, 2686);
    }
  }

  private static void gtin(String name, String content, int dataDigits) {
    int length = content.length();
    if (!DIGITS.matcher(content).matches() || length < dataDigits || length > dataDigits + 1) {
      throw BarcodeRejectedException.content(
          name + " content must be " + dataDigits + " or " + (dataDigits + 1) + " digits");
    }
    if (length > dataDigits
        && checkDigit(content, dataDigits) != content.charAt(dataDigits) - '0') {
      throw BarcodeRejectedException.content(name + " check digit does not match");
    }
  }

  /** GS1 check digit: data digits weighted 3, 1, 3, ... from the rightmost one. */
  private static int checkDigit(String content, int dataDigits) {
    int sum = 0;
    for (int i = 0; i < dataDigits; i++) {
      int weight = (dataDigits - i) % 2 == 1 ? 3 : 1;
      sum += (content.charAt(i) - '0') * weight;
    }
    return (10 - sum % 10) % 10;
  }

  private static void itf(String content) {
    if (!DIGITS.matcher(content).matches() || content.length() % 2 != 0) {
      throw BarcodeRejectedException.content("ITF content must be an even number of digits");
    }
    maxLength("ITF", content, LINEAR_MAX);
  }

  private static void linear(String name, String content, Pattern alphabet, String allowed) {
    maxLength(name, content, LINEAR_MAX);
    if (!alphabet.matcher(content).matches()) {
      throw BarcodeRejectedException.content(name + " content may only contain " + allowed);
    }
  }

  private static void maxLength(String name, String content, int max) {
    if (content.length() > max) {
      throw BarcodeRejectedException.content(
          name + " content must be at most " + max + " characters");
    }
  }
}
