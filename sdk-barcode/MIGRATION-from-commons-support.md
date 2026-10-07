# Migrating from commons-util-barcode-support

`io.codehunters.commons:commons-util-barcode-support` 5.1.0 (`codehuntersio-java-commons-support`) is
replaced by `io.codehunters:sdk-barcode` 1.0.0. The code was rewritten; nothing is source
compatible.

| commons-support | sdk-barcode |
|---|---|
| `BarcodeUtil.generateQRCodeImage(text, width, height)` | `renderer.toPng(BarcodeRequest.of(QR_CODE, text, width, height))` |
| `generateEAN13BarcodeImage`, `generateUPCABarcodeImage`, `generateCode39BarcodeImage`, `generateCode128BarcodeImage`, `generateITFBarcodeImage`, `generatePDF417BarcodeImage` (fixed 160 dpi, `BufferedImage`) | `BarcodeRequest.of(<format>, text, width, height)` rendered with `toPng` or `render(..., ImageFormat.PNG \| SVG, out)` |
| `BarcodeUtil.readQRCodeImage(File)` (`Result`, checked ZXing exceptions) | `reader.read(InputStream)`: `Optional<DecodedBarcode>`, any supported barcode format in a PNG, JPEG, GIF or BMP image |
| `BarcodeUtil.writeFile(image, properties)`, `BarcodeProperties` (path, resolution, image type, orientation) | Write the bytes yourself; size, margin and format are on `BarcodeRequest` |
| barcode4j 2.1 and ZXing `core`/`javase` 3.4.0 | ZXing `core` 3.5.4 only |

## Behaviour changes

- **Content is validated** per format before encoding, including EAN-13 and UPC-A check digits.
  barcode4j accepted some invalid content silently or failed with its own exceptions.
- **Sizes are pixels and are limited.** The old 1D methods ignored any size and always drew at
  160 dpi; requests now say how large the image is, and the `BarcodeLimits` cap it.
- **Decoding checks the image size and memory first** and reads only from a stream; it no longer
  opens a `FileInputStream` it never closed. Only PNG, JPEG, GIF and BMP are read, and large images
  are subsampled to about 4 megapixels.
- **Exceptions are unchecked** (`IllegalArgumentException`, `UncheckedIOException`) and never quote
  the content.

## Intentionally not migrated

- **barcode4j.** Unmaintained since 2012; ZXing covers every format it was used for.
- **JPEG output and `BarcodeProperties.imageType`/`antiAlias`/`orientation`.** Barcodes are black and
  white: PNG is lossless and smaller, and SVG scales. Rotate in the consumer if needed.
- **Writing to a file path.** A library writing to paths from properties is a path-traversal risk;
  callers decide where bytes go.
