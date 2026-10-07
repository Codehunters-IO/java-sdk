# Changelog — sdk-barcode

## 1.0.0

First release.

- `BarcodeRenderer`: QR Code, Data Matrix, PDF417, Code 128, Code 39, EAN-13, UPC-A and ITF as PNG
  or SVG (`ImageFormat`), from a `BarcodeRequest` (format, content, size, margin in modules per side,
  QR `ErrorCorrection`). Content is checked per format, with GS1 check digits for EAN-13 and UPC-A.
- `BarcodeReader`: decodes any of those formats from an image into `DecodedBarcode`, empty when none
  is found. The pixel count is checked from the image header before decoding.
- `BarcodeLimits`: largest rendered image and largest decoded image; requests above them fail before
  any work is done.
- Error messages and `toString()` never contain the encoded or decoded content.
- Headless and diskless: in-memory `java.awt` images only, ImageIO's file cache bypassed.
- Spring Boot auto-configuration with `codehunters.barcode.*` properties, validated at startup.
- ZXing `core` 3.5.4 only; barcode4j is gone. The core package depends on the JDK and ZXing, which an
  ArchUnit test enforces.
- Replaces `commons-util-barcode-support` from `codehuntersio-java-commons-support`; see
  [MIGRATION-from-commons-support.md](MIGRATION-from-commons-support.md).
