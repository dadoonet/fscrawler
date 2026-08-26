Update the FSCrawler documentation and generate a changelog summary.

## Workflow

### 1. Analyze recent changes
Run `git log main..HEAD --oneline` to list commits since diverging from main.
For each commit, identify:
- What changed (feature, fix, config option, API endpoint, etc.)
- Which user-facing behaviour is affected

### 2. Update documentation
The user docs live in `docs/source/` (MyST Markdown, built by ReadTheDocs — https://fscrawler.readthedocs.io/).

For each user-facing change:
- Locate the relevant `.md` file in `docs/source/`
- Add or update the description, examples, and configuration options
- Follow the existing MyST style (headings, code blocks, notes)

Key doc files:
- `docs/source/admin/fs/` — filesystem crawler settings
- `docs/source/admin/fs/elasticsearch.md` — Elasticsearch settings
- `docs/source/admin/fs/rest.md` — REST API reference
- `docs/source/installation.md` — installation guide
- `docs/source/dev/` — developer documentation

### 3. Generate changelog summary
Produce a concise markdown changelog entry in the format:

```markdown
## [Unreleased]

### Added
- ...

### Fixed
- ...

### Changed
- ...
```

Present the changelog to the user and ask if it should be written to `CHANGELOG.md` or included in a PR description.
