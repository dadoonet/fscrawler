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
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;

/**
 * {@code latest} / {@code noocr} are floating tags for the last stable release. SNAPSHOT builds must not move them;
 * they use {@code snapshot} / {@code snapshot-noocr} instead.
 */
class DockerImageTagsTest extends AbstractFSCrawlerTestCase {

    @Test
    void distributionPomDelegatesFloatingTagsToParentAliasProperties() throws Exception {
        String distPom = Files.readString(Path.of("pom.xml"));
        assertThat(xmlProperty(distPom, "docker.ocr.tags.0"))
                .as("the version tag stays on the SNAPSHOT or release version")
                .isEqualTo("${project.version}");
        assertThat(xmlProperty(distPom, "docker.ocr.tags.1"))
                .as("SNAPSHOT must not hardcode latest; the parent alias switches snapshot vs latest")
                .isEqualTo("${docker.ocr.alias.tag}");
        assertThat(xmlProperty(distPom, "docker.noocr.tags.0"))
                .as("SNAPSHOT must not hardcode noocr; the parent alias switches snapshot-noocr vs noocr")
                .isEqualTo("${docker.noocr.alias.tag}");
    }

    @Test
    void snapshotAliasTagsAreSnapshotNotLatest() throws Exception {
        String parentPom = Files.readString(repoRoot().resolve("pom.xml"));
        String defaultProperties = parentPom.substring(0, parentPom.indexOf("<profiles>"));
        assertThat(xmlProperty(defaultProperties, "docker.ocr.alias.tag"))
                .as("untagged SNAPSHOT builds publish the snapshot alias, not latest")
                .isEqualTo("snapshot");
        assertThat(xmlProperty(defaultProperties, "docker.noocr.alias.tag")).isEqualTo("snapshot-noocr");
        assertThat(defaultProperties)
                .as("latest must not be a default OCR floating tag")
                .doesNotContain("<docker.ocr.alias.tag>latest</docker.ocr.alias.tag>");
    }

    @Test
    void releaseProfileMovesLatestAndNoocr() throws Exception {
        String releaseProfile = releaseProfileXml(Files.readString(repoRoot().resolve("pom.xml")));
        assertThat(xmlProperty(releaseProfile, "docker.ocr.alias.tag"))
                .as("-Prelease must make docker pull dadoonet/fscrawler resolve to this release")
                .isEqualTo("latest");
        assertThat(xmlProperty(releaseProfile, "docker.noocr.alias.tag")).isEqualTo("noocr");
    }

    @Test
    void installationDocsDescribeLatestAsStableRelease() throws Exception {
        String installation =
                Files.readString(repoRoot().resolve("docs").resolve("source").resolve("installation.md"));
        assertThat(installation)
                .as("users must be told that untagged / latest is the last stable release")
                .contains("last **stable** release")
                .contains("`snapshot`")
                .contains("`snapshot-noocr`")
                .contains("|docker_image|");
    }

    @Test
    void snapshotTestersAreToldToUseTheSnapshotDockerTag() throws Exception {
        String installation =
                Files.readString(repoRoot().resolve("docs").resolve("source").resolve("installation.md"));
        assertThat(installation)
                .as("docker-compose .env must pin the floating Hub tag, not |FSCrawler_version| (3.1-SNAPSHOT)")
                .contains("FSCRAWLER_VERSION=|docker_hub_tag|")
                .doesNotContain("FSCRAWLER_VERSION=|FSCrawler_version|");

        String readme = Files.readString(repoRoot().resolve("README.md"));
        assertThat(readme)
                .as("README must tell SNAPSHOT testers to pull dadoonet/fscrawler:snapshot")
                .contains("dadoonet/fscrawler:snapshot")
                .contains("FSCRAWLER_VERSION=snapshot");
    }

    @Test
    void composeExamplesUseFloatingSnapshotAliasNotProjectVersion() throws Exception {
        String parentPom = Files.readString(repoRoot().resolve("pom.xml"));
        String defaultProperties = parentPom.substring(0, parentPom.indexOf("<profiles>"));
        assertThat(xmlProperty(defaultProperties, "docker.compose.tag"))
                .as("SNAPSHOT compose examples must pull :snapshot, not :3.1-SNAPSHOT")
                .isEqualTo("snapshot");
        assertThat(xmlProperty(releaseProfileXml(parentPom), "docker.compose.tag"))
                .as("released compose examples pin the version tag")
                .isEqualTo("${project.version}");

        Path templates =
                repoRoot().resolve("contrib").resolve("src").resolve("main").resolve("resources");
        for (String example : new String[] {
            "docker-compose-example-elasticsearch", "docker-compose-example-fscrawler", "docker-compose-example-edot"
        }) {
            String env = Files.readString(templates.resolve(example).resolve(".env"));
            assertThat(env)
                    .as("%s/.env must use the compose floating tag", example)
                    .contains("FSCRAWLER_VERSION=${docker.compose.tag}")
                    .doesNotContain("FSCRAWLER_VERSION=${project.version}");
        }
    }

    private static Path repoRoot() {
        return Path.of("..").toAbsolutePath().normalize();
    }

    private static String releaseProfileXml(String parentPom) {
        Matcher matcher = Pattern.compile("<profile>\\s*<id>release</id>.*?</profile>", Pattern.DOTALL)
                .matcher(parentPom);
        assertThat(matcher.find())
                .as("parent POM must define a release profile")
                .isTrue();
        return matcher.group();
    }

    private static String xmlProperty(String xml, String name) {
        Matcher matcher = Pattern.compile("<" + Pattern.quote(name) + ">([^<]*)</" + Pattern.quote(name) + ">")
                .matcher(xml);
        assertThat(matcher.find()).as("missing <%s> in:%n%s", name, xml).isTrue();
        return matcher.group(1);
    }
}
