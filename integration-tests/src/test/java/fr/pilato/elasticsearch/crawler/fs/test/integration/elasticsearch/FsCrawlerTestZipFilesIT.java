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

package fr.pilato.elasticsearch.crawler.fs.test.integration.elasticsearch;

import fr.pilato.elasticsearch.crawler.fs.client.ESSearchRequest;
import fr.pilato.elasticsearch.crawler.fs.framework.TimeValue;
import fr.pilato.elasticsearch.crawler.fs.test.integration.AbstractFsCrawlerITCase;
import org.junit.Test;

/**
 * Test crawler with zip files
 */
public class FsCrawlerTestZipFilesIT extends AbstractFsCrawlerITCase {

    /**
     * Test case for #230: <a href="https://github.com/dadoonet/fscrawler/issues/230">https://github.com/dadoonet/fscrawler/issues/230</a> : Add support for compressed files
     * It's a long job, so we let it run up to 2 minutes
     */
    @Test
    public void test_zip() throws Exception {
        crawler = startCrawler(getCrawlerName(), startCrawlerDefinition().build(), endCrawlerDefinition(getCrawlerName()), null, null,
                TimeValue.timeValueMinutes(2));

        // We expect to have one file
        countTestHelper(new ESSearchRequest().withIndex(getCrawlerName()), 1L, null);
    }
}
