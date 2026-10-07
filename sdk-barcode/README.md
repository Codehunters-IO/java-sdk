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
| `QR_CODE` | 2D | Any text, UTF-8 with an ECI, up to the symbol capacity |
| `DATA_MATRIX` | 2D | Any text, UTF-8 with an ECI, up to the symbol capacity |
| `PDF_417` | Stacked 2D | Any text, UTF-8 with an ECI, up to the symbol capacity |
| `CODE_128` | Linear | Printable ASCII, 1 to 80 characters |
| `CODE_39` | Linear | `0-9`, `A-Z`, space and `- . $ / + %`, 1 to 80 characters |
| `EAN_13` | Linear | 12 digits, or 13 with a valid check digit |
| `UPC_A` | Linear | 11 digits, or 12 with a valid check digit |
| `ITF` | Linear | An even number of digits, 2 to 80 |

`BarcodeRequest` checks these rules when it is built and throws `IllegalArgumentException`. Whether
2D content fits the symbol is known only when encoding, so `render` throws then. Error messages and
`toString()` never contain the content, which is often a ticket, a token or personal data.

What each 2D symbol holds depends on the kind of content. The largest values ZXing 3.5.4 encodes:

| `BarcodeFormat` | Digits | Lower-case ASCII | `é` (2 UTF-8 bytes) | `€` (3 UTF-8 bytes) |
|---|---|---|---|---|
| `QR_CODE` at `ErrorCorrection.L` | 7089 | 2952 | 1476 | 984 |
| `QR_CODE` at `M` (default) | 5596 | 2330 | 1165 | 776 |
| `DATA_MATRIX` | 3112 | 2332 | 776 | 517 |
| `PDF_417` (up to 30 columns x 90 rows) | 2686 | 1833 | 549 | 366 |

`BarcodeRequest` rejects content longer than the digits-only figure; anything below it may still
fail to fit, which `render` reports.

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

- Reads **PNG, JPEG, GIF and BMP** only, and looks for any of the formats above, trying harder on
  rotated or noisy images. TIFF, WBMP and whatever an ImageIO plugin on the classpath adds
  (TwelveMonkeys, JAI) are rejected as unsupported: each decoder is attack surface.
- **Empty** when the image holds no readable barcode, including when ZXing fails on a crafted image.
- **Decompression bombs** are rejected from the header, before any pixel is decoded:
  - width x height against `maxDecodePixels`, so a small file announcing 100000 x 100000 pixels
    costs nothing;
  - the memory the decode would take against `maxDecodeBytes`. It counts the image ImageIO allocates
    at its real depth (a 16-bit RGBA PNG takes 8 bytes per pixel, an 8-bit grey one 1), a 1-byte grey
    copy for ZXing, and for JPEG the decoder's own buffer of up to 6 bytes per source pixel.
- **Large images are subsampled** to about 2048 x 2048 (4 megapixels) while they are decoded, which
  bounds memory and search time. A barcode too small to survive that, in a large photo, is not found:
  crop the image first.
- `IllegalArgumentException` for an image above a limit, an unsupported format, or a file whose
  header or pixels cannot be decoded. The stream is read, not closed.
- **Concurrency.** Each call holds its decode memory (up to `maxDecodeBytes`) and a CPU until it
  returns. Bound the calls in flight, with a semaphore or the size of the pool that handles uploads,
  to bound the total.
- **Character sets.** No character set is forced. Symbols this SDK renders carry a UTF-8 ECI; for
  third-party symbols without one, ZXing guesses (ISO-8859-1, UTF-8, Shift_JIS).
- A 12-digit EAN-13 decodes with its check digit (13 digits). An EAN-13 starting with `0` is also a
  UPC-A, and decodes as `UPC_A` with 12 digits: `0123456789012` comes back as `UPC_A`
  `123456789012`. Compare GTINs after left-padding to 13 digits.
- `ITF` decodes every even length from 2 to 80 digits, the same range the renderer accepts.
- `DecodedBarcode.toString()` hides the content.

## Limits

`BarcodeLimits(maxWidth, maxHeight, maxDecodePixels, maxDecodeBytes)`; the three-argument
constructor keeps the default `maxDecodeBytes`.

| Limit | Default | Range | Guards |
|---|---|---|---|
| `maxWidth`, `maxHeight` | 4096 | 1 to 16384 | The requested size, and the PNG actually drawn |
| `maxDecodePixels` | 16777216 (16 megapixels) | 1 to 33554432 | The size of an image read by `BarcodeReader` |
| `maxDecodeBytes` | 134217728 (128 MB) | 1 to 1073741824 | The memory one decode takes, estimated from the header |

With the defaults the worst accepted image is a 16-megapixel progressive JPEG: about 96 MB for the
decoder's buffer plus 17 MB for the subsampled image and its grey copy.

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
    max-decode-bytes: 128MB     # DataSize: 64MB, 1GB...
```

Values outside their range fail at startup. Spring is optional: the `io.codehunters.barcode`
package depends on the JDK and ZXing only, which an ArchUnit test enforces.

Coming from `commons-util-barcode-support`? See
[MIGRATION-from-commons-support.md](MIGRATION-from-commons-support.md).
