# Migrating from commons-util-jasperreport-support

`io.codehunters.commons:commons-util-jasperreport-support` 5.1.0 (`codehuntersio-java-commons-support`)
is replaced by `io.codehunters:sdk-jasper` 1.0.0. The code was rewritten; nothing is source
compatible.

| commons-support | sdk-jasper |
|---|---|
| `JasperManagerUtil.fill(InputStream jrxml, params)` → PDF `byte[]` | `renderer.toBytes(ReportRequest.of("name.jasper", PDF).withParameters(params))` |
| `fill(jrxml, params, JRDataSource)` | `.withData(ReportData.source(() -> dataSource))` or `.withData(ReportData.beans(rows))` |
| `fill(jrxml, params, dataSource, SimplePdfExporterConfiguration)` | Not carried over; PDF export uses JasperReports' defaults and template properties |
| The caller opened the template stream from anywhere | Templates are classpath names under `codehunters.jasper.template-base` |
| `JRException` (checked) | `ReportException` (unchecked), without report data in its message |
| JasperReports 6.13 with iText 2.1.7 | JasperReports 7.0.8 with OpenPDF 1.3.43 |

## Behaviour changes

- **Compiled once.** `fill` compiled the JRXML on every call; templates are now cached, and `.jasper`
  files are read without compiling.
- **JasperReports 7 JRXML.** 6.x templates must be converted (Jaspersoft Studio 7 does it).
- **XLSX and CSV** are available besides PDF; `render` streams instead of building a `byte[]`, and
  `toBytes` is capped (`max-output-size`).
- **Resources stay on the classpath.** Images and subreports referenced by file path or URL no
  longer load; put them next to the templates.

## Intentionally not migrated

- **iText 2.1.7.** Unmaintained since 2009, with known vulnerabilities; OpenPDF is its maintained
  fork.
- **Templates from arbitrary streams.** A template is code; loading one from wherever a caller
  points is how a report becomes remote code execution.
