# sdk-jasper

Reports for services: fill a [JasperReports](https://community.jaspersoft.com/) 7 template that ships
with the application and export it as PDF, XLSX or CSV, streamed or as bytes under a size cap.
Templates are compiled once and cached, and a report can read images and subreports only from its
own classpath folder.

```kotlin
implementation(platform("io.codehunters:sdk-bom:<version>"))
implementation("io.codehunters:sdk-jasper")
```

## Rendering

```java
ReportRequest request = ReportRequest.of("sales/monthly.jasper", ReportFormat.PDF)
    .withParameters(Map.of("title", "Sales", "REPORT_LOCALE", locale))
    .withData(ReportData.beans(rows));                       // JavaBeans or records

response.setContentType(ReportFormat.PDF.mediaType());
renderer.render(request, response.getOutputStream());       // streamed; the stream stays open

byte[] pdf = renderer.toBytes(request);                     // capped at max-output-size
```

- **Templates** are names under the template base folder (`reports/` by default): relative, made of
  letters, digits, `.`, `_`, `-` and `/`, ending in `.jasper` or `.jrxml`. Absolute paths, `..`,
  URLs and `classpath:` prefixes are rejected when the request is built.
- **Rows.** `ReportData.beans(rows)` reads fields through JavaBean getters or record components; a
  dotted field such as `customer.name` walks nested values, and `_THIS` is the whole row.
  `ReportData.source(supplier)` opens a fresh `JRDataSource` for each render (a source is consumed
  as it is read, so the request never holds one). `ReportData.empty()` fills from parameters only.
- **Parameters** are copied and may hold `null`. `REPORT_LOCALE`, `REPORT_TIME_ZONE` and
  `REPORT_RESOURCE_BUNDLE` work as in JasperReports; the parameters the SDK manages or that would
  bypass its loading (`REPORT_VIRTUALIZER`, `REPORT_CLASS_LOADER`, `REPORT_CONNECTION`,
  `REPORT_DATA_SOURCE`, `REPORT_CONTEXT`, `REPORT_URL_HANDLER_FACTORY`, `REPORT_FILE_RESOLVER`,
  `REPORT_PARAMETERS_MAP`, `JASPER_REPORTS_CONTEXT`) are rejected.
- **Formats.** `PDF` (OpenPDF), `XLSX` (JasperReports' own OOXML writer, no POI) and `CSV` (UTF-8).
  `mediaType()` and `extension()` give the `Content-Type` and file extension.
- **Errors.** Everything that fails while loading, filling or exporting throws `ReportException`.
  Its message names the template at most, never a parameter value or a row; the cause is
  JasperReports' own exception, kept for the server log, and may quote data. `ReportRequest` and
  `ReportData` print parameter names and row counts only.

## Templates

- **Prefer `.jasper`.** A `.jrxml` is compiled on first use with the JDK's compiler (`javax.tools`),
  which a runtime image without the `jdk.compiler` module does not have. Compile templates in the
  build with `JasperCompileManager.compileReportToFile` and ship the `.jasper` files; a build-time
  task for this is not part of the SDK yet.
- **JRXML is the JasperReports 7 format**, `<element kind="textField">` and so on; 6.x files must be
  converted with Jaspersoft Studio 7. Expressions are Java: the Groovy and JavaScript compilers are
  not on the classpath.
- **The cache** keeps `cache-size` compiled templates, least recently used first. Requests that arrive
  together for a template not yet loaded share one compilation; a failure is not cached.
- **Templates are code.** Their expressions run as Java with the application's permissions. Load only
  templates shipped with the application; never one a user uploaded or named freely.

## What a report can read

JasperReports normally resolves image, subreport and style locations as classpath names, file paths
or URLs. Here they resolve only under the template base folder on the classpath: an absolute path, a
`file:` or `http:` URL, or a name with `..` resolves to nothing and the fill fails. A location built
from a parameter can therefore not read the disk or reach the network. JRXML may not declare a
`DOCTYPE`, and unknown entities are never loaded.

An expression can still build a `java.net.URL` or a `java.io.File` itself; that is template code,
covered by the rule above.

## Large reports

Filled pages live in memory until the export. `virtualizer.enabled=true` keeps `max-pages` pages in
memory and swaps the rest to a file in `virtualizer.directory`; the file is deleted when the render
ends, successful or not. `toBytes` buffers the whole document: it fails with "the report exceeds the
output limit" as soon as the document passes `max-output-size`. For anything large, `render` into the
response instead.

## Spring Boot

The auto-configuration registers a `ReportRenderer` that reads templates through the application's
class loader. With Micrometer and a single `MeterRegistry` it is wrapped in `MeteredReportRenderer`,
which times every render in `report.render`, tagged `format` (`pdf`, `xlsx`, `csv`) and `outcome`
(`SUCCESS`, `ERROR`). It backs off when the application defines its own `ReportRenderer`, without
JasperReports on the classpath, and with `codehunters.jasper.enabled=false`.

| Property | Default | |
|---|---|---|
| `codehunters.jasper.enabled` | `true` | |
| `codehunters.jasper.template-base` | `reports` | Classpath folder for templates and their resources |
| `codehunters.jasper.cache-size` | `64` | Compiled templates kept, 0 to 10000; 0 loads on every render |
| `codehunters.jasper.max-output-size` | `50MB` | Largest document `toBytes` returns, 1 byte to about 2 GB |
| `codehunters.jasper.virtualizer.enabled` | `false` | Swap filled pages to a file |
| `codehunters.jasper.virtualizer.max-pages` | `100` | Pages kept in memory, 1 to 100000 |
| `codehunters.jasper.virtualizer.directory` | `java.io.tmpdir` | Must exist and be writable when enabled |

Invalid values fail at startup. Without Spring, build a `JasperReportRenderer` from `ReportSettings`.

## Dependencies

- JasperReports 7.0.8 (`jasperreports`, `jasperreports-pdf`), Jakarta-era, no iText.
- OpenPDF 1.3.43 from Maven Central. `jasperreports-pdf` pins `1.3.43.jaspersoft.2`, which only
  Jaspersoft's own repository serves; the SDK excludes it so consumers resolve from Central alone.
- Jackson 2 raised to 2.21.7 (JasperReports reads JRXML with Jackson 2, and the version Spring Boot's
  BOM manages has open advisories). A consumer's own dependency management may lower it again.

## Known limits

- No HTML, DOCX or XLS (binary) export, no Groovy or JavaScript expressions.
- Reports filled from a JDBC `Connection` are not supported; pass rows or a `JRDataSource`.
- The cause of a `ReportException` can contain data from the report; log it, never return it.
