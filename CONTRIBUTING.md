# Contributing

Discuss a change with the repository owners (issue or message) before starting on it.

## Branches and pull requests

- Branch from the target branch with a prefix: `feature/`, `fix/`, `refactor/`, `docs/` or `chore/`.
  CI builds every push to those branches and every pull request to `master`, `main` and `develop`.
  `master` is the base branch; merging to it drafts releases but publishes nothing
  (see [README](README.md#releasing)).
- One concern per pull request. A change that needs another one first is stacked on that branch and
  merged after it, in order.
- `master` only receives reviewed pull requests.

### Stacked pull requests

A stack is a chain of pull requests, each one based on the branch of the previous one. GitHub does
not retarget a pull request when its base is merged unless that base branch is deleted, so merging
them one after another in table order leaves everything above the first one outside `master`.

- Either merge top-down: the top pull request into its base, then the next one down, and so on, and
  the bottom one into `master` last. Only that last merge drafts the releases, once, with the final versions.
- Or, after each merge into `master`, delete the merged branch (GitHub then retargets the next pull
  request to `master`) or retarget it by hand before merging it.
- Use **merge commits** for stack pull requests, not squash. A squash creates a commit unrelated to
  the branch history, so every pull request above it conflicts with `master` and has to be
  reconciled.

## Commits

[Conventional Commits](https://www.conventionalcommits.org/) with the module as scope:
`feat(file-share): ...`, `fix(audit-log): ...`, `test(totp): ...`, `docs(plan): ...`,
`chore(build): ...`. Each commit is atomic and builds on its own, except the failing-test commit
described below.

## Test-driven changes

Every behaviour change starts with a test that fails for the right reason:

1. Commit the failing test (`test(<module>): ...`).
2. Commit the smallest change that makes it pass (`feat`/`fix`), then refactor while green.

Tests describe one behaviour each, are named `should_<expected>_when_<condition>`, and contain no
branching or loops; put repeated setup in fixtures. Adapters are tested against real infrastructure
with Testcontainers (`*IT`), so the build needs Docker. Mocks are for outbound ports only; prefer
fakes when behaviour matters.

## Definition of done

A change is done when, on its branch:

- `./gradlew build` passes: tests, Spotless (`spotlessApply` fixes formatting), and the JaCoCo gates
  of LINE ≥ 90% and BRANCH ≥ 80% for the module. The gates are fixed; add tests rather than lower
  them (`verifyCoverageGates` rejects `coverage.branch.minimum`).
- `./gradlew -p build-logic test` passes when `build-logic/` changed.
- The module's `README.md` describes the new behaviour and its `CHANGELOG.md` lists it under the
  next version. A breaking change also gets a step in the module's `MIGRATION-<major>.md` and a new
  major version.
- A new module is added to `settings.gradle.kts`, to `platform/sdk-bom` (`verifyBomConstraints`
  fails otherwise) and to the module table in the root `README.md` and `CHANGELOG.md`.
- Every library release bumps `sdk-bom` too, in the same pull request, followed by
  `./gradlew :sdk-bom:updateBomLock`. Otherwise `verifyBomLock` fails: consumers of the released
  BOM version would never see the new library version.
- No secrets, tokens or personal data in code, tests, logs or exception messages.
- Local assistant or editor configuration files stay out of the repository.

## Versions and releases

- Semantic versioning per module; the version lives in the module's `build.gradle.kts`.
- A version is published once. Bump it in the same pull request as the change; work in progress
  may use `-SNAPSHOT`, which is never published and must not be listed in the BOM.
- Releasing a merged version:
  1. After the merge, *Releases* shows a draft per new version (`sdk-<module>/vX.Y.Z`,
     `bom/vX.Y.Z`), with that version's `CHANGELOG.md` section as notes.
  2. Check the notes and that the target is the merge commit, then *Publish release*. Publishing
     creates the tag and uploads that module only.
  3. Publish the libraries first and the BOM last: a BOM release fails while a version it lists is
     not in GitHub Packages.
  4. A failed upload is retried with *Actions → Publish → Run workflow* and the tag.
- Never push release tags by hand, and never create a release on a commit that is not on `master`:
  the publish workflow rejects it.
- Commit messages are linted on pull requests (`commitlint.config.js`): Conventional Commits,
  header and body lines up to 120 characters.

## Configuration properties

- Properties live under `codehunters.<sdk>.*` and bind to a validated `@ConfigurationProperties`
  record; invalid values fail at startup.
- Renaming a key is a breaking change: ship it in a major version with the rename in the module's
  `MIGRATION-<major>.0.md`. The SDK keeps no aliases for older keys
  ([ADR 0021](docs/adr/0021-postgresql-only-and-codehunters-properties.md)).
- PostgreSQL is the only supported database; new persistence code and reference DDL target it.

## Architecture decisions

A decision that changes a module's public API, artifacts, dependencies, transaction or messaging
semantics, or the build gets an ADR in [`docs/adr`](docs/adr/README.md) (MADR format) in the same
pull request. An accepted ADR is not edited; write a new one that supersedes it.

## Code of conduct

Please follow it in all your interactions with the project.

### Our Pledge

We pledge to make our community welcoming, safe, and equitable for all.

We are committed to fostering an environment that respects and promotes the dignity, rights, and contributions of all individuals, regardless of characteristics including race, ethnicity, caste, color, age, physical characteristics, neurodiversity, disability, sex or gender, gender identity or expression, sexual orientation, language, philosophy or religion, national or social origin, socio-economic position, level of education, or other status. The same privileges of participation are extended to everyone who participates in good faith and in accordance with this Covenant.


### Encouraged Behaviors

While acknowledging differences in social norms, we all strive to meet our community's expectations for positive behavior. We also understand that our words and actions may be interpreted differently than we intend based on culture, background, or native language.

With these considerations in mind, we agree to behave mindfully toward each other and act in ways that center our shared values, including:

1. Respecting the **purpose of our community**, our activities, and our ways of gathering.
2. Engaging **kindly and honestly** with others.
3. Respecting **different viewpoints** and experiences.
4. **Taking responsibility** for our actions and contributions.
5. Gracefully giving and accepting **constructive feedback**.
6. Committing to **repairing harm** when it occurs.
7. Behaving in other ways that promote and sustain the **well-being of our community**.


### Restricted Behaviors

We agree to restrict the following behaviors in our community. Instances, threats, and promotion of these behaviors are violations of this Code of Conduct.

1. **Harassment.** Violating explicitly expressed boundaries or engaging in unnecessary personal attention after any clear request to stop.
2. **Character attacks.** Making insulting, demeaning, or pejorative comments directed at a community member or group of people.
3. **Stereotyping or discrimination.** Characterizing anyone's personality or behavior on the basis of immutable identities or traits.
4. **Sexualization.** Behaving in a way that would generally be considered inappropriately intimate in the context or purpose of the community.
5. **Violating confidentiality**. Sharing or acting on someone's personal or private information without their permission.
6. **Endangerment.** Causing, encouraging, or threatening violence or other harm toward any person or group.
7. Behaving in other ways that **threaten the well-being** of our community.

#### Other Restrictions

1. **Misleading identity.** Impersonating someone else for any reason, or pretending to be someone else to evade enforcement actions.
2. **Failing to credit sources.** Not properly crediting the sources of content you contribute.
3. **Promotional materials**. Sharing marketing or other commercial content in a way that is outside the norms of the community.
4. **Irresponsible communication.** Failing to responsibly present content which includes, links or describes any other restricted behaviors.


### Reporting an Issue

Tensions can occur between community members even when they are trying their best to collaborate. Not every conflict represents a code of conduct violation, and this Code of Conduct reinforces encouraged behaviors and norms that can help avoid conflicts and minimize harm.

When an incident does occur, it is important to report it promptly. To report a possible violation, please contact the repository owners.

Community Moderators take reports of violations seriously and will make every effort to respond in a timely manner. They will investigate all reports of code of conduct violations, reviewing messages, logs, and recordings, or interviewing witnesses and other participants. Community Moderators will keep investigation and enforcement actions as transparent as possible while prioritizing safety and confidentiality. In order to honor these values, enforcement actions are carried out in private with the involved parties, but communicating to the whole community may be part of a mutually agreed upon resolution.


### Addressing and Repairing Harm

If an investigation by the Community Moderators finds that this Code of Conduct has been violated, the following enforcement ladder may be used to determine how best to repair harm, based on the incident's impact on the individuals involved and the community as a whole. Depending on the severity of a violation, lower rungs on the ladder may be skipped.

1) Warning
   1) Event: A violation involving a single incident or series of incidents.
   2) Consequence: A private, written warning from the Community Moderators.
   3) Repair: Examples of repair include a private written apology, acknowledgement of responsibility, and seeking clarification on expectations.
2) Temporarily Limited Activities
   1) Event: A repeated incidence of a violation that previously resulted in a warning, or the first incidence of a more serious violation.
   2) Consequence: A private, written warning with a time-limited cooldown period designed to underscore the seriousness of the situation and give the community members involved time to process the incident. The cooldown period may be limited to particular communication channels or interactions with particular community members.
   3) Repair: Examples of repair may include making an apology, using the cooldown period to reflect on actions and impact, and being thoughtful about re-entering community spaces after the period is over.
3) Temporary Suspension
   1) Event: A pattern of repeated violation which the Community Moderators have tried to address with warnings, or a single serious violation.
   2) Consequence: A private written warning with conditions for return from suspension. In general, temporary suspensions give the person being suspended time to reflect upon their behavior and possible corrective actions.
   3) Repair: Examples of repair include respecting the spirit of the suspension, meeting the specified conditions for return, and being thoughtful about how to reintegrate with the community when the suspension is lifted.
4) Permanent Ban
   1) Event: A pattern of repeated code of conduct violations that other steps on the ladder have failed to resolve, or a violation so serious that the Community Moderators determine there is no way to keep the community safe with this person as a member.
   2) Consequence: Access to all community spaces, tools, and communication channels is removed. In general, permanent bans should be rarely used, should have strong reasoning behind them, and should only be resorted to if working through other remedies has failed to change the behavior.
   3) Repair: There is no possible repair in cases of this severity.

This enforcement ladder is intended as a guideline. It does not limit the ability of Community Managers to use their discretion and judgment, in keeping with the best interests of our community.


### Scope

This Code of Conduct applies within all community spaces, and also applies when an individual is officially representing the community in public or other spaces. Examples of representing our community include using an official email address, posting via an official social media account, or acting as an appointed representative at an online or offline event.


### Attribution

This Code of Conduct is adapted from the Contributor Covenant, version 3.0, permanently available at [https://www.contributor-covenant.org/version/3/0/](https://www.contributor-covenant.org/version/3/0/).

Contributor Covenant is stewarded by the Organization for Ethical Source and licensed under CC BY-SA 4.0. To view a copy of this license, visit [https://creativecommons.org/licenses/by-sa/4.0/](https://creativecommons.org/licenses/by-sa/4.0/)

For answers to common questions about Contributor Covenant, see the FAQ at [https://www.contributor-covenant.org/faq](https://www.contributor-covenant.org/faq). Translations are provided at [https://www.contributor-covenant.org/translations](https://www.contributor-covenant.org/translations). Additional enforcement and community guideline resources can be found at [https://www.contributor-covenant.org/resources](https://www.contributor-covenant.org/resources). The enforcement ladder was inspired by the work of [Mozilla's code of conduct team](https://github.com/mozilla/inclusion).
