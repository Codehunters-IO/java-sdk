package io.codehunters.barcode.internal;

import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel;
import io.codehunters.barcode.BarcodeFormat;
import io.codehunters.barcode.ErrorCorrection;
import java.util.Arrays;
import java.util.Optional;

/** Maps the SDK's formats and error correction levels to ZXing's. Not API. */
public final class ZxingMapping {

  private ZxingMapping() {}

  /**
   * @param format an SDK format
   * @return the ZXing format it encodes and decodes as
   */
  public static com.google.zxing.BarcodeFormat toZxing(BarcodeFormat format) {
    return switch (format) {
      case QR_CODE -> com.google.zxing.BarcodeFormat.QR_CODE;
      case DATA_MATRIX -> com.google.zxing.BarcodeFormat.DATA_MATRIX;
      case PDF_417 -> com.google.zxing.BarcodeFormat.PDF_417;
      case CODE_128 -> com.google.zxing.BarcodeFormat.CODE_128;
      case CODE_39 -> com.google.zxing.BarcodeFormat.CODE_39;
      case EAN_13 -> com.google.zxing.BarcodeFormat.EAN_13;
      case UPC_A -> com.google.zxing.BarcodeFormat.UPC_A;
      case ITF -> com.google.zxing.BarcodeFormat.ITF;
    };
  }

  /**
   * @param zxing a format ZXing decoded
   * @return the SDK format, or empty for a symbology the SDK does not support
   */
  public static Optional<BarcodeFormat> fromZxing(com.google.zxing.BarcodeFormat zxing) {
    return Arrays.stream(BarcodeFormat.values())
        .filter(format -> toZxing(format) == zxing)
        .findFirst();
  }

  /**
   * @param level an SDK error correction level
   * @return the ZXing level
   */
  public static ErrorCorrectionLevel toZxing(ErrorCorrection level) {
    return switch (level) {
      case L -> ErrorCorrectionLevel.L;
      case M -> ErrorCorrectionLevel.M;
      case Q -> ErrorCorrectionLevel.Q;
      case H -> ErrorCorrectionLevel.H;
    };
  }
}
