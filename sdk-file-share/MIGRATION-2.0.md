# Migrating sdk-file-share 1.x to 2.0

1. **Properties.** Rename `vitxo.file-share.*` to `codehunters.file-share.*`. The old keys work until
   3.0.0 and log a warning at startup.
2. **Authentication.** 1.x sent no credentials. To send a client-credentials token, add
   `spring-boot-starter-oauth2-client`, declare a `client_credentials` registration and set
   `codehunters.file-share.auth.client-registration-id`. The base URL must then be `https`, or set
   `codehunters.file-share.auth.allow-insecure-http=true`. Services that added their own
   interceptor to `fileShareRestClient` should declare it as a `FileShareRequestInterceptor` bean;
   it runs before the SDK sets the token. A service that sends its own `Authorization` header
   leaves `client-registration-id` unset.
3. **Presigned URLs.** They must be `https`. Set `codehunters.file-share.presigned.allowed-hosts`
   (for example `*.s3.amazonaws.com`).
4. **Ticket expiry.** `PresignedUploadTicket.expiresAt()` and `PresignedDownloadTicket.expiresAt()`
   are `Instant` (were `LocalDateTime`); timestamps without an offset are read as UTC.
5. **FileRef.** `id` and `workflowId` are never `null`; `status` is `FileStatus.UNKNOWN` instead of
   `null` when the service sends none or an unknown value. Remove `null` checks on `status()`.
6. **Validation.** `UploadRequest`, `FileRef` and the `referenceId` arguments are validated (see the
   README); invalid values throw `IllegalArgumentException` before any call.
7. **Large files.** `download(referenceId, fileId)` fails above `max-download-size` (50 MB by
   default). Use `download(referenceId, fileId, OutputStream)` for large files. Content downloads
   are bounded by `download-idle-timeout` (silence between bytes), not by `read-timeout`, and a
   redirect from the content endpoint is an error.
8. **Errors.** Malformed responses now throw `FileShareException` instead of raw runtime exceptions;
   error messages carry at most 512 characters of the body; `status()` exposes the HTTP status.
9. **Injection.** `fileShareRestClient` is no longer an autowiring candidate; inject it with
   `@Qualifier("fileShareRestClient")`. Code that built `RestClientFileShareAdapter` directly passes
   a `PresignedUrlPolicy` and a maximum download size, and may call `withContentClient(...)` to
   download through a client with idle-based timeouts.
