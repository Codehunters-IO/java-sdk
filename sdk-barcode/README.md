# sdk-barcode

Barcodes and QR codes for services: render them as PNG or SVG, and decode them from uploaded images,
with limits that keep a request or an upload from exhausting memory. Built on
[ZXing](https://github.com/zxing/zxing) `core` only (3.5.4); no `javase`, no other image library.

```kotlin
implementation(platform("io.codehunters:sdk-bom:<version>"))
implementation("io.codehunters:sdk-barcode")
```

## Formats

| `BarcodeFormat` | Kind | Content |
|---|---|---|
| `QR_CODE` | 2D | Any text, UTF-8, up to the symbol capacity (at most 7089 characters) |
| `DATA_MATRIX` | 2D | Any text, up to the symbol capacity (at most 3116 characters) |
| `PDF_417` | Stacked 2D | Any text, up to the symbol capacity (at most 2710 characters) |
| `CODE_128` | Linear | Printable ASCII, 1 to 80 characters |
| `CODE_39` | Linear | `0-9`, `A-Z`, space and `- . $ / + %`, 1 to 80 characters |
| `EAN_13` | Linear | 12 digits, or 13 with a valid check digit |
| `UPC_A` | Linear | 11 digits, or 12 with a valid check digit |
| `ITF` | Linear | An even number of digits, 2 to 80 |

`BarcodeRequest` checks these rules when it is built and throws `IllegalArgumentException`. Whether
2D content fits the symbol is known only when encoding, so `render` throws then. Error messages and
`toString()` never contain the content, which is often a ticket, a token or personal data.

## Rendering

```java
BarcodeRenderer renderer = new BarcodeRenderer();          // BarcodeLimits.DEFAULT

byte[] png = renderer.toPng(BarcodeRequest.of(BarcodeFormat.QR_CODE, ticketUrl, 300, 300));

BarcodeRequest ean = BarcodeRequest.of(BarcodeFormat.EAN_13, "400638133393", 400, 120)
    .withMargin(12);                                         // quiet zone, modules per side
renderer.render(ean, ImageFormat.SVG, response.getOutputStream());
```

- **Size.** `width` and `height` are the image size in pixels. A PNG uses whole pixels per module,
  centred; when the symbol needs more pixels than requested (a long QR code at 50 x 50), the image is
  as large as the symbol needs. Linear bars fill the requested height.
- **Margin.** The quiet zone in modules on each side, the same for every format: 4 by default for 2D
  symbols, 10 for linear ones, 0 to 256.
- **Error correction.** `ErrorCorrection.L`, `M` (default), `Q` or `H`, for QR codes only.
- **SVG.** Drawn in module units (`viewBox`) with one `rect` per run of dark modules and
  `shape-rendering="crispEdges"`. 2D symbols keep their proportions; linear bars stretch to the
  requested height. It stays small whatever the requested size.
- **Output.** `render` flushes the stream and leaves it open. A failing stream throws
  `UncheckedIOException`.
- **No display, no disk.** Only in-memory `java.awt` images are used, so it works with
  `java.awt.headless=true` (the module's tests run that way), and ImageIO's temporary-file cache is
  bypassed.

## Decoding

```java
BarcodeReader reader = new BarcodeReader();
Optional<DecodedBarcode> found = reader.read(upload.getInputStream());
found.map(DecodedBarcode::content);
```

- Reads whatever ImageIO reads (PNG, JPEG, GIF, BMP) and looks for any of the formats above, trying
  harder on rotated or noisy images.
- **Empty** when the image holds no readable barcode.
- **Decompression bombs** are rejected from the header: width x height is checked against
  `maxDecodePixels` before any pixel is decoded, so a small file announcing 100000 x 100000 pixels
  costs nothing.
- `IllegalArgumentException` for an image above the limit, a format ImageIO does not know, or a file
  that cannot be decoded. The stream is read, not closed.
- A 12-digit EAN-13 decodes with its check digit (13 digits).
- `ITF` decodes the lengths ZXing accepts by default: 6, 8, 10, 12 or 14 digits.
- `DecodedBarcode.toString()` hides the content.

## Limits

`BarcodeLimits(maxWidth, maxHeight, maxDecodePixels)`:

| Limit | Default | Range | Guards |
|---|---|---|---|
| `maxWidth`, `maxHeight` | 4096 | 1 to 16384 | The requested size, and the PNG actually drawn |
| `maxDecodePixels` | 16777216 (64 MB of pixels while decoding) | 1 to 33554432 | Images read by `BarcodeReader` |

The limits bound decoded pixels, not the compressed upload. Cap the request body as well, for
example with `spring.servlet.multipart.max-file-size`.

## Spring Boot

The auto-configuration registers a `BarcodeRenderer` and a `BarcodeReader`; each backs off when the
application defines its own.

```yaml
codehunters:
  barcode:
    enabled: true               # false registers nothing
    max-width: 4096
    max-height: 4096
    max-decode-pixels: 16777216
```

Values outside their range fail at startup. Spring is optional: the `io.codehunters.barcode`
package depends on the JDK and ZXing only, which an ArchUnit test enforces.

Coming from `commons-util-barcode-support`? See
[MIGRATION-from-commons-support.md](MIGRATION-from-commons-support.md).
