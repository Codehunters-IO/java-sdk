package io.codehunters.commons.totp;

import java.nio.charset.StandardCharsets;
import java.util.HexFormat;
import java.util.Objects;

/**
 * Builds {@code otpauth://totp/} key URIs in the format authenticator apps import. Components are
 * percent-encoded as UTF-8, leaving only RFC 3986 unreserved characters as they are.
 */
final class TotpUri {

  private static final HexFormat HEX = HexFormat.of().withUpperCase();

  private TotpUri() {}

  /**
   * Who the key belongs to: the service and the user's account in it.
   *
   * @param issuer the service name; not blank, no {@code ':'}, no control characters
   * @param account the account name; same rules as {@code issuer}
   */
  record Label(String issuer, String account) {

    Label {
      issuer = validated(issuer, "issuer");
      account = validated(account, "account");
    }
  }

  static String of(TotpConfig config, TotpSecret secret, Label label) {
    Objects.requireNonNull(secret, "secret must not be null");
    String issuer = encode(label.issuer());
    return "otpauth://totp/"
        + issuer
        + ":"
        + encode(label.account())
        + "?secret="
        + unpadded(secret.toBase32())
        + "&issuer="
        + issuer
        + parameters(config);
  }

  private static String parameters(TotpConfig config) {
    return "&algorithm="
        + uriName(config.algorithm())
        + "&digits="
        + config.digits().value()
        + "&period="
        + config.timeStep().getSeconds();
  }

  private static String uriName(TotpAlgorithm algorithm) {
    return switch (algorithm) {
      case HMAC_SHA1 -> "SHA1";
      case HMAC_SHA256 -> "SHA256";
      case HMAC_SHA512 -> "SHA512";
    };
  }

  private static String unpadded(String base32) {
    int end = base32.indexOf('=');
    return end < 0 ? base32 : base32.substring(0, end);
  }

  // The message names the field but never repeats the value, which may identify a user.
  private static String validated(String value, String name) {
    Objects.requireNonNull(value, name + " must not be null");
    if (value.isBlank()) {
      throw new IllegalArgumentException(name + " must not be blank");
    }
    if (value.codePoints().anyMatch(TotpUri::isForbidden)) {
      throw new IllegalArgumentException(
          name + " must not contain ':', control or format characters");
    }
    return value;
  }

  // Format characters (bidi overrides, zero-width spaces) are invisible and could make an
  // authenticator app show a different label than the one encoded.
  private static boolean isForbidden(int codePoint) {
    return codePoint == ':'
        || Character.isISOControl(codePoint)
        || Character.getType(codePoint) == Character.FORMAT;
  }

  private static String encode(String value) {
    var encoded = new StringBuilder();
    for (byte b : value.getBytes(StandardCharsets.UTF_8)) {
      encoded.append(isUnreserved(b) ? String.valueOf((char) b) : "%" + HEX.toHexDigits(b));
    }
    return encoded.toString();
  }

  private static boolean isUnreserved(byte b) {
    return (b >= 'A' && b <= 'Z')
        || (b >= 'a' && b <= 'z')
        || (b >= '0' && b <= '9')
        || b == '-'
        || b == '.'
        || b == '_'
        || b == '~';
  }
}
