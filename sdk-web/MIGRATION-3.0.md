# Migrating to sdk-web 3.0

`io.codehunters:sdk-web` 3.0.0 moves the properties of its three parts under the artifact's own
prefix, `codehunters.web`. Packages, class names and behaviour are those of 2.0.0. The old prefixes
are not read any more: a service that keeps them starts with the defaults, silently.

## Properties

| 2.x | 3.0 |
|---|---|
| `codehunters.web-errors.*` | `codehunters.web.errors.*` |
| `codehunters.device.*` | `codehunters.web.device.*` |
| `codehunters.i18n.*` | `codehunters.web.i18n.*` |

Every key below the prefix keeps its name:

| 2.x | 3.0 |
|---|---|
| `codehunters.web-errors.enabled` | `codehunters.web.errors.enabled` |
| `codehunters.web-errors.type-base-uri` | `codehunters.web.errors.type-base-uri` |
| `codehunters.web-errors.include-trace-id` | `codehunters.web.errors.include-trace-id` |
| `codehunters.device.enabled` | `codehunters.web.device.enabled` |
| `codehunters.device.trusted-proxies` | `codehunters.web.device.trusted-proxies` |
| `codehunters.device.forwarding-header` | `codehunters.web.device.forwarding-header` |
| `codehunters.device.user-agent.max-length` | `codehunters.web.device.user-agent.max-length` |
| `codehunters.device.user-agent.cache-size` | `codehunters.web.device.user-agent.cache-size` |
| `codehunters.i18n.enabled` | `codehunters.web.i18n.enabled` |
| `codehunters.i18n.basename` | `codehunters.web.i18n.basename` |
| `codehunters.i18n.default-locale` | `codehunters.web.i18n.default-locale` |
| `codehunters.i18n.supported-locales` | `codehunters.web.i18n.supported-locales` |
| `codehunters.i18n.header-name` | `codehunters.web.i18n.header-name` |
| `codehunters.i18n.encoding` | `codehunters.web.i18n.encoding` |
| `codehunters.i18n.fallback-to-system-locale` | `codehunters.web.i18n.fallback-to-system-locale` |

In YAML the three blocks move under `web`:

```yaml
# before
codehunters:
  web-errors:
    type-base-uri: https://errors.example.com/
  device:
    trusted-proxies: 10.0.0.0/8
  i18n:
    default-locale: en

# after
codehunters:
  web:
    errors:
      type-base-uri: https://errors.example.com/
    device:
      trusted-proxies: 10.0.0.0/8
    i18n:
      default-locale: en
```

Environment variables follow the same rule: `CODEHUNTERS_DEVICE_TRUSTEDPROXIES` becomes
`CODEHUNTERS_WEB_DEVICE_TRUSTEDPROXIES`, `CODEHUNTERS_I18N_DEFAULTLOCALE` becomes
`CODEHUNTERS_WEB_I18N_DEFAULTLOCALE` and `CODEHUNTERS_WEBERRORS_TYPEBASEURI` becomes
`CODEHUNTERS_WEB_ERRORS_TYPEBASEURI`.

## Steps

1. **Dependency.** Bump `sdk-web` to 3.0.0, or the `sdk-bom` that manages it. `sdk-audit-log`
   reads the device part through `sdk-web`, so its client IP follows `codehunters.web.device.*` too.
2. **Configuration.** Rename the keys in every `application*.yml`/`.properties`, config server
   repository, Helm values and environment variable as in the tables above.
3. **Check.** Startup fails on invalid values but not on unknown keys. Look for the old prefixes
   (`grep -rnE 'codehunters\.(web-errors|device|i18n)|CODEHUNTERS_(WEBERRORS|DEVICE|I18N)_'`) before
   deploying.
