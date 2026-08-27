# Release the project

The release is driven by an interactive script at the root of the repository:

```
$ ./release.sh
```

Run `./release.sh --help` for the full list of options.

## Script options

`--local`
: Full local rehearsal in an isolated git worktree: release branch, Maven build with
  the `release` profile (javadoc, sources, GPG signing), tag, and release notes
  generation. The main clone stays on the branch you started from. Nothing is published
  remotely (no Maven Central, Docker Hub, `git push`, GitHub release, or production email).

`--skip-tests`
: Adds `-DskipTests` to Maven build commands (also prefilled in the Extra Maven options
  prompt).

`--dry-run`
: Simulates the workflow without running git or Maven commands.

`--rollback`
: Undoes a local or failed release using the `release/.release` state file (under the
  gitignored `release/` directory). Removes the isolated worktree under
  `release/worktrees/<version>/`, deletes the local release branch and tag, leaves the
  main checkout on its original branch, restores leftover filtered files (notably
  `distribution/test-scripts/`, which lives outside `target/`), re-filters them against
  the restored SNAPSHOT POMs, and removes `release/.release`.

Typical local rehearsal:

```
$ cp .env.example .env
$ ./release.sh --local --skip-tests
```

If the release fails, or you want to discard the local rehearsal:

```
$ ./release.sh --rollback
```

The `release/.release` file is written as soon as the release worktree is created, so
`--rollback` works even when the build fails midway.

Do not open `release/worktrees/<version>/` as a second IDE workspace while the release
is running: Maven and git mutations are already bound to that path.

## What the script does (production release)

* Create an isolated git worktree at `release/worktrees/<version>/` on branch
  `release-<version>` (the main clone stays on your integration branch)
* Replace the SNAPSHOT version by the final version number
* Commit the change
* Build the final artifacts using the `release` profile (javadoc, sources, GPG signing)
* Copy `distribution/target/fscrawler-distribution-<version>.zip` to
  `release/<version>/fscrawler-<version>.zip` so later `mvn clean` does not delete it
* Tag the version
* Prepare release notes from `docs/source/release/{version}.md` and GitHub API
* Deploy **only** `fscrawler-distribution` to [Maven Central](https://central.sonatype.com/)
  using the `central-publishing-maven-plugin`. Other modules set `skipPublishing=true`.
  `flatten-maven-plugin` (`flattenMode=oss`) writes a self-contained POM (no parent,
  OSS metadata inlined, dependency versions resolved) so Central can validate the ZIP
  artifact without publishing `fscrawler-parent`.
* Prepare the next SNAPSHOT version, update the README "Latest versions" table
  (HTML comment markers `<!-- release-versions:start -->` / `<!-- release-versions:end -->`),
  and point `.github/dependabot.yml` at the GitHub milestone titled like that SNAPSHOT
  (`3.1-SNAPSHOT` → milestone **3.1**, written as its numeric id)
* Commit the change
* Merge the release branch into the branch you started from (still on the main clone)
* Remove the isolated worktree and delete the release branch
* Push the changes and the tag to origin
* Create a GitHub release with `gh release create`, attaching `fscrawler-<version>.zip`
* Optionally announce the version on https://discuss.elastic.co/c/annoucements/community-ecosystem

Every `mvn` and mutating `git` command in the worktree aborts if `HEAD` is not
`release-<version>`, so a checkout of another tag in the main clone cannot hijack the
release.

You will be guided through all the steps.

## README versions table

The "Latest versions" table in `README.md` is wrapped in HTML comments so it stays
readable Markdown (no visible `{VERSION}` placeholders):

```
<!-- release-versions:start -->
| Elasticsearch | FSCrawler | Released | Docs |
...
<!-- release-versions:end -->
```

On the "prepare for next development iteration" commit, `scripts/update_readme_versions.py`
turns the SNAPSHOT row into the released version (release date, ReadTheDocs
`fscrawler-{version}` URL) and appends a new SNAPSHOT row. Historical rows are left
untouched. The Elasticsearch range is kept from the previous SNAPSHOT row unless you
pass `--es-versions`.

Maven Central, SNAPSHOT, and Docker badges are already dynamic and are not rewritten.

## Dependabot milestone

`.github/dependabot.yml` assigns every Dependabot PR to a GitHub milestone. The YAML
field is the **numeric** milestone id (the suffix in
`https://github.com/dadoonet/fscrawler/milestone/28`), not the title.

On the "prepare for next development iteration" commit,
`scripts/update_dependabot_milestone.py` looks up the open milestone whose title matches
the next SNAPSHOT without `-SNAPSHOT` (`3.1-SNAPSHOT` → `3.1` → `28`) and rewrites every
`milestone:` key. In a production release the script creates that milestone if it does
not exist yet. `--local` only updates the file when the milestone is already there.

## Release notes

Release notes live in Markdown under `docs/source/release/` (for example `3.0.md`).
They are included in the ReadTheDocs documentation via MyST Parser and reused by the release
script to build `release/{version}/release-notes.md` (gitignored work directory at the repo
root, outside `target/` so `mvn clean` does not remove them).

The final notes combine:

* A usage header (`scripts/templates/release-header.md`)
* The version-specific Markdown file (MyST `{ref}` resolved to ReadTheDocs links;
  heading levels demoted by one so they nest under the header)
* GitHub-generated changelist (`## What's Changed`) from `gh api .../releases/generate-notes`

The early preview during `release.sh` may show an incomplete GitHub section, because
`generate-notes` runs before the tag is pushed and then resolves against the remote default
branch. After a successful `git push` of the release tag, `release.sh` regenerates
`release/{version}/release-notes.md` so the GitHub release and announcement email use the
correct commit range.

`release.sh` always runs the full workflow. Working files for a given version are written under
`release/{version}/` (for example `release/3.0/release-notes.md` and `release/3.0/release.log`)
so you can open and edit them in the IDE before sending the announcement.

To regenerate notes or resend the announcement without starting another release, call the
helper scripts directly.

Regenerate the assembled notes (requires `gh` authenticated and `GITHUB_REPO` in `.env`):

```
$ python3 scripts/prepare-release-notes.py \
    --version 3.0 \
    --since-tag fscrawler-2.9
```

Send (or resend) the announcement email from an existing notes file:

```
$ python3 scripts/send-announcement.py \
    release/3.0/release-notes.md \
    --subject "FSCrawler 3.0 released"
```

To update notes after a GitHub release was already published, edit the Markdown file, regenerate
with `prepare-release-notes.py`, then run:

```
$ gh release edit fscrawler-{version} --notes-file release/{version}/release-notes.md
```

The announcement header (`scripts/templates/release-header.md`) points `wget` at the GitHub
release asset `fscrawler-{version}.zip`. The ZIP's inner directory remains
`fscrawler-distribution-{version}` (Maven `artifactId`). Maven Central still publishes
`fscrawler-distribution-{version}.zip`.

## Before releasing

Verify that the project builds correctly with the release profile:

```
$ mvn clean install -Prelease -DskipTests
```

Prerequisites:

* Copy `.env.example` to `.env` and configure SMTP credentials and `GITHUB_REPO`
* `python3` and the GitHub CLI (`gh auth login`)
* A clean-ish git working tree on your integration branch
* GPG signing configured for the Maven `release` profile
* `~/.m2/settings.xml` with a `central` server entry (Sonatype Central token) for production deploy
* Docker Hub credentials when pushing images (or pass `-Ddocker.skip`)

## Environment variables (`.env`)

See `.env.example` at the repository root:

| Variable      | Purpose                                                             |
|---------------|---------------------------------------------------------------------|
| GITHUB_REPO   | GitHub repository (`dadoonet/fscrawler`)                            |
| SMTP_HOST     | SMTP server hostname                                                |
| SMTP_PORT     | SMTP port (465 for SSL)                                             |
| SMTP_USER     | SMTP username                                                       |
| SMTP_PASS     | Mailbox password (quote special chars: `SMTP_PASS='...'`)           |
| SMTP_SECURITY | Optional: `ssl` (port 465) or `starttls` (port 587)                 |
| ANNOUNCE_FROM | Sender address (must match `SMTP_USER` for Ionos)                   |
| ANNOUNCE_TO   | Recipient (personal address for local, Elastic list for production) |

After deployment, check the publishing status on
[Central Portal](https://central.sonatype.com/publishing/deployments).
The `central-publishing-maven-plugin` is configured with `autoPublish` enabled, so artifacts
are published automatically once validation succeeds.

Maven Central **release** coordinates are immutable: answering “no” during the post-deploy
checks only skips the git merge/push; it does not remove or replace what was published.
If a release build on Central is wrong, publish a **new** version (for example a patch).
Docker Hub tags can usually be overwritten by deploying the same tag again.

## Release Drafter

The repository uses [Release Drafter](https://github.com/release-drafter/release-drafter) to
maintain a **draft** GitHub release on each push to `main`. Tags follow the `fscrawler-{version}`
convention. The final published release uses the hybrid notes assembled by `release.sh`.

Logs are written to `release/<release-version>/release.log`. On failure, the script prints the
last lines of the log and suggests `./release.sh --rollback`.

```{note}
Only developers with write rights to the Sonatype Central namespace under `fr.pilato`
can perform the release.

Only developers with write rights to the [DockerHub repository](https://hub.docker.com/r/dadoonet/fscrawler/)
can push the Docker images.
```
