# Changelog — sdk-jasper

## 2.0.0

Package reorganisation; behaviour and `codehunters.jasper.*` properties unchanged. See
[MIGRATION-2.0.md](MIGRATION-2.0.md).

- The public API stays in `io.codehunters.jasper`; `config` and `metrics` are unchanged.
- The implementation moved by concern: template loading and the hardened context to
  `io.codehunters.jasper.template`, exporting and the capped buffer to `io.codehunters.jasper.export`,
  the bean data source and swap files to `io.codehunters.jasper.fill`. These packages are not API.
- `ReportSettings.resolve(String)` is public, as the template package reads it.

## 1.0.0

First release.

- `ReportRenderer` / `JasperReportRenderer`: fills a JasperReports 7 template and exports it as PDF
  (OpenPDF), XLSX or CSV (`ReportFormat`), streamed into an `OutputStream` the caller keeps, or as
  bytes from `toBytes`, which fails once the document passes the output limit.
- `ReportRequest`: template name, parameters (copied, `null` values allowed, SDK-managed built-ins
  rejected, as are `URL`, `URI`, `File`, `Path`, stream and reader values that JasperReports would
  load from directly), `ReportData` (beans, a data source supplier, or none) and format. `toString()` shows
  parameter names and row counts only.
- `ReportData.beans` reads JavaBean getters and record components, with dotted paths; a supplied
  data source that is `AutoCloseable` is closed after the fill.
- Templates load only from the classpath base folder (`reports/` by default); `.jasper` files are
  read as they are, `.jrxml` compiled once with ecj (`jasperreports-jdt`, works in Spring Boot fat
  jars) in a bounded cache shared by concurrent requests, with failures never cached.
- Images, subreports and styles named by a `String` resolve only under the same folder, beside the
  template that names them first; file paths and URLs resolve to nothing. DTDs in JRXML and JRTX are
  never processed.
- Every fill is bounded by a page limit and a timeout (`FillLimits`, `max-pages`, `timeout`).
- Optional swap-file virtualization for large reports, in a renderer-owned, owner-only folder with
  one subfolder per render; files are deleted when the render ends, and the folder when the
  renderer (`AutoCloseable`) closes.
- Tagged PDF and accessible PDF/A levels are refused before export with a clear `ReportException`;
  the OpenPDF release on Maven Central cannot write them.
- `ReportException` messages name the template at most, never report data; a `LinkageError` from an
  exporter is wrapped too.
- Spring Boot auto-configuration with `codehunters.jasper.*` properties, validated at startup, the
  renderer closed with the context, and a
  `report.render` timer (`format`, `outcome`) when a `MeterRegistry` exists.
- OpenPDF comes from Maven Central instead of the Jaspersoft-only build `jasperreports-pdf` pins;
  Jackson 2 is raised to 2.21.7.
- Replaces `commons-util-jasperreport-support` from `codehuntersio-java-commons-support`; see
  [MIGRATION-from-commons-support.md](MIGRATION-from-commons-support.md).
