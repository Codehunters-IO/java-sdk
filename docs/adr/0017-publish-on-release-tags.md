# 0017 — Publish packages only from published GitHub releases

- Status: accepted; the workflows moved to ci-templates in [0027](0027-release-workflows-from-ci-templates.md)
- Date: 2026-10-08
- Numbering: 0015 and 0016 belong to pull requests still open when this was written (geo-validation
  1.0 and the sdk-web / sdk-commons consolidation); this ADR takes 0017 so the three can merge in
  any order.

## Context

Until now every push to `master` ran `./gradlew build publish` for all modules, uploaded every
version not yet in GitHub Packages, and then tagged each uploaded module
(`sdk-<module>/vX.Y.Z`, `bom/vX.Y.Z`). That made merging and releasing the same act:

- A merge could not land without releasing whatever version it carried. Merging a stack of pull
  requests released each intermediate version.
- Nobody looked at what was about to be released before it became immutable: GitHub Packages
  rejects re-publishing a version.
- The BOM could be uploaded in the same run as, or before, the libraries it lists.

## Decision

Packages are published only when a GitHub release is published, one module per release.

- **Drafts.** `release-drafts.yml` runs on every push to `master`. It builds the commit and, for
  every module whose version has no tag yet, drafts a release named after the tag, with that
  version's `CHANGELOG.md` section as notes. An existing draft is moved to the newer commit.
  `./gradlew releaseCandidate` (from the `codehunters.publishing` convention) writes the
  `<tag> <gradle path> <project dir>` lines the workflows read, so no workflow parses Gradle output.
- **Publish.** A person publishes the draft. That creates the tag and starts `publish.yml`, which:
  - accepts only `sdk-<module>/vX.Y.Z` or `bom/vX.Y.Z`;
  - checks, before running anything from the tagged tree, that the tag is on the default branch;
  - maps the tag to the module that declares that version at that commit, and fails otherwise;
  - refuses a BOM release while any version in `bom-lock.properties` is missing from GitHub
    Packages;
  - builds and publishes that module only. Versions already uploaded are still skipped, so a
    manual re-run with the tag (`workflow_dispatch`) is safe.
- **Tags** are created only by publishing a release. The old tag job and `build/released-tags/`
  are removed.

Alongside, the pull-request build adopts the shared `ci-templates@v2` workflows that fit a library
monorepo (TruffleHog, dependency review, commit lint) and a weekly OWASP dependency check
(`java-owasp`). The shared Java build, test and artifact workflows are not used: they assume one
version per repository and gate coverage globally, while this repository versions and gates each
module.

## Consequences

- Merging is safe: nothing is released until someone publishes a draft.
- Releasing takes a click per module. A change spanning several modules produces several drafts,
  plus one for the BOM, which goes last.
- A draft created by the workflow's token can be published by a person, and that event does trigger
  `publish.yml` (GitHub only suppresses events raised by the workflow token itself).
- Required status checks named after the old jobs (`publish`, `tag`) no longer exist; the CI
  `build` check is unchanged.
- The release-notes links are copied from the CHANGELOG as written: relative links in them point
  nowhere on the release page.

## Alternatives considered

- **Push tags by hand.** Same trigger, no review step and no notes; rejected for the draft flow the
  maintainers chose.
- **One repository-wide release tag publishing every changed module.** Fits a single-version
  repository, not per-module semantic versions and tags.
- **Keep publishing on merge.** Rejected for the reasons in the context.
