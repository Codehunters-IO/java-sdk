package io.codehunters.totp;

import java.util.Locale;

final class Base32 {

  private static final String ALPHABET = "ABCDEFGHIJKLMNOPQRSTUVWXYZ234567";

  private Base32() {}

  static byte[] decode(String encoded) {
    String input = encoded.toUpperCase(Locale.ROOT).replaceAll("[=\\s]", "");
    int totalBits = input.length() * 5;
    byte[] result = new byte[totalBits / 8];
    int buffer = 0;
    int bitsLeft = 0;
    int index = 0;

    for (char c : input.toCharArray()) {
      int val = ALPHABET.indexOf(c);
      if (val < 0) {
        throw new TotpException("Invalid Base32 character in secret");
      }
      buffer = (buffer << 5) | val;
      bitsLeft += 5;
      if (bitsLeft >= 8) {
        bitsLeft -= 8;
        result[index++] = (byte) (buffer >> bitsLeft);
      }
    }
    return result;
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
