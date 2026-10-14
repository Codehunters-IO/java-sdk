# Rulesets

These files are definitions, not state: editing one changes nothing until it is sent to the API.
All four are repository-level rulesets, the only kind the organisation's plan allows; the org-level
definitions in `Codehunters-IO/ci-templates/.github/ruleset/` cannot be applied yet.

| File | Applies to | In force |
|------|-----------|----------|
| `ruleset-java-sdk-develop.json` | `develop` | ruleset `25034719` |
| `ruleset-java-sdk-main.json` | `main` | ruleset `25034723` |
| `ruleset-java-sdk-hardening.json` | `develop` + `main` | ruleset `25034725` |
| `ruleset-java-sdk-tags.json` | release tags `sdk-<module>/vX.Y.Z`, `bom/vX.Y.Z` | ruleset `25034727` |

GitHub applies the union of every ruleset matching a ref. The branch rulesets are split by what
varies: the merge method is a parameter of the `pull_request` rule, so `develop` (squash) and `main`
(merge commit) need one ruleset each; what both share lives once, in the hardening ruleset.

**`develop`** takes changes only through a pull request, **squash-merged**. **`main`** only takes
the release pull request from `develop`, as a **merge commit**, and is never merged back (see
[CONTRIBUTING](../../CONTRIBUTING.md)). Both need signed commits and these checks green on the latest
base:

- `validate / PR Quality Gates / Build / Build`
- `validate / PR Quality Gates / Test / Test`
- `validate / PR Quality Gates / Security Scan / TruffleHog Secret Scan`
- `validate / PR Quality Gates / Commit Lint / Lint Commit Messages`

Neither branch can be deleted or force-pushed. Repository admins may merge a pull request without
the checks (`bypass_mode: pull_request`), never push directly. The release pull request needs that
bypass: `main` holds release merge commits `develop` never gets, so `develop` is never "up to date"
with `main` and the strict policy cannot be met.

**Release tags** cannot be moved or deleted once created. Creating them stays open: publishing a
draft release creates its tag. Probed on 2026-10-13 with the bypass removed: creating
`sdk-probe/v0.0.999` passed, force-moving and deleting it were rejected (`GH013`).

Renaming a job in `ci.yml` or in the shared pipeline changes its check name: update the list here
and in the live ruleset in the same change, or every pull request waits on a check that never
reports. `delete_branch_on_merge` is on; it is a repository setting, not a rule.

Apply or update (updating is a `PUT` against the id, not a `PATCH`):

```bash
gh api -X POST repos/Codehunters-IO/java-sdk/rulesets --input .github/ruleset/<file>.json
gh api -X PUT  repos/Codehunters-IO/java-sdk/rulesets/<id> --input .github/ruleset/<file>.json
```

Check what is in force rather than trusting this table:

```bash
gh api repos/Codehunters-IO/java-sdk/rules/branches/develop --jq '[.[].type]|unique'
```
