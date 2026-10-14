# Migrating sdk-file-share 4.x to 5.0

5.0.0 no longer accepts presigned URLs on any host by default.

## Name the storage hosts

`upload` sends the caller's file to the presigned URL in the service's answer. With an empty
`codehunters.file-share.presigned.allowed-hosts`, 4.x accepted any https host on any port and only
logged a warning at startup, so a compromised or spoofed response could send uploads to an internal
service.

| `presigned.allowed-hosts` | 4.x | 5.0 |
|---|---|---|
| set | those hosts, port 443 | those hosts, port 443 |
| empty | any https host, any port, startup `WARN` | startup fails |
| empty + `presigned.allow-any-host: true` | — | any https host, port 443, startup `WARN` |

List the object storage hosts:

```yaml
codehunters:
  file-share:
    presigned:
      allowed-hosts:
        - "*.s3.amazonaws.com"
```

Only where any host is acceptable, opt in with `presigned.allow-any-host: true`.

## Port 443 only

Presigned URLs on another port (`https://storage:9000/…`) are rejected even when any host is
allowed. Front such storage with TLS on 443, or provide your own `FileShareClient`.

## Constructing the properties by hand

`FileShareProperties.Presigned` has a new `allowAnyHost` component. Code that calls its constructor
directly adds the argument; binding from configuration needs no change.
