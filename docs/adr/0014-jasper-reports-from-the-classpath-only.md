---
status: accepted
date: 2026-10-07
decision-makers: [andres]
---

# 0014. Render JasperReports templates from the classpath only

## Context and Problem Statement

`commons-util-jasperreport-support` compiled whatever `InputStream` a caller handed it, on every
call, and exported PDF through iText 2.1.7. JasperReports templates are code: their expressions are
compiled to Java and run with the application's permissions, and by default JasperReports resolves
images and subreports as classpath names, file paths or URLs. `jasperreports-pdf` 7 also pins an
OpenPDF build that only Jaspersoft's repository serves. How should `sdk-jasper` load templates and
their resources, and what should it depend on?

## Decision Drivers

- A request must not be able to make a report run code or read files it was not shipped with.
- Rendering stays fast under load: no compilation per request.
- Consumers resolve every dependency from Maven Central.

## Considered Options

- Option A — Templates and resources only from one classpath folder, compiled once into a bounded
  cache; JasperReports' repository replaced by a classpath-only one; OpenPDF taken from Central.
- Option B — Keep JasperReports' default repository and accept any template stream, as before.
- Option C — Option A, but keep Jaspersoft's OpenPDF build and ask consumers to add their repository.

## Decision Outcome

Chosen option: **Option A**. Template names are validated as relative classpath names (no absolute
paths, `..` or URLs) under `codehunters.jasper.template-base`. The JasperReports context the SDK uses
inherits every default extension except the repository, which is replaced by one that reads only
from that folder, with the file repository off, `DOCTYPE` declarations refused and unknown entities
never loaded. Compiled templates are cached, with concurrent first requests sharing one compilation.
`jasperreports-pdf`'s OpenPDF is excluded in favour of the same release on Maven Central.

### Consequences

- Good: a parameter that ends up as an image or subreport location cannot read the disk or reach the
  network; templates cannot come from users through the SDK.
- Good: a template is compiled once per process, not per request.
- Good: no extra Maven repository for consumers.
- Bad: images and subreports must be packaged next to the templates; reports that loaded them from
  disk or HTTP must change.
- Bad: OpenPDF 1.3.43 from Central may lack fixes Jaspersoft added to its build; the PDF tests cover
  text, images and multi-page output.
- Neutral: an expression can still open a `URL` or `File` itself. Templates are trusted code, which
  the README states; the SDK does not sandbox expressions.
