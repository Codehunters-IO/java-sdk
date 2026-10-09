# Migrating sdk-barcode 1.x to 2.0

2.0 reorganises packages for readability. Behaviour, the `codehunters.barcode.*` properties and the
auto-configuration are unchanged; only package names move.

The public API stays in `io.codehunters.barcode`: `BarcodeRenderer`, `BarcodeReader`,
`BarcodeRequest`, `BarcodeFormat`, `ImageFormat`, `ErrorCorrection`, `BarcodeLimits` and
`DecodedBarcode`. `io.codehunters.barcode.config` keeps its classes. An application that imports
only those needs no change.

The implementation moved into sub-packages, rendering apart from reading. These classes were
package-private in 1.x and are not API in 2.0 either: they may change in any release.

| 1.x | 2.0 |
|---|---|
| `io.codehunters.barcode.ContentRules` | `io.codehunters.barcode.internal.render.ContentRules` |
| `io.codehunters.barcode.Symbol` | `io.codehunters.barcode.internal.render.Symbol` |
| `io.codehunters.barcode.PngWriter` | `io.codehunters.barcode.internal.render.PngWriter` |
| `io.codehunters.barcode.SvgWriter` | `io.codehunters.barcode.internal.render.SvgWriter` |
| `io.codehunters.barcode.DecodePlan` | `io.codehunters.barcode.internal.read.DecodePlan` |
| `io.codehunters.barcode.Luminance` | `io.codehunters.barcode.internal.read.Luminance` |

`BarcodeFormat` and `ErrorCorrection` no longer carry ZXing types: the mapping lives in the internal
`io.codehunters.barcode.internal` package, which is not API.

Only imports change; properties are unchanged. In the IDE, "organise imports" fixes the rest.

## Logging

Logger categories follow the classes, so a level set on an old package or class name no longer
applies. Rename it the same way as the imports, for example:

```properties
# before
logging.level.io.codehunters.barcode.Symbol=DEBUG
# after
logging.level.io.codehunters.barcode.internal.render.Symbol=DEBUG
```

A level on `io.codehunters` keeps working.
