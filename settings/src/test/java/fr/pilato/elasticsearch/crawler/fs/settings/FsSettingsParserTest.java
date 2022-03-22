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

package fr.pilato.elasticsearch.crawler.fs.settings;

import com.fasterxml.jackson.core.JsonProcessingException;
import fr.pilato.elasticsearch.crawler.fs.framework.ByteSizeUnit;
import fr.pilato.elasticsearch.crawler.fs.framework.ByteSizeValue;
import fr.pilato.elasticsearch.crawler.fs.framework.Percentage;
import fr.pilato.elasticsearch.crawler.fs.framework.TimeValue;
import fr.pilato.elasticsearch.crawler.fs.test.framework.AbstractFSCrawlerTestCase;
import org.junit.Test;

import java.io.IOException;
import java.util.Arrays;
import java.util.Collections;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.*;

public class FsSettingsParserTest extends AbstractFSCrawlerTestCase {

    private static final Ocr OCR_FULL = Ocr.builder().setLanguage("eng").setOutputType("txt").build();

    private static final Fs FS_EMPTY = Fs.builder().build();
    private static final Fs FS_FULL = Fs.builder()
            .setUrl("/path/to/docs")
            .setStoreSource(true)
            .setAddFilesize(true)
            .addExclude("resume.doc")
            .addInclude("*.doc")
            .addInclude("*.xls")
            .addFilter("foo")
            .setFilenameAsId(true)
            .setIndexedChars(new Percentage(10000))
            .setRemoveDeleted(true)
            .setUpdateRate(TimeValue.timeValueMinutes(5))
            .setIndexContent(true)
            .setOcr(OCR_FULL)
            .build();
    private static final Elasticsearch ELASTICSEARCH_EMPTY = Elasticsearch.builder().build();
    private static final Elasticsearch ELASTICSEARCH_FULL = Elasticsearch.builder()
            .setNodes(Collections.singletonList(new ServerUrl("http://127.0.0.1")))
            .setUsername("elastic")
            .setPassword("changeme")
            .setBulkSize(1000)
            .setByteSize(ByteSizeValue.parseBytesSizeValue("10mb"))
            .setFlushInterval(TimeValue.timeValueSeconds(5))
            .setIndex("docs")
            .setPipeline("pipeline-id-if-any")
            .build();
    private static final WorkplaceSearch WORKPLACE_SEARCH_EMPTY = WorkplaceSearch.builder().build();
    private static final WorkplaceSearch WORKPLACE_SEARCH_FULL = WorkplaceSearch.builder()
            .setServer(new ServerUrl("https://127.0.0.1:3002"))
            .setUsername("elastic")
            .setUsername("changeme")
            .setId("ID")
            .setName("My local files on /tmp/es")
            .setUrlPrefix("https://127.0.0.1")
            .setBulkSize(100)
            .setFlushInterval(TimeValue.timeValueSeconds(30))
            .build();
    private static final Server SERVER_EMPTY = Server.builder().build();
    private static final Server SERVER_FULL = Server.builder()
            .setHostname("127.0.0.1")
            .setUsername("dadoonet")
            .setPassword("WhATDidYOUexPECt?")
            .setPort(22)
            .setProtocol("SSH")
            .setPemPath("/path/to/pemfile")
            .build();
    private static final Rest REST_FULL = Rest.builder()
            .setEnableCors(false)
            .setUrl(Rest.URL_DEFAULT)
            .build();

    private void settingsTester(FsSettings source) throws IOException {
        String yaml = FsSettingsParser.toYaml(source);

        logger.info("-> testing settings: [{}]", yaml);
        FsSettings generated = FsSettingsParser.fromYaml(yaml);
        assertThat(generated, is(source));
    }

    @Test
    public void testWithSimplestJsonJobFile() throws IOException {
        String json = "{ \"name\" : \"test\" }";
        logger.info("-> testing settings: [{}]", json);
        defaultSettingsTester(FsSettingsParser.fromJson(json));
    }

    @Test
    public void testWithSimplestYamlJobFile() throws IOException {
        String yaml = "name: \"test\"";
        logger.info("-> testing settings: [{}]", yaml);
        defaultSettingsTester(FsSettingsParser.fromYaml(yaml));
    }

    private void defaultSettingsTester(FsSettings settings) {
        // We enrich missing needed values
        FsCrawlerValidator.validateSettings(logger, settings, false);

        // We need to check the default expected settings
        assertThat(settings, notNullValue());
        assertThat(settings.getName(), is("test"));
        assertThat(settings.getServer(), nullValue());
        assertThat(settings.getRest(), nullValue());

        assertThat(settings.getElasticsearch(), notNullValue());
        assertThat(settings.getElasticsearch().getBulkSize(), is(100));
        assertThat(settings.getElasticsearch().getFlushInterval(), is(TimeValue.timeValueSeconds(5)));
        assertThat(settings.getElasticsearch().getByteSize(), is(new ByteSizeValue(10, ByteSizeUnit.MB)));
        assertThat(settings.getElasticsearch().getIndex(), is("test"));
        assertThat(settings.getElasticsearch().getIndexFolder(), is("test_folder"));
        assertThat(settings.getElasticsearch().getNodes(), iterableWithSize(1));
        assertThat(settings.getElasticsearch().getNodes().get(0).getUrl(), is("http://127.0.0.1:9200"));
        assertThat(settings.getElasticsearch().getNodes().get(0).getCloudId(), is(nullValue()));
        assertThat(settings.getElasticsearch().getNodes().get(0).decodedUrl(), is("http://127.0.0.1:9200"));

        assertThat(settings.getElasticsearch().getUsername(), is(nullValue()));
        assertThat(settings.getElasticsearch().getPassword(), is(nullValue()));
        assertThat(settings.getElasticsearch().getPipeline(), is(nullValue()));

        assertThat(settings.getFs(), notNullValue());
        assertThat(settings.getFs().getChecksum(), nullValue());
        assertThat(settings.getFs().getIncludes(), nullValue());
        assertThat(settings.getFs().getExcludes(), contains("*/~*"));
        assertThat(settings.getFs().getIndexedChars(), nullValue());
        assertThat(settings.getFs().getTikaConfigPath(), nullValue());
        assertThat(settings.getFs().getUpdateRate(), is(TimeValue.timeValueMinutes(15)));
        assertThat(settings.getFs().getUrl(), is("/tmp/es"));
        assertThat(settings.getFs().isAddFilesize(), is(true));
        assertThat(settings.getFs().isAttributesSupport(), is(false));
        assertThat(settings.getFs().isAddAsInnerObject(), is(false));
        assertThat(settings.getFs().isFilenameAsId(), is(false));
        assertThat(settings.getFs().isIndexContent(), is(true));
        assertThat(settings.getFs().isIndexFolders(), is(true));
        assertThat(settings.getFs().isJsonSupport(), is(false));
        assertThat(settings.getFs().isLangDetect(), is(false));
        assertThat(settings.getFs().isRawMetadata(), is(false));
        assertThat(settings.getFs().isRemoveDeleted(), is(true));
        assertThat(settings.getFs().isStoreSource(), is(false));
        assertThat(settings.getFs().isXmlSupport(), is(false));
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
                                .setNodes(Collections.singletonList(new ServerUrl("http://127.0.0.1:9200")))
                                .build())
                        .build()
        );
    }

    @Test
    public void testParseSettingsElasticsearchTwoNodes() throws IOException {
        settingsTester(
                FsSettings.builder(getCurrentTestName())
                        .setElasticsearch(Elasticsearch.builder()
                                .setNodes(Arrays.asList(
                                        new ServerUrl("http://127.0.0.1:9200"),
                                        new ServerUrl("http://127.0.0.1:9201")))
                                .build())
                        .build()
        );
    }

    @Test
    public void testParseSettingsElasticsearchTwoNodesWithHttps() throws IOException {
        settingsTester(
                FsSettings.builder(getCurrentTestName())
                        .setElasticsearch(Elasticsearch.builder()
                                .setNodes(Arrays.asList(
                                        new ServerUrl("http://127.0.0.1:9200"),
                                        new ServerUrl("https://localhost:9243")))
                                .build())
                        .build()
        );
    }

    @Test
    public void testParseSettingsElasticsearchWithPathPrefix() throws IOException {
        settingsTester(
                FsSettings.builder(getCurrentTestName())
                        .setElasticsearch(Elasticsearch.builder()
                                .setPathPrefix("/path/to/elasticsearch")
                                .build())
                        .build()
        );
    }

    @Test
    public void testParseSettingsElasticsearchCloudId() throws IOException {
        String cloudId = "wpsearch:ZXUtd2VzdC0zLmF3cy5lbGFzdGljLWNsb3VkLmNvbTo5MjQzJDg4NTYwNTJmNDBkNjQ4YjE5Mzk1ZDQ5YTViZjgwNTA4JDJhYjJmYTU5N2RiNDQwNjJhMGZmMjY2ZTBkZTEwY2Fm";
        FsSettings fsSettings = FsSettings.builder(getCurrentTestName())
                .setElasticsearch(Elasticsearch.builder()
                        .setNodes(Collections.singletonList(new ServerUrl(cloudId)))
                        .build())
                .build();
        settingsTester(fsSettings);

        assertThat(fsSettings.getElasticsearch().getNodes().get(0).getCloudId(), is("wpsearch:ZXUtd2VzdC0zLmF3cy5lbGFzdGljLWNsb3VkLmNvbTo5MjQzJDg4NTYwNTJmNDBkNjQ4YjE5Mzk1ZDQ5YTViZjgwNTA4JDJhYjJmYTU5N2RiNDQwNjJhMGZmMjY2ZTBkZTEwY2Fm"));
        assertThat(fsSettings.getElasticsearch().getNodes().get(0).getUrl(), is(nullValue()));
        assertThat(fsSettings.getElasticsearch().getNodes().get(0).decodedUrl(), is("https://8856052f40d648b19395d49a5bf80508.eu-west-3.aws.elastic-cloud.com:9243"));

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
    public void testParseSettingsEmptyWorkplaceSearch() throws IOException {
        settingsTester(
                FsSettings.builder(getCurrentTestName())
                        .setWorkplaceSearch(WORKPLACE_SEARCH_EMPTY)
                        .build()
        );
    }

    @Test
    public void testParseSettingsWorkplaceSearch() throws IOException {
        settingsTester(
                FsSettings.builder(getCurrentTestName())
                        .setWorkplaceSearch(WORKPLACE_SEARCH_FULL)
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
                        .setWorkplaceSearch(WORKPLACE_SEARCH_FULL)
                        .setServer(SERVER_FULL)
                        .setFs(FS_FULL)
                        .setRest(REST_FULL)
                        .build()
        );
    }

    @Test
    public void testPasswordIsReadableFromJsonSettings() throws IOException {
        String json = "{\n" +
                "  \"name\" : \"test_password_is_readable\",\n" +
                "  \"fs\" : {\n" +
                "    \"url\" : \"/path/to/docs\"\n" +
                "  },\n" +
                "  \"server\" : {\n" +
                "    \"username\" : \"dadoonet\",\n" +
                "    \"password\" : \"" + SERVER_FULL.getPassword() + "\"\n" +
                "  },\n" +
                "  \"elasticsearch\" : {\n" +
                "    \"username\" : \"username\",\n" +
                "    \"password\" : \"" + ELASTICSEARCH_FULL.getPassword() + "\"\n" +
                "  }\n" +
                "}";

        logger.info("-> testing settings: [{}]", json);
        checkPasswordSettings(FsSettingsParser.fromJson(json));
    }

    @Test
    public void testPasswordIsReadableFromYamlSettings() throws IOException {
        String yaml =
                "name: \"test_password_is_readable\"\n" +
                "fs:\n" +
                "  url: \"/path/to/docs\"\n" +
                "server:\n" +
                "  username: \"dadoonet\"\n" +
                "  password: \"" + SERVER_FULL.getPassword() + "\"\n" +
                "elasticsearch:\n" +
                "  username: \"username\"\n" +
                "  password: \"" + ELASTICSEARCH_FULL.getPassword() + "\"\n";

        logger.info("-> testing settings: [{}]", yaml);
        checkPasswordSettings(FsSettingsParser.fromYaml(yaml));
    }

    private void checkPasswordSettings(FsSettings settings) throws JsonProcessingException {
        assertThat(settings.getElasticsearch().getPassword(), is(ELASTICSEARCH_FULL.getPassword()));
        assertThat(settings.getServer().getPassword(), is(SERVER_FULL.getPassword()));

        // Generate the YAML and check that we don't render the passwords
        String filteredYaml = FsSettingsParser.toYaml(settings);
        assertThat(filteredYaml, not(containsString(ELASTICSEARCH_FULL.getPassword())));
        assertThat(filteredYaml, not(containsString(SERVER_FULL.getPassword())));
    }
}
