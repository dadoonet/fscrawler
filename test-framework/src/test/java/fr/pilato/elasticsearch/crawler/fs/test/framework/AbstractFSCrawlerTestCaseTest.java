/*
 * Licensed to David Pilato (the "Author") under one
 * or more contributor license agreements.  See the NOTICE file
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
 */
package fr.pilato.elasticsearch.crawler.fs.test.framework;

import org.junit.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class AbstractFSCrawlerTestCaseTest extends AbstractFSCrawlerTestCase {

    @Test
    public void toUnderscoreCase() {
        assertThat(toUnderscoreCase("")).isEmpty();
        assertThat(toUnderscoreCase("AbstractFSCrawlerTestCaseTest"))
                .isEqualTo("abstract_f_s_crawler_test_case_test");
        assertThat(toUnderscoreCase("abstract_f_s_crawler_test_case_test"))
                .isEqualTo("abstract_f_s_crawler_test_case_test");
    }

    @Test
    public void testCrawlerName() {
        String crawlerName = getCrawlerName();

        // The test depends on the env variable "test.index.prefix" being set to something"
        if (indexPrefix.isEmpty()) {
            assertThat(crawlerName).isEqualTo("fscrawler_abstract_f_s_crawler_test_case_test_test_crawler_name");
        } else {
            assertThat(crawlerName).isEqualTo("fscrawler_" + indexPrefix + "_abstract_f_s_crawler_test_case_test_test_crawler_name");
        }
    }
}
