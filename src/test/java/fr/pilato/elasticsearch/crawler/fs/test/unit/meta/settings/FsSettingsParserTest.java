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

package fr.pilato.elasticsearch.crawler.fs.test.unit.meta.settings;

import fr.pilato.elasticsearch.crawler.fs.meta.settings.Elasticsearch;
import fr.pilato.elasticsearch.crawler.fs.meta.settings.Fs;
import fr.pilato.elasticsearch.crawler.fs.meta.settings.FsSettings;
import fr.pilato.elasticsearch.crawler.fs.meta.settings.FsSettingsParser;
import fr.pilato.elasticsearch.crawler.fs.meta.settings.Percentage;
import fr.pilato.elasticsearch.crawler.fs.meta.settings.Server;
import fr.pilato.elasticsearch.crawler.fs.meta.settings.TimeValue;
import fr.pilato.elasticsearch.crawler.fs.test.AbstractFSCrawlerTestCase;
import org.junit.Test;

import java.io.IOException;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;

public class FsSettingsParserTest extends AbstractFSCrawlerTestCase {

    private static final Fs FS_EMPTY = Fs.builder().build();
    private static final Fs FS_FULL = Fs.builder()
            .setUrl("/path/to/docs")
            .setStoreSource(true)
            .setAddFilesize(true)
            .addExclude("resume.doc")
            .addInclude("*.doc")
            .addInclude("*.xls")
            .setFilenameAsId(true)
            .setIndexedChars(new Percentage(10000))
            .setRemoveDeleted(true)
            .setUpdateRate(TimeValue.timeValueMinutes(5))
            .setIndexContent(true)
            .build();
    private static final Elasticsearch ELASTICSEARCH_EMPTY = Elasticsearch.builder().build();
    private static final Elasticsearch ELASTICSEARCH_FULL = Elasticsearch.builder()
            .addNode(Elasticsearch.Node.builder()
                    .setHost("127.0.0.1")
                    .setPort(9200)
                    .build())
            .setUsername("username")
            .setPassword("password")
            .setBulkSize(1000)
            .setFlushInterval(TimeValue.timeValueSeconds(5))
            .setIndex("docs")
            .setType("doc")
            .build();
    private static final Server SERVER_EMPTY = Server.builder().build();
    private static final Server SERVER_FULL = Server.builder()
            .setHostname("localhost")
            .setUsername("dadoonet")
            .setPassword("WhATDidYOUexPECt?")
            .setPort(22)
            .setProtocol("SSH")
            .setPemPath("/path/to/pemfile")
            .build();


    private void settingsTester(FsSettings source) throws IOException {
        String json = FsSettingsParser.toJson(source);

        logger.info("-> generated settings: [{}]", json);
        FsSettings generated = FsSettingsParser.fromJson(json);
        assertThat(generated, is(source));
    }

    @Test
    public void testParseEmptySettings() throws IOException {
        settingsTester(
                FsSettings.builder(getCurrentTestName())
                        .build()
        );
    }

    @Test
    public void testParseSettingsEmptyFs() throws IOException {
        settingsTester(
                FsSettings.builder(getCurrentTestName())
                        .setFs(FS_EMPTY)
                        .build()
        );
    }

    @Test
    public void testParseSettingsFs() throws IOException {
        settingsTester(
                FsSettings.builder(getCurrentTestName())
                        .setFs(FS_FULL)
                        .build()
        );
    }

    @Test
    public void testParseSettingsEmptyElasticsearch() throws IOException {
        settingsTester(
                FsSettings.builder(getCurrentTestName())
                        .setElasticsearch(ELASTICSEARCH_EMPTY)
                        .build()
        );
    }

    @Test
    public void testParseSettingsElasticsearchOneNode() throws IOException {
        settingsTester(
                FsSettings.builder(getCurrentTestName())
                        .setElasticsearch(Elasticsearch.builder()
                                .addNode(Elasticsearch.Node.builder()
                                        .setHost("127.0.0.1")
                                        .setPort(9200)
                                        .build())
                                .build())
                        .build()
        );
    }

    @Test
    public void testParseSettingsElasticsearchTwoNodes() throws IOException {
        settingsTester(
                FsSettings.builder(getCurrentTestName())
                        .setElasticsearch(Elasticsearch.builder()
                                .addNode(Elasticsearch.Node.builder()
                                        .setHost("127.0.0.1")
                                        .setPort(9200)
                                        .build())
                                .addNode(Elasticsearch.Node.builder()
                                        .setHost("localhost")
                                        .setPort(9201)
                                        .build())
                                .build())
                        .build()
        );
    }

    @Test
    public void testParseSettingsElasticsearchIndexSettings() throws IOException {
        settingsTester(
                FsSettings.builder(getCurrentTestName())
                        .setElasticsearch(ELASTICSEARCH_FULL)
                        .build()
        );
    }

    @Test
    public void testParseSettingsEmptyServer() throws IOException {
        settingsTester(
                FsSettings.builder(getCurrentTestName())
                        .setServer(SERVER_EMPTY)
                .build()
        );
    }

    @Test
    public void testParseSettingsServer() throws IOException {
        settingsTester(
                FsSettings.builder(getCurrentTestName())
                        .setServer(SERVER_FULL)
                        .build()
        );
    }

    @Test
    public void testParseCompleteSettings() throws IOException {
        settingsTester(
                FsSettings.builder(getCurrentTestName())
                        .setElasticsearch(ELASTICSEARCH_FULL)
                        .setServer(SERVER_FULL)
                        .setFs(FS_FULL)
                        .build()
        );
    }
}
