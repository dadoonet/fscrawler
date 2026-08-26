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
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

/**
 * The README "Latest versions" table is updated by {@code scripts/update_readme_versions.py} using invisible HTML
 * comment markers. The table itself stays readable Markdown — no visible placeholders.
 */
class UpdateReadmeVersionsTest extends AbstractFSCrawlerTestCase {

    private static final String START_MARKER = "<!-- release-versions:start -->";
    private static final String END_MARKER = "<!-- release-versions:end -->";

    @Test
    void readmeLatestVersionsTableIsWrappedInHtmlMarkers() throws Exception {
        String readme = Files.readString(repoRoot().resolve("README.md"));
        assertThat(readme)
                .as("README must wrap the versions table in HTML comments so release.sh can update it")
                .contains(START_MARKER)
                .contains(END_MARKER);
        int start = readme.indexOf(START_MARKER);
        int end = readme.indexOf(END_MARKER);
        assertThat(start).isGreaterThanOrEqualTo(0);
        assertThat(end).as("end marker must come after start marker").isGreaterThan(start);
        String block = readme.substring(start, end);
        assertThat(block)
                .contains("| Elasticsearch |")
                .contains("| FSCrawler")
                .contains("-SNAPSHOT")
                .doesNotContain("{VERSION}");
    }

    @Test
    void updateReadmeVersionsPythonTestsPass() throws Exception {
        Path testsDir = repoRoot().resolve("scripts").resolve("tests");
        ProcessBuilder pb =
                new ProcessBuilder("python3", "-m", "unittest", "discover", "-s", testsDir.toString(), "-v");
        pb.redirectErrorStream(true);
        Process process = pb.start();
        String output = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        int code = process.waitFor();
        assertThat(code).as("python3 -m unittest output:%n%s", output).isZero();
    }

    private static Path repoRoot() {
        return Path.of("..").toAbsolutePath().normalize();
    }
}
