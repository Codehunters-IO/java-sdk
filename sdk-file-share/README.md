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
| `download(referenceId, fileId, OutputStream)` / `download(FileRef, OutputStream)` | Streams content of any size; returns the bytes written |

Failures are `FileShareException` subtypes: `NotFound` (404), `Unauthorized` (401/403),
`Conflict` (409), `Validation` (other 4xx), `Unavailable` (5xx, timeouts, I/O). `status()` holds the
HTTP status when there was one, and the message carries at most 512 characters of the error body.
A response the client cannot read (missing fields, invalid UUID, timestamp or presigned URL) is a
plain `FileShareException`.

`UploadRequest` and `FileRef` reject, with `IllegalArgumentException`, a `referenceId` that is not a
single safe path segment (`[A-Za-z0-9._-]`, not `.` or `..`), a file name with a path separator or
control character, an unparseable content type and a negative size.

Ticket `toString()` hides the URL's query string (the signature). Do not log `uploadUrl()` or
`downloadUrl()` yourself.

## Configuration

| Property | Default | Meaning |
|---|---|---|
| `codehunters.file-share.enabled` | `true` | Turns the auto-configuration off |
| `codehunters.file-share.base-url` | — | Required; absolute `http`/`https` URL |
| `codehunters.file-share.connect-timeout` | `5s` | TCP connect timeout |
| `codehunters.file-share.read-timeout` | `30s` | Response timeout |
| `codehunters.file-share.max-download-size` | `50MB` | Limit of the in-memory `download` |
| `codehunters.file-share.presigned.allowed-hosts` | empty (any) | Hosts presigned URLs may point at, exact or `*.domain`; URLs must always be `https` |
| `codehunters.file-share.auth.client-registration-id` | — | Spring Security client registration (`client_credentials`) used for the bearer token |
| `codehunters.file-share.auth.principal` | `codehunters-file-share` | Name the token is cached under |

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
listeners and async tasks work. Startup fails if the registration is missing or not
`client_credentials`. Declaring any `FileShareRequestInterceptor` bean replaces this built-in
authentication (for example to relay a user token); several run in `@Order`.

### Beans

- `fileShareRestClient` is built from the application's `RestClient.Builder` (so Boot's
  customizers, observations and message converters apply) but is not an autowiring candidate:
  inject it with `@Qualifier("fileShareRestClient")` if needed.
- Define your own `FileShareClient` or `fileShareRestClient` to replace them.

## Known limits

- An `allowed-hosts` list is not enforced when empty; set it in production.
- `http` presigned URLs (for example a local MinIO without TLS) are rejected; front it with TLS or
  provide your own `FileShareClient`.
- Expiry timestamps without an offset are read as UTC.
- The SDK sets its own JDK request factory to apply the timeouts, so a request factory or SSL
  bundle configured on the application's `RestClient.Builder` does not reach file-share calls.
- Interceptors already registered on the application's `RestClient.Builder` also run for
  file-share calls.

Upgrading from 1.x: see [MIGRATION-2.0.md](MIGRATION-2.0.md).
