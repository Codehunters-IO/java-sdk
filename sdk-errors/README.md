# sdk-errors

The error contract the SDKs share with the services that use them: one exception,
`ClientInputException`, for input a client got wrong. It depends on the JDK only.

```kotlin
implementation("io.codehunters:sdk-errors:1.0.0")
```

Most applications do not add it themselves: `sdk-web`, `sdk-jpa` and `sdk-barcode` bring it, because
it is part of their API ([ADR 0028](../docs/adr/0028-error-contract-in-its-own-module.md)).

## `ClientInputException`

Says "the client sent something this code cannot accept". It is an `IllegalArgumentException`, so
existing `catch` blocks keep working, with a stable `code()` matching `[A-Z][A-Z0-9_]*` (anything
else, and a blank message, is rejected).

The message may be sent to the client as the problem detail, so it must state the rule that was
broken and never repeat the input or other secret, personal or internal data: "sort property is not
allowed; allowed: created, name", not "cannot sort by passwordHash". The same contract makes it safe
to log. Programming errors (a `null` argument, a misconfigured whitelist) stay plain
`IllegalArgumentException`s: they are the server's fault, not the client's.

```java
throw new ClientInputException("INVALID_DATE_RANGE", "the range may span at most 31 days");
```

`sdk-web` answers it with 400 and the code, also when it is the cause of another exception, and
localizes the detail with the bundle key `error.<CODE>`. The `code()` is API: once released, keep it
stable.

Codes the SDKs throw:

| Code | Thrown by |
|---|---|
| `INVALID_SORT` | `sdk-jpa` (`InvalidSortException`) |
| `INVALID_SEARCH` | `sdk-jpa` (`InvalidSearchException`) |
| `BARCODE_IMAGE_REJECTED`, `BARCODE_CONTENT_REJECTED` | `sdk-barcode` (`BarcodeRejectedException`) |

Until `sdk-commons` 3.0.0 the class was `io.codehunters.commons.error.ClientInputException`; see
[CHANGELOG.md](CHANGELOG.md).
