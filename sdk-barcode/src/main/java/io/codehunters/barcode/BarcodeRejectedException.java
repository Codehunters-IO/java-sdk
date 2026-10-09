package io.codehunters.barcode;

import io.codehunters.commons.error.ClientInputException;
import java.io.Serial;

/**
 * The reader or renderer refuses what it was given, typically by a client: an image to decode
 * ({@value #IMAGE_REJECTED}) or a render request ({@value #CONTENT_REJECTED}). Messages describe
 * the rule that was broken and never repeat the content, so they are safe to return as a 400
 * detail; sdk-web does so out of the box.
 *
 * <p>Still an {@link IllegalArgumentException}, which the reader and renderer threw before. A
 * missing ({@code null}) content counts as client input and is {@value #CONTENT_REJECTED}, like an
 * empty one; any other missing argument, such as a {@code null} format or stream, is a programming
 * error and stays a plain {@code IllegalArgumentException}.
 */
public final class BarcodeRejectedException extends ClientInputException {

  /**
   * An image {@link BarcodeReader} will not decode: a format other than PNG, JPEG, GIF or BMP, more
   * pixels or decode memory than the {@link BarcodeLimits}, or bytes that cannot be read as an
   * image.
   */
  public static final String IMAGE_REJECTED = "BARCODE_IMAGE_REJECTED";

  /**
   * A {@link BarcodeRequest} that cannot be rendered: content that is missing, breaks the rules of
   * its format or does not fit the symbol, a size or margin out of range, or an image larger than
   * the {@link BarcodeLimits}.
   */
  public static final String CONTENT_REJECTED = "BARCODE_CONTENT_REJECTED";

  @Serial private static final long serialVersionUID = 1L;

  private BarcodeRejectedException(String code, String message, Throwable cause) {
    super(code, message, cause);
  }

  /**
   * An image the reader will not decode.
   *
   * @param message the rule broken, without the image's content
   * @param cause the failure behind it, for the server log only; may be {@code null}
   * @return the exception, with code {@value #IMAGE_REJECTED}
   */
  public static BarcodeRejectedException image(String message, Throwable cause) {
    return new BarcodeRejectedException(IMAGE_REJECTED, message, cause);
  }

  /**
   * A render request that cannot be honoured.
   *
   * @param message the rule broken, without the content
   * @return the exception, with code {@value #CONTENT_REJECTED}
   */
  public static BarcodeRejectedException content(String message) {
    return new BarcodeRejectedException(CONTENT_REJECTED, message, null);
  }
}
