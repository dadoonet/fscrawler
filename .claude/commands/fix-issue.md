Fix a GitHub issue in FSCrawler following the test-first workflow.

## Workflow

1. **Read the issue**: Use `gh issue view <number>` to read the full issue description and comments.

2. **Understand the problem**: Identify the affected module(s) and class(es).

3. **Reproduce first**: Write or adjust an existing test (`*Test.java` for unit, `*IT.java` for integration) that **fails** with the current behaviour. Run it to confirm it is red.

4. **Fix the code**: Change only what is necessary to make the test pass. Do not refactor unrelated code.

5. **Verify**: Run the test again and confirm it is green.

6. **Create a PR**:
   - Branch name: `fix/<issue-number>-<short-description>`
   - Commit format: `fix(scope): 🐛 description` (see `.claude/rules/git-workflow.md`)
   - Use `gh pr create` targeting `master`
   - Reference the issue in the PR body with `Fixes #<number>`

Ask the user for the issue number if not provided.
