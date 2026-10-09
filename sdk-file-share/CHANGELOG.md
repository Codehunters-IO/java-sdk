# Changelog — sdk-file-share

## 4.0.0

### Breaking
- The 1.x property aliases are removed, with their `EnvironmentPostProcessor`
  (`FileShareLegacyProperties`) and their deprecated configuration metadata: only
  `codehunters.file-share.*` binds. See [MIGRATION-4.0.md](MIGRATION-4.0.md).

### Removed
- The dependency on `sdk-support`.

## 3.0.0

### Breaking
- Packages reorganised by purpose instead of by layer; only imports change, properties and beans
  are unchanged. API (`FileShareClient`, `UploadRequest`, `FileShareRequestInterceptor`, exceptions)
  is in `io.codehunters.fileshare`, value types in `io.codehunters.fileshare.model`, the REST
  client in `io.codehunters.fileshare.http`. See [MIGRATION-3.0.md](MIGRATION-3.0.md).

## 2.0.0

### Breaking
- Properties move to `codehunters.file-share.*`.
- Ticket `expiresAt` is an `Instant`.
- `FileRef` requires `id` and `workflowId`; `status` is never `null` (`FileStatus.UNKNOWN`).
- `UploadRequest` and `FileRef` validate their fields; `referenceId` arguments are validated.
- Presigned URLs must be `https` and, when configured, on an allowed host and port 443.
- With authentication on, the base URL must be `https` unless `auth.allow-insecure-http=true`.
- `download(referenceId, fileId)` is capped by `max-download-size`.
- `fileShareRestClient` is not an autowiring candidate; `RestClientFileShareAdapter` takes a
  `PresignedUrlPolicy` and a maximum download size.

### Added
- Client-credentials authentication (`codehunters.file-share.auth.client-registration-id`) and the
  `FileShareRequestInterceptor` hook, whose beans run before the token is set. A 401 or 403 evicts
  the cached token.
- `download-idle-timeout` and the `fileShareContentRestClient` bean: content downloads are bounded
  by silence between bytes, not by a total deadline, and never follow redirects.
- Streaming `download(..., OutputStream)` overloads.
- `FileShareException.status()`.
- `codehunters.file-share.presigned.allowed-hosts`, `max-download-size`, `auth.principal`,
  `auth.allow-insecure-http`.

### Fixed
- `connect-timeout` was ignored.
- The SDK's `RestClient` ignored the application's `RestClient.Builder` and competed with the
  application's own `RestClient` for injection.
- Malformed responses (missing presigned URL, invalid URI, UUID or timestamp) escaped as raw
  runtime exceptions; unknown statuses became `null`.
- Error messages embedded the whole response body, control characters included.
- Ticket `toString()` printed the presigned URL signature.
- Metadata maps were not copied.

### Removed
- Lombok.
