# Changelog — sdk-barcode

## 2.1.0

### Added
- `BarcodeRejectedException`, a `ClientInputException` from `sdk-commons` (still an
  `IllegalArgumentException`), replaces the bare `IllegalArgumentException` for what a client can
  get wrong: `BARCODE_IMAGE_REJECTED` for images `BarcodeReader` refuses,
  `BARCODE_CONTENT_REJECTED` for `BarcodeRequest`s that cannot be rendered. Messages are unchanged.
  `sdk-web` 3.0.0 answers it with 400 and the code. A missing (`null`) content counts as
  `BARCODE_CONTENT_REJECTED`, like an empty one; other missing arguments stay plain
  `IllegalArgumentException`s.
- `sdk-commons` is an `api` dependency, so it reaches the application's runtime classpath. Its
  TOTP auto-configuration stays off unless `codehunters.totp.enabled=true`.

## 2.0.0

Package reorganisation; behaviour and `codehunters.barcode.*` properties unchanged. See
[MIGRATION-2.0.md](MIGRATION-2.0.md).

- The public API stays in `io.codehunters.barcode`; `config` is unchanged.
- Rendering internals (content rules, symbol, PNG and SVG writers) moved to
  `io.codehunters.barcode.internal.render`, decoding internals (decode plan, luminance) to
  `io.codehunters.barcode.internal.read`. These packages are not API.
- The ZXing mapping moved to the internal `io.codehunters.barcode.internal.ZxingMapping`; `BarcodeFormat` and
  `ErrorCorrection` no longer reference ZXing types.

## 1.0.0

First release.

- `BarcodeRenderer`: QR Code, Data Matrix, PDF417, Code 128, Code 39, EAN-13, UPC-A and ITF as PNG
  or SVG (`ImageFormat`), from a `BarcodeRequest` (format, content, size, margin in modules per side,
  QR `ErrorCorrection`). Content is checked per format, with GS1 check digits for EAN-13 and UPC-A.
- `BarcodeRenderer` writes QR Code, Data Matrix and PDF417 content as UTF-8 with an ECI. PDF417 may
  use its whole range of 90 rows; content is capped at the longest each symbol holds (QR Code 7089,
  Data Matrix 3112, PDF417 2686 digits).
- `BarcodeReader`: decodes any of those formats from a PNG, JPEG, GIF or BMP image into
  `DecodedBarcode`, empty when none is found. Other image formats are rejected. The pixel count and
  the memory the decode would take are checked from the image header before decoding; large images
  are subsampled to about 4 megapixels. Malformed headers and decoder crashes never escape as
  unchecked errors. No character set is forced, so third-party symbols without an ECI keep ZXing's
  guess. Every even ITF length from 2 to 80 reads back.
- `BarcodeLimits`: largest rendered image, largest decoded image and most memory one decode may
  take (`maxDecodeBytes`, 128 MB by default); requests above them fail before any work is done.
- Error messages and `toString()` never contain the encoded or decoded content.
- Headless and diskless: in-memory `java.awt` images only, ImageIO's file cache bypassed.
- Spring Boot auto-configuration with `codehunters.barcode.*` properties (including
  `max-decode-bytes`), validated at startup.
- ZXing `core` 3.5.4 only; barcode4j is gone. The core package depends on the JDK and ZXing, which an
  ArchUnit test enforces.
- Replaces `commons-util-barcode-support` from `codehuntersio-java-commons-support`; see
  [MIGRATION-from-commons-support.md](MIGRATION-from-commons-support.md).
