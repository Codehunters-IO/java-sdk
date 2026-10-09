# Architectural Decision Records

MADR format. An accepted ADR is not edited; a new ADR supersedes it.

| ADR | Title | Status |
|---|---|---|
| [0001](0001-split-event-messaging-by-transport.md) | Split sdk-event-messaging by transport | accepted |
| [0002](0002-move-policy-enforcer-to-its-own-artifact.md) | Move the Keycloak policy enforcer to sdk-user-security-authz | accepted |
| [0003](0003-retire-geo-validation-from-the-sdk.md) | Retire sdk-geo-validation from the SDK | superseded by [0015](0015-domain-agnostic-geofencing.md) |
| [0004](0004-single-codehunters-property-prefix.md) | Use the codehunters.&lt;sdk&gt; property prefix with deprecated aliases | accepted |
| [0005](0005-rename-sdk-utils-to-sdk-totp.md) | Rename sdk-utils to sdk-totp | superseded by [0016](0016-consolidate-web-and-commons-artifacts.md) |
| [0006](0006-sdk-bom-and-no-exported-boot-bom.md) | Publish an sdk-bom and stop exporting the Spring Boot BOM | accepted |
| [0007](0007-support-spring-boot-4-only.md) | Support Spring Boot 4 only | accepted |
| [0008](0008-keep-measured-annotation.md) | Keep @Measured and its own aspect | accepted |
| [0009](0009-file-share-outbound-client-credentials.md) | Authenticate sdk-file-share calls with client credentials | accepted |
| [0010](0010-sdk-commons-for-framework-free-helpers.md) | Put framework-free helpers in sdk-commons | accepted, amended by [0016](0016-consolidate-web-and-commons-artifacts.md) |
| [0011](0011-problem-details-with-stable-error-codes.md) | Answer errors with problem details and stable error codes | accepted |
| [0012](0012-trusted-proxy-client-ip.md) | Resolve the client IP only through trusted proxies | accepted |
| [0013](0013-jpa-building-blocks.md) | JPA building blocks: UUID v7 entities, Spring Data auditing and whitelisted queries | accepted |
| [0014](0014-jasper-reports-from-the-classpath-only.md) | Render JasperReports templates from the classpath only | accepted |
| [0015](0015-domain-agnostic-geofencing.md) | Keep sdk-geo-validation as domain-agnostic geofencing | accepted |
| [0016](0016-consolidate-web-and-commons-artifacts.md) | Merge the servlet web modules into sdk-web and TOTP into sdk-commons | accepted |
| [0017](0017-publish-on-release-tags.md) | Publish packages only from published GitHub releases | accepted |
| [0018](0018-charge-attempts-only-for-started-sends.md) | Charge reclaim attempts only for rows whose send had started | accepted |
