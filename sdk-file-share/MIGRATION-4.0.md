# Migrating sdk-file-share 3.x to 4.0

4.0 removes the 1.x property aliases, hides the REST adapter and adds presigned uploads. Packages,
beans and the `codehunters.file-share.*` properties of 3.0 are kept.

1. **Properties.** Only `codehunters.file-share.*` binds. A service still configured with the 1.x
   keys fails at startup (`codehunters.file-share.base-url must be configured when the SDK is
   enabled`), and timeouts set only under the old keys fall back to their defaults. Set the keys
   under the current prefix:

   | Property | Default |
   |---|---|
   | `codehunters.file-share.enabled` | `true` |
   | `codehunters.file-share.base-url` | — (required) |
   | `codehunters.file-share.connect-timeout` | `5s` |
   | `codehunters.file-share.read-timeout` | `30s` |

   As environment variables: `CODEHUNTERS_FILESHARE_ENABLED`, `CODEHUNTERS_FILESHARE_BASEURL`,
   `CODEHUNTERS_FILESHARE_CONNECTTIMEOUT`, `CODEHUNTERS_FILESHARE_READTIMEOUT`.
2. **Own `FileShareClient` implementations** add `upload(PresignedUploadTicket, InputStream,
   long)`. Test doubles can be replaced by `io.codehunters.fileshare.testing.InMemoryFileShareClient`.
3. **Code that built `RestClientFileShareAdapter`** (now package-private) builds the client with
   `new RestFileShareClients(api, content, upload).client(new PresignedUrlPolicy(hosts),
   new TransferLimits(maxDownloadSize, maxUploadSize))`, or injects the auto-configured
   `FileShareClient`. `MAX_DOWNLOAD_SIZE_LIMIT` is now on `TransferLimits`.
4. **Hand-rolled presigned uploads** can use `fileShare.upload(ticket, content, contentLength)`,
   which sends `ticket.requiredHeaders()` and never the service token.
5. **Client credentials.** Nothing to change in configuration. If the application declares an
   `OAuth2AuthorizedClientManager` bean (other than Spring Security's request-bound default), the SDK
   now uses it: it must support `client_credentials` and, for calls outside HTTP requests, be an
   `AuthorizedClientServiceOAuth2AuthorizedClientManager`. A 403 from file-share no longer evicts
   the cached token; a 401 does.
6. **Dependency.** Bump `sdk-file-share` to 4.0.0, or the `sdk-bom` that manages it. It no longer
   brings `io.codehunters:sdk-support`.
