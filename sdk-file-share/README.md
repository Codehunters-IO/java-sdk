# sdk-file-share

Client for the file-share service: presigned upload and download tickets, file metadata and
server-side downloads, with outbound client-credentials authentication.

```kotlin
implementation(platform("io.codehunters:sdk-bom:<version>"))
implementation("io.codehunters:sdk-file-share")
// Only for codehunters.file-share.auth.client-registration-id:
implementation("org.springframework.boot:spring-boot-starter-oauth2-client")
```

## Usage

Inject `FileShareClient` (`io.codehunters.fileshare`). Value types such as `FileRef` and the
tickets are in `io.codehunters.fileshare.model`; the REST implementation is built by the
auto-configuration (`RestFileShareClients` in `io.codehunters.fileshare.http`; the adapter class
itself is not public). Upgrading from 2.x: see [MIGRATION-3.0.md](MIGRATION-3.0.md).

Methods:

| Method | Does |
|---|---|
| `requestUpload(UploadRequest)` | Registers the file and returns a `PresignedUploadTicket` (`PUT` the bytes to `uploadUrl` with its `requiredHeaders()`) |
| `upload(PresignedUploadTicket, InputStream, contentLength)` | Sends exactly `contentLength` bytes to the ticket's URL (see [Uploading](#uploading)) |
| `requestDownload(referenceId, fileId)` | Returns a `PresignedDownloadTicket`; `FileShareConflictException` if the file is not downloadable yet |
| `getMetadata(referenceId, fileId)` | Returns the `FileRef` |
| `download(referenceId, fileId)` | Loads the content into memory, up to `max-download-size` |
| `download(referenceId, fileId, OutputStream)` / `download(FileRef, OutputStream)` | Streams content of any size; returns the bytes written. A write failure on your stream is an `UncheckedIOException`, not a service error |

Failures are `FileShareException` subtypes: `NotFound` (404), `Unauthorized` (401/403),
`Conflict` (409), `Validation` (other 4xx), `Unavailable` (5xx, timeouts, I/O). `status()` holds the
HTTP status when there was one, and the message carries at most 512 characters of the error body,
with control and line-separator characters replaced by spaces. The content endpoint must answer
`2xx`: a redirect is an error, never followed.
A response the client cannot read (missing fields, invalid UUID, timestamp or presigned URL) is a
plain `FileShareException`.

`UploadRequest` and `FileRef` reject, with `IllegalArgumentException`, a `referenceId` that is not a
single safe path segment (`[A-Za-z0-9._-]`, not `.` or `..`), a file name with a path separator or
control character, an unparseable content type and a negative size.

Ticket `toString()` prints only scheme, host, port and path: the query string (the signature) and
any user info are hidden. Do not log `uploadUrl()` or
`downloadUrl()` yourself.

### Uploading

```java
var ticket = fileShare.requestUpload(
    new UploadRequest("REF-1", workflowId, "invoice.pdf", "application/pdf", size, Map.of()));
try (var content = Files.newInputStream(path)) {
  fileShare.upload(ticket, content, size);
}
```

`upload` performs the signed `PUT` itself: it sends the ticket's `requiredHeaders()` (the
`Content-Type` the URL was signed for, so it is not a parameter) and a fixed `Content-Length`,
streams exactly `contentLength` bytes without buffering them, and reads no further. It goes through
its own `fileShareUploadRestClient`: no service token, no `FileShareRequestInterceptor`, no
interceptor or customizer of the application's `RestClient.Builder`, and no redirects, so nothing
but the signed request reaches object storage. The ticket's URL is checked against
`presigned.allowed-hosts` again before sending.

- `contentLength` above `max-upload-size`, negative, or longer than the stream:
  `IllegalArgumentException` (the first two before anything is sent).
- A read failure on your stream: `UncheckedIOException`, not a service error.
- The storage answering `403` (expired ticket, signature mismatch) is a
  `FileShareUnauthorizedException`, `5xx` a `FileShareUnavailableException`; the storage's error
  body is never read into the message, since it can echo the signed request.
- `upload-idle-timeout` bounds the wait for the storage's answer; a stalled write is bounded by the
  OS, not by the SDK.

### Testing

`io.codehunters.fileshare.testing.InMemoryFileShareClient` is a `FileShareClient` that keeps files
in memory, for tests of code that uses the SDK. It is in the main jar because the build publishes no
test-fixtures artifact, but it is test-only: the auto-configuration never registers it. Declare it
as a bean in a test configuration (the SDK's `FileShareClient` steps aside), seed files with
`store(UploadRequest, byte[])` and read uploads back with `content(fileId)`. Tickets point at
`https://file-share.test`, a host nothing answers on.

## Configuration

| Property | Default | Meaning |
|---|---|---|
| `codehunters.file-share.enabled` | `true` | Turns the auto-configuration off |
| `codehunters.file-share.base-url` | — | Required; absolute `http`/`https` URL (`https` when authentication is on) |
| `codehunters.file-share.connect-timeout` | `5s` | TCP connect timeout, also for the token endpoint |
| `codehunters.file-share.read-timeout` | `30s` | Deadline for a whole metadata, upload-ticket or token call, from sending the request to the last byte of the response |
| `codehunters.file-share.download-idle-timeout` | `30s` | Longest silence between bytes of a content download; the download as a whole has no deadline |
| `codehunters.file-share.max-download-size` | `50MB` | Limit of the in-memory `download`; between `1B` and about 2 GB, checked at startup |
| `codehunters.file-share.max-upload-size` | `5GB` | Largest `contentLength` `upload` accepts; at least `1B`, checked at startup |
| `codehunters.file-share.upload-idle-timeout` | `30s` | How long `upload` waits for the storage's answer; the upload as a whole has no deadline |
| `codehunters.file-share.presigned.allowed-hosts` | — | **Required** unless `allow-any-host`: hosts presigned URLs may point at, exact or `*.domain`. URLs must always be `https` on port 443 |
| `codehunters.file-share.presigned.allow-any-host` | `false` | `true` accepts presigned URLs on any https host when `allowed-hosts` is empty, and logs a warning at startup |
| `codehunters.file-share.auth.client-registration-id` | — | Spring Security client registration (`client_credentials`) used for the bearer token; blank means none |
| `codehunters.file-share.auth.principal` | `codehunters-file-share` | Name the token is cached under |
| `codehunters.file-share.auth.allow-insecure-http` | `false` | Allows sending the token to an `http` base URL (for example inside a mesh that adds mTLS) |

### Authentication

```yaml
spring.security.oauth2.client:
  registration.file-share:
    provider: keycloak
    client-id: my-service
    client-secret: ${FILE_SHARE_CLIENT_SECRET}
    authorization-grant-type: client_credentials
  provider.keycloak.token-uri: https://sso.example.com/realms/main/protocol/openid-connect/token
codehunters.file-share.auth.client-registration-id: file-share
```

The token is sent by Spring Security's `OAuth2ClientHttpRequestInterceptor`, for the configured
registration and cached under `auth.principal`, never under the signed-in user. It uses:

- the SDK's own `AuthorizedClientServiceOAuth2AuthorizedClientManager`, whose token calls use
  `connect-timeout` and `read-timeout` and which works outside an HTTP request (schedulers,
  listeners, async tasks). Other `OAuth2AuthorizedClientManager` beans of the application, such as
  Spring Security's request-bound `DefaultOAuth2AuthorizedClientManager`, are ignored;
- an `OAuth2AuthorizedClientManager` bean named `fileShareAuthorizedClientManager`, when the
  application declares one, so its providers, token client and storage apply. It must support
  `client_credentials` and work outside an HTTP request if file-share is called from one.

If the manager returns no authorized client, the call fails with `FileShareUnauthorizedException`
without reaching file-share, and the first miss logs a `WARN` naming the registration id.

A 401 from file-share drops the cached token, so the next call fetches a new one. An unreachable or
failing token endpoint is a `FileShareUnavailableException`; a rejected client is a
`FileShareUnauthorizedException`.

Startup fails if the registration is missing or not `client_credentials`, if
`spring-security-oauth2-client` is missing, or if `base-url` is plain `http` without
`auth.allow-insecure-http=true`.

`FileShareRequestInterceptor` beans run on every call, in `@Order`, before the SDK sets the
`Authorization` header: they add to authentication and never see or replace the token. To send
something other than client credentials, leave `client-registration-id` unset and set the header
in your own interceptor.

### Beans

- `fileShareRestClient` (metadata and upload tickets) and `fileShareContentRestClient` (content
  downloads) are built from the application's `RestClient.Builder` (so Boot's customizers,
  observations and message converters apply) but are not autowiring candidates: inject them with
  `@Qualifier` if needed.
- `fileShareUploadRestClient` (presigned uploads) is built from a plain `RestClient.builder()`, on
  purpose: nothing from the application reaches object storage. Not an autowiring candidate.
- `fileShareRestClients` (`RestFileShareClients`) groups the three; `fileShareClient` is built from
  it.
- Define your own `FileShareClient`, `fileShareRestClient`, `fileShareContentRestClient`,
  `fileShareUploadRestClient` or `RestFileShareClients` to replace them.

## Known limits

- Presigned URLs on a port other than 443 are rejected, even with `allow-any-host`.
- `http` presigned URLs (for example a local MinIO without TLS) are rejected; front it with TLS or
  provide your own `FileShareClient`.
- Expiry timestamps without an offset are read as UTC.
- The SDK sets its own request factories to apply the timeouts (JDK `HttpClient` for API calls,
  `HttpURLConnection` for content), so a request factory or SSL bundle configured on the
  application's `RestClient.Builder` does not reach file-share calls.
- Interceptors already registered on the application's `RestClient.Builder` also run for
  file-share calls (not uploads), before the SDK's authentication.
- File name and content type checks reject ASCII control characters and path separators only.
  Unicode format characters (for example bidi overrides), line separators and look-alike slashes
  pass; the service must not trust the name for paths or headers.

Upgrading from 1.x: see [MIGRATION-2.0.md](MIGRATION-2.0.md), [MIGRATION-3.0.md](MIGRATION-3.0.md),
[MIGRATION-4.0.md](MIGRATION-4.0.md) and [MIGRATION-5.0.md](MIGRATION-5.0.md) (presigned hosts are
required).
