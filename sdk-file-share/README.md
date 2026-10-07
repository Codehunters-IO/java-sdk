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

Inject `FileShareClient`:

| Method | Does |
|---|---|
| `requestUpload(UploadRequest)` | Registers the file and returns a `PresignedUploadTicket` (`PUT` the bytes to `uploadUrl`) |
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

## Configuration

| Property | Default | Meaning |
|---|---|---|
| `codehunters.file-share.enabled` | `true` | Turns the auto-configuration off |
| `codehunters.file-share.base-url` | — | Required; absolute `http`/`https` URL (`https` when authentication is on) |
| `codehunters.file-share.connect-timeout` | `5s` | TCP connect timeout, also for the token endpoint |
| `codehunters.file-share.read-timeout` | `30s` | Deadline for a whole metadata, upload-ticket or token call, from sending the request to the last byte of the response |
| `codehunters.file-share.download-idle-timeout` | `30s` | Longest silence between bytes of a content download; the download as a whole has no deadline |
| `codehunters.file-share.max-download-size` | `50MB` | Limit of the in-memory `download`; between `1B` and about 2 GB, checked at startup |
| `codehunters.file-share.presigned.allowed-hosts` | empty (any) | Hosts presigned URLs may point at, exact or `*.domain`, on port 443; URLs must always be `https`. Empty logs a warning at startup |
| `codehunters.file-share.auth.client-registration-id` | — | Spring Security client registration (`client_credentials`) used for the bearer token; blank means none |
| `codehunters.file-share.auth.principal` | `codehunters-file-share` | Name the token is cached under |
| `codehunters.file-share.auth.allow-insecure-http` | `false` | Allows sending the token to an `http` base URL (for example inside a mesh that adds mTLS) |

The 1.x keys `vitxo.file-share.*` still work until 3.0.0 and log a deprecation warning.

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

The token is obtained and cached by Spring Security outside any HTTP request, so schedulers,
listeners and async tasks work. A 401 or 403 from file-share drops the cached token, so the next
call fetches a new one. An unreachable or failing token endpoint is a
`FileShareUnavailableException`; a rejected client is a `FileShareUnauthorizedException`.

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
- Define your own `FileShareClient`, `fileShareRestClient` or `fileShareContentRestClient` to
  replace them.

## Known limits

- An `allowed-hosts` list is not enforced when empty; set it in production.
- `http` presigned URLs (for example a local MinIO without TLS) are rejected; front it with TLS or
  provide your own `FileShareClient`.
- Expiry timestamps without an offset are read as UTC.
- The SDK sets its own request factories to apply the timeouts (JDK `HttpClient` for API calls,
  `HttpURLConnection` for content), so a request factory or SSL bundle configured on the
  application's `RestClient.Builder` does not reach file-share calls.
- Interceptors already registered on the application's `RestClient.Builder` also run for
  file-share calls, before the SDK's authentication.
- File name and content type checks reject ASCII control characters and path separators only.
  Unicode format characters (for example bidi overrides), line separators and look-alike slashes
  pass; the service must not trust the name for paths or headers.
- The `vitxo.file-share.*` aliases are applied once, after the configuration files load. Keys
  added later through `@PropertySource` are not aliased; use the new names there.

Upgrading from 1.x: see [MIGRATION-2.0.md](MIGRATION-2.0.md).
