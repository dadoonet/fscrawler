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
  remotely (no Docker Hub, `git push`, GitHub release, or production email).

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
* Copy `distribution/target/fscrawler-distribution-<version>.zip` (and the Maven GPG
  `.asc`) to `release/<version>/fscrawler-<version>.zip` so later `mvn clean` does not
  delete them, and write `fscrawler-<version>.zip.sha256`
* Tag the version
* Prepare release notes from `docs/source/release/{version}.md` and GitHub API
* Push Docker images to [Docker Hub](https://hub.docker.com/r/dadoonet/fscrawler/)
  (`mvn deploy` with `skipPublishing=true` on every module — nothing is published to Maven Central).
  `-Prelease` moves the floating tags `latest` (OCR) and `noocr` onto this version.
  SNAPSHOT pushes to `main` use `snapshot` / `snapshot-noocr` instead and must not
  overwrite `latest`.
* Prepare the next SNAPSHOT version, update the README "Latest versions" table
  (HTML comment markers `<!-- release-versions:start -->` / `<!-- release-versions:end -->`),
  and point `.github/dependabot.yml` at the GitHub milestone titled like that SNAPSHOT
  (`3.1-SNAPSHOT` → milestone **3.1**, written as its numeric id)
* Commit the change
* Merge the release branch into the branch you started from (still on the main clone)
* Remove the isolated worktree and delete the release branch
* Push the changes and the tag to origin
* Create (or promote the Release Drafter draft of) the GitHub release with
  `gh release create` / `gh release edit --draft=false`, attaching `fscrawler-<version>.zip`,
  `fscrawler-<version>.zip.asc`, and `fscrawler-<version>.zip.sha256`
* Once that GitHub release looks OK, delete the matching SNAPSHOT pre-release
  (`fscrawler-<version>-SNAPSHOT`) so the next push to `main` publishes the new SNAPSHOT
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

GitHub release, SNAPSHOT, and Docker badges are already dynamic and are not rewritten.

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
`fscrawler-distribution-{version}` (Maven `artifactId`).

## SNAPSHOT pre-releases

Every push to `main` runs `.github/workflows/maven.yml`, which:

* Pushes Docker images to Docker Hub
* Publishes (or overwrites) a public GitHub **pre-release** tagged
  `fscrawler-{version}` (for example `fscrawler-3.1-SNAPSHOT`) with the asset
  `fscrawler-3.1-SNAPSHOT.zip`. Maven still builds
  `distribution/target/fscrawler-distribution-{version}.zip`; the publisher
  copies it to that friendly name before `gh release upload`, because GitHub
  download URLs use the file basename (`gh`'s `file#label` syntax only sets a
  display label). A leftover `fscrawler-distribution-{version}.zip` asset is
  deleted on refresh.

SNAPSHOT pre-releases are **not** GPG-signed. Only stable GitHub releases include
`.asc` and `.sha256`.

The pre-release is created with `--prerelease --latest=false` so it never becomes GitHub's
"Latest" release. The ZIP is overwritten (`gh release upload --clobber`) on every subsequent
push.

### Testing the workflow from a branch

### Testing the workflow from a branch

The Actions **Run workflow** button (`workflow_dispatch`) only appears once this file is on
`main`. Until then, `gh workflow run maven.yml` cannot target another branch either.

Inputs (both default to `false`, same as a push to `main`):

* `skip_docker` — no Docker Hub login/push; `mvn package -Ddocker.skip` still builds the ZIP
* `skip_github` — do not create or overwrite the SNAPSHOT GitHub pre-release

Typical checks after the workflow is on `main`:

```
# GitHub ZIP only (no Docker Hub)
gh workflow run maven.yml --ref <branch> -f skip_docker=true -f skip_github=false

# Docker Hub only
gh workflow run maven.yml --ref <branch> -f skip_docker=false -f skip_github=true

# Build only
gh workflow run maven.yml --ref <branch> -f skip_docker=true -f skip_github=true
```

Leaving both at `false` publishes for real (Docker Hub + `fscrawler-{version}` pre-release).

When `release.sh` publishes the matching stable GitHub release (`fscrawler-3.1`) and you
confirm it looks OK, it deletes that SNAPSHOT pre-release
(`gh release delete fscrawler-3.1-SNAPSHOT --yes --cleanup-tag`). The next push to `main`
creates `fscrawler-3.2-SNAPSHOT`.

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

Docker Hub tags can usually be overwritten by deploying the same tag again.
The GitHub ZIP can be re-uploaded with `gh release upload --clobber` if the release already exists.

## Release Drafter

The repository uses [Release Drafter](https://github.com/release-drafter/release-drafter) to
maintain a **draft** GitHub release on each push to `main`. Tags follow the `fscrawler-{version}`
convention (SNAPSHOT suffix stripped, for example `fscrawler-3.1`). That draft is a different
tag from the SNAPSHOT pre-release (`fscrawler-3.1-SNAPSHOT`). `release.sh` promotes the draft
(`gh release edit --draft=false`) or creates the release, then attaches the ZIP.

Logs are written to `release/<release-version>/release.log`. On failure, the script prints the
last lines of the log and suggests `./release.sh --rollback`.

```{note}
Only developers with write rights to the [DockerHub repository](https://hub.docker.com/r/dadoonet/fscrawler/)
can push the Docker images.

GitHub Releases use the repository `GITHUB_TOKEN` (CI) or an authenticated `gh` CLI (release script).
```
