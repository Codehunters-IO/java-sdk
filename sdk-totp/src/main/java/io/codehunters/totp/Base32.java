package io.codehunters.totp;

import java.util.Locale;
import java.util.Set;

final class Base32 {

  private static final String ALPHABET = "ABCDEFGHIJKLMNOPQRSTUVWXYZ234567";

  /** Unpadded lengths, modulo 8, that no whole number of bytes encodes to (RFC 4648 §6). */
  private static final Set<Integer> INVALID_LENGTHS = Set.of(1, 3, 6);

  private Base32() {}

  /**
   * Decodes RFC 4648 Base32. Case is ignored and spaces may separate groups, as authenticator apps
   * display them. Padding is optional but, when present, only at the end and up to a multiple of
   * eight characters. Lengths that cannot come from whole bytes and non-zero trailing bits are
   * rejected, so a mistyped secret fails here instead of decoding to a different key.
   */
  static byte[] decode(String encoded) {
    String input = stripPadding(encoded.replace(" ", "").toUpperCase(Locale.ROOT));
    if (INVALID_LENGTHS.contains(input.length() % 8)) {
      throw new TotpException("Invalid Base32 length in secret");
    }
    byte[] result = new byte[input.length() * 5 / 8];
    int buffer = 0;
    int bitsLeft = 0;
    int index = 0;

    for (char c : input.toCharArray()) {
      buffer = (buffer << 5) | valueOf(c);
      bitsLeft += 5;
      if (bitsLeft >= 8) {
        bitsLeft -= 8;
        result[index++] = (byte) (buffer >> bitsLeft);
      }
    }
    if ((buffer & ((1 << bitsLeft) - 1)) != 0) {
      throw new TotpException("Invalid Base32 trailing bits in secret");
    }
    return result;
  }

  private static String stripPadding(String input) {
    int end = input.indexOf('=');
    if (end < 0) {
      return input;
    }
    if (input.length() % 8 != 0 || input.substring(end).chars().anyMatch(c -> c != '=')) {
      throw new TotpException("Invalid Base32 padding in secret");
    }
    return input.substring(0, end);
  }

  private static int valueOf(char c) {
    int val = ALPHABET.indexOf(c);
    if (val < 0) {
      throw new TotpException("Invalid Base32 character in secret");
    }
    return val;
  }

  static String encode(byte[] data) {
    StringBuilder result = new StringBuilder();
    int buffer = 0;
    int bitsLeft = 0;

    for (byte b : data) {
      buffer = (buffer << 8) | (b & 0xFF);
      bitsLeft += 8;
      while (bitsLeft >= 5) {
        bitsLeft -= 5;
        result.append(ALPHABET.charAt((buffer >> bitsLeft) & 0x1F));
      }
    }
    if (bitsLeft > 0) {
      result.append(ALPHABET.charAt((buffer << (5 - bitsLeft)) & 0x1F));
    }
    while (result.length() % 8 != 0) {
      result.append('=');
    }
    return result.toString();
  }
}
