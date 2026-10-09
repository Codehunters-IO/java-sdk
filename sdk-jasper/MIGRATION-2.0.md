# Migrating sdk-jasper 1.x to 2.0

2.0 reorganises packages for readability. Behaviour, the `codehunters.jasper.*` properties and the
auto-configuration are unchanged; only package names move.

The public API stays in `io.codehunters.jasper`: `ReportRenderer`, `JasperReportRenderer`,
`ReportRequest`, `ReportData`, `ReportFormat`, `ReportSettings`, `FillLimits`, `Virtualization` and
`ReportException`. `io.codehunters.jasper.config` and `io.codehunters.jasper.metrics` keep their
classes. An application that imports only those needs no change.

The implementation moved into sub-packages by concern. These classes were package-private in 1.x
and are not API in 2.0 either: they may change in any release.

| 1.x | 2.0 |
|---|---|
| `io.codehunters.jasper.TemplateLoader` | `io.codehunters.jasper.template.TemplateLoader` |
| `io.codehunters.jasper.TemplateCache` | `io.codehunters.jasper.template.TemplateCache` |
| `io.codehunters.jasper.TemplateNames` | `io.codehunters.jasper.template.TemplateNames` |
| `io.codehunters.jasper.ClasspathRepositoryService` | `io.codehunters.jasper.template.ClasspathRepositoryService` |
| `io.codehunters.jasper.HardenedContext` | `io.codehunters.jasper.template.HardenedContext` |
| `io.codehunters.jasper.Exports` | `io.codehunters.jasper.export.Exports` |
| `io.codehunters.jasper.CappedOutputStream` | `io.codehunters.jasper.export.CappedOutputStream` |
| `io.codehunters.jasper.BeanRows` | `io.codehunters.jasper.fill.BeanRows` |
| `io.codehunters.jasper.Swap` | `io.codehunters.jasper.fill.Swap` |
| `io.codehunters.jasper.SwapFolder` | `io.codehunters.jasper.fill.SwapFolder` |

Only imports change; properties are unchanged. If anything of yours referred to a moved class by
name (reflection, logging configuration for a logger category), use the new name. In the IDE,
"organise imports" fixes the rest.
