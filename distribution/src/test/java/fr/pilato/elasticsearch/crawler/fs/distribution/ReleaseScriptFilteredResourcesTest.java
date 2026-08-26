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
 * {@code distribution/test-scripts/} is generated outside {@code target/}, so {@code mvn clean} does not remove it. The
 * release script must regenerate it on version bumps and restore it on rollback, otherwise a failed release leaves
 * {@code FSCRAWLER_VERSION=3.0} on the SNAPSHOT branch.
 */
class ReleaseScriptFilteredResourcesTest extends AbstractFSCrawlerTestCase {

    @Test
    void versionBumpRegeneratesDistributionTestScripts() throws Exception {
        String regenerate = functionBody(readReleaseScript(), "regenerate_filtered_resources");
        assertThat(regenerate)
                .as("set_project_version must run generate-test-resources so test-scripts get the new version")
                .contains("generate-test-resources");
    }

    @Test
    void rollbackRestoresDistributionTestScripts() throws Exception {
        String rollback = functionBody(readReleaseScript(), "rollback_from_state_file");
        assertThat(rollback)
                .as("--rollback must restore leftover filtered test-scripts before leaving the release branch")
                .contains("distribution/test-scripts");
        assertThat(rollback)
                .as("--rollback must re-filter resources against the restored SNAPSHOT POMs")
                .contains("regenerate_filtered_resources");
    }

    private static String readReleaseScript() throws Exception {
        Path script = Path.of("..", "release.sh");
        assertThat(script)
                .as("release.sh must be reachable from the distribution module basedir")
                .exists();
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
