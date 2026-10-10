# 0027 — Build, release drafts and publish come from ci-templates

- Status: accepted
- Date: 2026-10-10
- Amends: [0017](0017-publish-on-release-tags.md) (only where the workflows live; the release flow is
  unchanged)

## Context

ADR 0017 kept the build, release-draft and publish workflows in this repository, because the shared
Java workflows of `Codehunters-IO/ci-templates` assume a service: `java-build` compiles with
`-x test` and looks for a bootable JAR, and `java-artifact-dependency-github` publishes the whole
project at one version. Every other repository of the organization inherits its CI from
ci-templates; this one carried about 240 lines of its own workflow logic and three scripts.

## Decision

ci-templates gains a stack for multi-module Gradle libraries, and this repository calls it:

| Workflow here | Calls | Repository-specific input |
|---|---|---|
| `ci.yml` | `java-library-build.yml` | `./gradlew build`, `-p build-logic test`, test images |
| `release-drafts.yml` | `java-library-release-drafts.yml` | tag pattern, `io.codehunters` group, test images |
| `publish.yml` | `java-library-publish.yml` | tag pattern, `verify-bom-release.sh`, test images |

The contract stays the one of ADR 0017: `./gradlew releaseCandidate` writes
`<tag> <gradle path> <project dir>` lines, drafts take their notes from the module's
`CHANGELOG.md`, and publishing checks the tag is on the default branch before running anything
from the tagged tree. What stays here is what only this repository knows: the image list and the
BOM check (`.github/scripts/verify-bom-release.sh`), which the shared workflow runs as its
`verify_script`.

The image pre-pull with Docker Hub mirrors moved into the shared workflows (`docker_images`).

## Consequences

- A fix to the build, draft or publish logic lands in ci-templates and reaches this repository
  through the `@v2` alias, like every other consumer.
- A change to the release flow needs a ci-templates release first; testing one before that means
  pointing the three `uses:` at a ci-templates branch, never merging that pin.
- The optional SonarCloud step of the old build is gone: the token it read is obsolete and the
  quality tool is still undecided. Adding one is an input of the shared build, not a step here.
- Required status checks are named after the calling job and the shared one (`build / Build`).
  No ruleset requires them today: the current GitHub plan offers none for private repositories.
