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
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;

/** Guards a self-contained distribution POM (no parent, resolved versions). */
class FlattenedDistributionPomTest extends AbstractFSCrawlerTestCase {

    private static final Pattern CLI_VERSION =
            Pattern.compile("<artifactId>fscrawler-cli</artifactId>\\s*<version>[^<]+</version>", Pattern.DOTALL);
    private static final Pattern ES_CLIENT_VERSION = Pattern.compile(
            "<artifactId>fscrawler-elasticsearch-client</artifactId>\\s*<version>[^<]+</version>", Pattern.DOTALL);

    @Test
    void flattenedPomIsSelfContained() throws Exception {
        Path flattenedPom = Path.of(".flattened-pom.xml");
        assertThat(flattenedPom)
                .as("flatten-maven-plugin must write .flattened-pom.xml during process-resources")
                .exists();

        String xml = Files.readString(flattenedPom);
        assertThat(xml).doesNotContain("<parent>");
        assertThat(xml).contains("<groupId>fr.pilato.elasticsearch.crawler</groupId>");
        assertThat(xml).contains("<artifactId>fscrawler-distribution</artifactId>");
        assertThat(xml).contains("<description>");
        assertThat(xml).contains("<url>https://github.com/dadoonet/fscrawler/</url>");
        assertThat(xml).contains("<licenses>");
        assertThat(xml).contains("<scm>");
        assertThat(xml).contains("<url>scm:git:git@github.com:dadoonet/fscrawler.git</url>");
        assertThat(xml).contains("<developers>");
        assertThat(xml).containsPattern(CLI_VERSION);
        assertThat(xml).containsPattern(ES_CLIENT_VERSION);
    }
}
