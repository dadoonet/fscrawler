# FSCrawler — Cursor / agent reference

This is the main project reference for Cursor and other coding agents. Keep it in sync with
`.claude/rules/` when conventions change.

## Project

Java 17+ Maven multi-module file system crawler (Apache Tika → Elasticsearch).
See `CLAUDE.md` and `AGENTS.md` for environment-specific notes.

## Standing rules (all agents)

These apply at the same priority level — do not skip them:

1. **TDD** — write a failing test first, confirm red, then fix, then confirm green.
   Details: `.claude/rules/testing.md` → *Test-First Workflow*.
2. **RandomizedTesting** — prefer `RandomizedTest.*(…, randomizedRandomForTests)` over
   incidental hardcoded fixture values. Reproduce with `-Dtests.seed=…`.
   Details: `.claude/rules/testing.md` → *RandomizedTesting*.
3. **Commit messages** — `type(scope): emoji description` with bullet details.
   Details: `.claude/rules/git-workflow.md` → *Commit Message Format*.

## Rule files

| Topic | Path |
|-------|------|
| Architecture / modules | `.claude/rules/architecture.md` |
| Build commands | `.claude/rules/build-commands.md` |
| Testing, TDD, RandomizedTesting | `.claude/rules/testing.md` |
| Code style / Spotless | `.claude/rules/code-style.md` |
| Git / commits / PRs | `.claude/rules/git-workflow.md` |

## Quick commands

```bash
mvn clean package -DskipTests -Ddocker.skip
mvn spotless:apply
mvn test -DskipIntegTests
mvn verify -pl integration-tests -am -DskipUnitTests -Dit.test=ClassName#method
```

Cloud VM notes (Docker, local Elasticsearch): see `AGENTS.md`.
