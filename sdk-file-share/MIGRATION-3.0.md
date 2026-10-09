# Migrating sdk-file-share 2.x to 3.0

3.0 only moves classes to new packages. Behaviour, beans and properties (`codehunters.file-share.*`)
are unchanged; only imports change. In most IDEs, *Optimize/Organize Imports* after replacing the
old packages below fixes every file.

| 2.x | 3.0 |
|---|---|
| `io.codehunters.fileshare.application.FileShareClient` | `io.codehunters.fileshare.FileShareClient` |
| `io.codehunters.fileshare.application.UploadRequest` | `io.codehunters.fileshare.UploadRequest` |
| `io.codehunters.fileshare.config.FileShareRequestInterceptor` | `io.codehunters.fileshare.FileShareRequestInterceptor` |
| `io.codehunters.fileshare.domain.exception.*` (`FileShareException` and subtypes) | `io.codehunters.fileshare.*` |
| `io.codehunters.fileshare.domain.model.*` (`FileRef`, `FileStatus`, `FileConstraints`, `PresignedUploadTicket`, `PresignedDownloadTicket`) | `io.codehunters.fileshare.model.*` |
| `io.codehunters.fileshare.infrastructure.RestClientFileShareAdapter` | `io.codehunters.fileshare.http.RestClientFileShareAdapter` |
| `io.codehunters.fileshare.infrastructure.PresignedUrlPolicy` | `io.codehunters.fileshare.http.PresignedUrlPolicy` |

`io.codehunters.fileshare.config` (`FileShareAutoConfiguration`, `FileShareProperties`,
`FileShareLegacyProperties`) keeps its name.

The 1.x keys `vitxo.file-share.*` are now honoured until 4.0.0 (was 3.0.0).
