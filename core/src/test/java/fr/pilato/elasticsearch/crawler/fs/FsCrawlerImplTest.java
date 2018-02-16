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

package fr.pilato.elasticsearch.crawler.fs;

import fr.pilato.elasticsearch.crawler.fs.settings.Fs;
import fr.pilato.elasticsearch.crawler.fs.settings.FsSettings;
import fr.pilato.elasticsearch.crawler.fs.test.framework.AbstractFSCrawlerTestCase;
import org.junit.Test;

public class FsCrawlerImplTest extends AbstractFSCrawlerTestCase {
    @Test(expected = RuntimeException.class)
    public void test_checksum_non_existing_algorithm() {
        FsSettings fsSettings = FsSettings.builder("test_checksum_non_existing_algorithm")
                .setFs(Fs.builder().setChecksum("FSCRAWLER").build())
                .build();
        new FsCrawlerImpl(rootTmpDir, fsSettings);
    }

    /**
     * Test case for issue #185: https://github.com/dadoonet/fscrawler/issues/185 : Add xml_support setting
     */
    @Test(expected = RuntimeException.class)
    public void test_xml_and_json_enabled() {
        FsSettings fsSettings = FsSettings.builder("test_xml_and_json_enabled")
                .setFs(Fs.builder().setXmlSupport(true).setJsonSupport(true).build())
                .build();
        new FsCrawlerImpl(rootTmpDir, fsSettings);
    }
}
