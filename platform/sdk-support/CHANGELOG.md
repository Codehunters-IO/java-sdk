# Changelog — sdk-support

Internal module; its consumers are the SDK modules, which need no change for these releases.

## 1.0.1

### Fixed
- `LegacyPropertyAliases` copies a list or map under an old key whole from the highest-precedence
  property source that defines any of its entries, as Boot binds it. It used to merge entries across
  sources, so a profile overriding `old.list[0..1]` over a default `old.list[0..2]` left the stale
  `[2]` in the new key. Environment variables (`OLD_LIST_0_`) take part like any other source.
- Nothing is copied for an old key when any entry of its new key is already set; before, the entries
  the new key did not define were still copied.
- A declared key without a rename fails when the aliases are constructed, not at application
  startup.

## 1.0.0

First release: `LegacyPropertyAliases`, the renamed-key aliases the SDK modules apply.
