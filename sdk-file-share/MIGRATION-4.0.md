# Migrating sdk-file-share 3.x to 4.0

4.0 removes the 1.x property aliases. Packages, classes, beans and the `codehunters.file-share.*`
properties are those of 3.0.

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
2. **Dependency.** Bump `sdk-file-share` to 4.0.0, or the `sdk-bom` that manages it. It no longer
   brings `io.codehunters:sdk-support`.
