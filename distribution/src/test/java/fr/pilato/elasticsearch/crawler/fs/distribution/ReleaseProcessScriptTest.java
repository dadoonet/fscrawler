/*
 * Licensed to David Pilato (the "Author") under one
 * or more contributor license agreements. See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership. Author licenses this
 * file to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 *
 * Made from 🇫🇷🇪🇺 with ❤️ - 2011-2026
 */
package fr.pilato.elasticsearch.crawler.fs.distribution;

import static org.assertj.core.api.Assertions.assertThat;

import fr.pilato.elasticsearch.crawler.fs.test.framework.AbstractFSCrawlerTestCase;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

/**
 * Guards the release-process follow-ups: isolated git worktree, HEAD check, GitHub ZIP asset, and README table update.
 */
class ReleaseProcessScriptTest extends AbstractFSCrawlerTestCase {

    @Test
    void createReleaseBranchUsesGitWorktree() throws Exception {
        String body = functionBody(readReleaseScript(), "create_release_branch");
        assertThat(body)
                .as("the release must run in an isolated git worktree, not checkout -b in the main clone")
                .contains("worktree add")
                .doesNotContain("checkout -q -b")
                .contains("release/worktrees");
    }

    @Test
    void mavenAndGitMutationsRequireReleaseBranchHead() throws Exception {
        String script = readReleaseScript();
        assertThat(script).contains("require_release_head");
        String requireHead = functionBody(script, "require_release_head");
        assertThat(requireHead).contains("RELEASE_BRANCH");
        String mvnRun = functionBody(script, "mvn_run");
        assertThat(mvnRun)
                .as("mvn_run must refuse to run if the worktree left the release branch")
                .contains("require_release_head");
        String gitRun = functionBody(script, "git_run");
        assertThat(gitRun)
                .as("git_run must refuse to mutate git if the worktree left the release branch")
                .contains("require_release_head");
    }

    @Test
    void mavenRunsInsideTheReleaseWorktree() throws Exception {
        String mvnRun = functionBody(readReleaseScript(), "mvn_run");
        assertThat(mvnRun)
                .as("Maven must run in WORK_DIR (the worktree), not the agent's main checkout")
                .contains("WORK_DIR");
    }

    @Test
    void releaseZipIsCopiedBeforeLaterMavenClean() throws Exception {
        String script = readReleaseScript();
        String build = functionBody(script, "build_release");
        assertThat(build)
                .as("the distribution ZIP must be copied out of target/ before bump_development_version runs mvn clean")
                .contains("copy_release_zip");
        String copy = functionBody(script, "copy_release_zip");
        assertThat(copy)
                .contains("fscrawler-distribution-")
                .contains("fscrawler-${RELEASE_VERSION}.zip")
                .doesNotContain("create_github_release");
    }

    @Test
    void githubReleaseAttachesFriendlyZipName() throws Exception {
        String body = functionBody(readReleaseScript(), "create_github_release");
        assertThat(body)
                .as("gh release create must attach the ZIP with display name fscrawler-x.y.zip")
                .contains("gh release create")
                .contains("#fscrawler-${RELEASE_VERSION}.zip");
    }

    @Test
    void bumpDevelopmentVersionUpdatesReadmeTable() throws Exception {
        String bump = functionBody(readReleaseScript(), "bump_development_version");
        assertThat(bump).contains("update_readme_versions.py").contains("commit_all");
        int updater = bump.indexOf("update_readme_versions.py");
        int commit = bump.indexOf("commit_all");
        assertThat(updater)
                .as("README must be updated on the next-development commit, before commit_all")
                .isGreaterThanOrEqualTo(0);
        assertThat(commit).isGreaterThan(updater);
    }

    @Test
    void rollbackRemovesWorktreeBeforeDeletingBranch() throws Exception {
        String rollback = functionBody(readReleaseScript(), "rollback_from_state_file");
        assertThat(rollback).contains("remove_release_worktree");
        int remove = rollback.indexOf("remove_release_worktree");
        int deleteBranch = rollback.indexOf("branch -D");
        assertThat(remove).isGreaterThanOrEqualTo(0);
        assertThat(deleteBranch)
                .as("git worktree remove must happen before branch -D (the branch is locked while checked out)")
                .isGreaterThan(remove);
        String helper = functionBody(readReleaseScript(), "remove_release_worktree");
        assertThat(helper).contains("worktree remove");
    }

    @Test
    void finalizeMergesFromRootWithoutCheckingOutReleaseBranchOnMainTree() throws Exception {
        String finalize = functionBody(readReleaseScript(), "finalize_release");
        assertThat(finalize)
                .as("root clone stays on the original branch; merge there, then drop the worktree")
                .contains("remove_release_worktree")
                .doesNotContain("checkout -q \"${ORIGINAL_BRANCH}\"");
    }

    @Test
    void announcementHeaderUsesGitHubZipName() throws Exception {
        String header = Files.readString(
                repoRoot().resolve("scripts").resolve("templates").resolve("release-header.md"));
        assertThat(header).contains("unzip fscrawler-{VERSION}.zip").contains("cd fscrawler-distribution-{VERSION}");
        String notes = Files.readString(repoRoot().resolve("scripts").resolve("prepare-release-notes.py"));
        assertThat(notes)
                .as("announcement wget must point at the GitHub release asset fscrawler-x.y.zip")
                .contains("github.com")
                .contains("fscrawler-{version}.zip")
                .doesNotContain("fscrawler-distribution-{version}.zip");
    }

    private static Path repoRoot() {
        return Path.of("..").toAbsolutePath().normalize();
    }

    private static String readReleaseScript() throws Exception {
        Path script = repoRoot().resolve("release.sh");
        assertThat(script).as("release.sh must exist at the repository root").exists();
        return Files.readString(script);
    }

    private static String functionBody(String script, String name) {
        String header = name + "() {";
        int start = script.indexOf(header);
        assertThat(start).as("function %s() must exist in release.sh", name).isGreaterThanOrEqualTo(0);
        int end = script.indexOf("\n}", start);
        assertThat(end).as("function %s() must have a closing brace", name).isGreaterThan(start);
        return script.substring(start, end + 2);
    }
}
