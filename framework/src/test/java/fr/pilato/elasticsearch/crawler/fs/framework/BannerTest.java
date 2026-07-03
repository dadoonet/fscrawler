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
package fr.pilato.elasticsearch.crawler.fs.framework;

import fr.pilato.elasticsearch.crawler.fs.test.framework.AbstractFSCrawlerTestCase;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;

class BannerTest extends AbstractFSCrawlerTestCase {

    @Test
    void renderContainsMessagesAndVersion() {
        String banner = Banner.render("9.9.9-TEST");

        Assertions.assertThat(banner)
                // The injected version appears in the banner
                .contains("9.9.9-TEST")
                // The four message lines are present
                .contains("You know, for Files!")
                .contains("Made from France with Love")
                .contains("Source: https://github.com/dadoonet/fscrawler/")
                .contains("Documentation: https://fscrawler.readthedocs.io/")
                // A recognizable fragment of the ASCII art is present
                .contains(",---,.");
    }

    @Test
    void renderProducesFixedWidthBorderedLines() {
        String banner = Banner.render("9.9.9-TEST");

        // Every bordered content line ("|...|") is WIDTH+2 = 102 chars wide
        banner.lines()
                .filter(line -> line.startsWith("|") && line.endsWith("|"))
                .forEach(line -> Assertions.assertThat(line).hasSize(102));
    }
}
