# Changelog — sdk-file-share

## 2.0.0

### Breaking
- Properties move to `codehunters.file-share.*`; `vitxo.file-share.*` is deprecated and honoured
  until 3.0.0.
- Ticket `expiresAt` is an `Instant`.
- `FileRef` requires `id` and `workflowId`; `status` is never `null` (`FileStatus.UNKNOWN`).
- `UploadRequest` and `FileRef` validate their fields; `referenceId` arguments are validated.
- Presigned URLs must be `https` and, when configured, on an allowed host.
- `download(referenceId, fileId)` is capped by `max-download-size`.
- `fileShareRestClient` is not an autowiring candidate; `RestClientFileShareAdapter` takes a
  `PresignedUrlPolicy` and a maximum download size.

### Added
- Client-credentials authentication (`codehunters.file-share.auth.client-registration-id`) and the
  `FileShareRequestInterceptor` hook.
- Streaming `download(..., OutputStream)` overloads.
- `FileShareException.status()`.
- `codehunters.file-share.presigned.allowed-hosts`, `max-download-size`, `auth.principal`.

### Fixed
- `connect-timeout` was ignored.
- The SDK's `RestClient` ignored the application's `RestClient.Builder` and competed with the
  application's own `RestClient` for injection.
- Malformed responses (missing presigned URL, invalid URI, UUID or timestamp) escaped as raw
  runtime exceptions; unknown statuses became `null`.
- Error messages embedded the whole response body.
- Ticket `toString()` printed the presigned URL signature.
- Metadata maps were not copied.

### Removed
- Lombok.
