# Changelog — sdk-jasper

## 2.1.0

### Behaviour changes

- The compiled-template cache is Caffeine instead of a hand-written least-recently-used map. Its size
  bound (`cache-size`), one shared compilation per template for concurrent requests and failures never
  being cached are unchanged; a full cache now evicts by Caffeine's frequency-and-recency policy
  (W-TinyLFU) rather than strictly the least recently used template. `caffeine` is a new runtime
  dependency, at the version Spring Boot manages.
- A template loader that reads its own template through the cache fails with
  `IllegalStateException("re-entrant load of template …")` instead of deadlocking on its own
  unfinished load. Reading another template is still allowed.
- When the loader throws an `Error`, the callers waiting for that load now get the same raw `Error`
  as the loading caller, instead of a `ReportException` wrapping it.
- A loader that returns `null` fails the load with a `ReportException` naming the template; the
  failure is not cached.

- Parameters holding a `URL`, `URI`, `File`, `Path`, stream or reader below the first level of a
  collection, map or array, which 2.0.0 let through, are now rejected.
- A parameter whose collection, map or array contains itself is now rejected (2.0.0 accepted it, and
  the request's `equals` and `hashCode` then overflowed the stack).
- A value nesting collections, maps or arrays more than 8 levels deep is now rejected.
- Nested collections, maps and arrays in parameters are now copied into unmodifiable containers;
  code that changed them after building the request no longer affects it.

### Changes

- **Fix (security):** `ReportRequest` now looks for `URL`, `URI`, `File`, `Path`, stream and reader
  values at any depth: inside nested collections, map keys and values, and object arrays. Before, the
  check stopped one level down, so a `Map<String, List<URL>>` or a `List<Object[]>` holding a `File`
  reached JasperReports, which could load a template resource from disk or the network. Containers
  are copied as they are checked, so the request cannot be changed afterwards. Rejections name the
  parameter, never its value. The check is best effort: `Optional`, `Map.Entry`, other `Iterable`s
  and beans are not looked into.
- **Metrics:** the `report.render` timer gains a `template` tag. It holds the template name once that
  template has rendered successfully and `unknown` for failures of a template that never did, so
  names taken from requests cannot grow it; its cardinality is bounded by the templates the
  application ships. Dashboards or alerts that match the exact tag set of `report.render` may need
  updating.
- **Metrics:** the template cache publishes Micrometer's standard cache meters (`cache.gets`,
  `cache.puts`, `cache.evictions`, `cache.size`, ...) tagged `cache=codehunters.jasper.template.cache`
  when Micrometer and a single `MeterRegistry` are present and `cache-size` is above 0. New
  `TemplateCacheMetrics` binds them outside Spring. `JasperReportRenderer.templateCache()` exists for
  it and is not API: its return type lives in an internal package.
- The auto-configuration declares the renderer in a single bean method, wrapped with metrics when
  Micrometer and a single `MeterRegistry` are present; back-off on an application `ReportRenderer`
  is unchanged.
- README: a minimal JasperReports 7 JRXML template.

## 2.0.0

Package reorganisation; behaviour and `codehunters.jasper.*` properties unchanged. See
[MIGRATION-2.0.md](MIGRATION-2.0.md).

- The public API stays in `io.codehunters.jasper`; `config` and `metrics` are unchanged.
- The implementation moved by concern: template loading and the hardened context to
  `io.codehunters.jasper.internal.template`, exporting and the capped buffer to `io.codehunters.jasper.internal.export`,
  the bean data source and swap files to `io.codehunters.jasper.internal.fill`. These packages are not API.

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
