# Changelog — sdk-jasper

## 1.0.0

First release.

- `ReportRenderer` / `JasperReportRenderer`: fills a JasperReports 7 template and exports it as PDF
  (OpenPDF), XLSX or CSV (`ReportFormat`), streamed into an `OutputStream` the caller keeps, or as
  bytes from `toBytes`, which fails once the document passes the output limit.
- `ReportRequest`: template name, parameters (copied, `null` values allowed, SDK-managed built-ins
  rejected), `ReportData` (beans, a data source supplier, or none) and format. `toString()` shows
  parameter names and row counts only.
- `ReportData.beans` reads JavaBean getters and record components, with dotted paths.
- Templates load only from the classpath base folder (`reports/` by default); `.jasper` files are
  read as they are, `.jrxml` compiled once in a bounded cache shared by concurrent requests, with
  failures never cached.
- Images, subreports and styles resolve only under the same folder; file paths and URLs resolve to
  nothing. JRXML may not declare a `DOCTYPE` or load unknown entities.
- Optional swap-file virtualization for large reports; the swap file is deleted when the render
  ends.
- `ReportException` messages name the template at most, never report data.
- Spring Boot auto-configuration with `codehunters.jasper.*` properties, validated at startup, and a
  `report.render` timer (`format`, `outcome`) when a `MeterRegistry` exists.
- OpenPDF comes from Maven Central instead of the Jaspersoft-only build `jasperreports-pdf` pins;
  Jackson 2 is raised to 2.21.7.
- Replaces `commons-util-jasperreport-support` from `codehuntersio-java-commons-support`; see
  [MIGRATION-from-commons-support.md](MIGRATION-from-commons-support.md).
