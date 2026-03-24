# Git Workflow

## Branch Naming

- **Main branch**: `master` (target for all PRs)
- **Feature branches**: `feature/<description>` or `<username>/<description>`
- **Bug fix branches**: `fix/<description>` or `fix/<issue-number>-<description>`

Always create a dedicated branch before starting any implementation work.

## Commit Message Format

```
type(scope): emoji description

- Detail line 1
- Detail line 2
```

### Types and Emojis

| Type       | Emoji | When to use               |
|------------|-------|---------------------------|
| `feat`     | ✨    | New feature               |
| `fix`      | 🐛    | Bug fix                   |
| `docs`     | 📝    | Documentation             |
| `refactor` | ♻️    | Refactoring               |
| `test`     | 🧪    | Tests                     |
| `chore`    | 🔧    | Maintenance, deps, tooling|

### Example

```
fix(core): 🐛 re-check checkpoint nextCheck in between-runs wait

- Allow forced rescan when checkpoint file is updated externally
- Read checkpoint from disk each wait chunk when !userStopped
```

## Documentation Requirement

Any feature addition or change with user-visible impact **must** be accompanied by documentation updates in `docs/source/` (reStructuredText). This includes:
- New configuration options
- New or changed REST API endpoints
- New CLI flags or behaviours
- Changes to default behaviour

Use `/project:document` to assist with this.

## Before Committing

Run Spotless to catch formatting errors:
```bash
mvn spotless:check   # check
mvn spotless:apply   # fix
```

## Pull Requests

- Target branch: `master`
- Title: short (under 70 chars), no colon needed
- Body: bullet summary + test plan
- If the change has user-visible impact, confirm doc updates are included
