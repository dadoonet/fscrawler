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
package fr.pilato.elasticsearch.crawler.fs.settings;

import static org.junit.jupiter.api.parallel.ExecutionMode.SAME_THREAD;

import fr.pilato.elasticsearch.crawler.fs.framework.ByteSizeUnit;
import fr.pilato.elasticsearch.crawler.fs.framework.ByteSizeValue;
import fr.pilato.elasticsearch.crawler.fs.framework.FsCrawlerIllegalConfigurationException;
import fr.pilato.elasticsearch.crawler.fs.framework.Percentage;
import fr.pilato.elasticsearch.crawler.fs.framework.TimeValue;
import fr.pilato.elasticsearch.crawler.fs.test.framework.AbstractFSCrawlerTestCase;
import java.io.IOException;
import java.net.URISyntaxException;
import java.nio.file.Path;
import java.util.List;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.assertj.core.api.Assertions;
import org.assertj.core.api.AssertionsForClassTypes;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.Execution;

@Execution(SAME_THREAD)
class FsSettingsLoaderTest extends AbstractFSCrawlerTestCase {

    private static final Logger logger = LogManager.getLogger();

    // Get the config path from resources
    Path configPath;

    @BeforeEach
    @AfterEach
    void cleanupSystemProperties() {
        System.clearProperty("name");
        System.clearProperty("fs.url");
        System.clearProperty("fs.xml_support");
    }

    public FsSettingsLoaderTest() throws URISyntaxException {
        configPath = Path.of(FsSettingsLoaderTest.class.getResource("/config").toURI());
    }

    @Test
    void loadWrongSettings() {
        AssertionsForClassTypes.assertThatExceptionOfType(FsCrawlerIllegalConfigurationException.class)
                .isThrownBy(() -> new FsSettingsLoader(configPath).read("yaml-wrong"))
                .withMessageContaining("Syntax error in configuration file [_settings.yaml]")
                .withMessageContaining("line ")
                .withMessageContaining("column ");
    }

    @Test
    void loadStructuralWrongSettings() {
        AssertionsForClassTypes.assertThatExceptionOfType(FsCrawlerIllegalConfigurationException.class)
                .isThrownBy(() -> new FsSettingsLoader(configPath).read("yaml-structural-wrong"))
                .withMessageContaining("Can not load settings")
                .withMessageContaining("Please make sure that your setting file(s) are properly formatted.");
    }

    @Test
    void loadDeprecatedElasticsearchNodesSettings() throws IOException {
        FsSettings fsSettings = new FsSettingsLoader(configPath).read("yaml-deprecated");
        Assertions.assertThat(fsSettings.getName()).isEqualTo("test_deprecated_elasticsearch");
    }

    @Test
    void loadNonExistingSettings() throws IOException {
        FsSettings doesnotexist = new FsSettingsLoader(configPath).read("doesnotexist");
        FsSettings expected = generateExpectedDefaultFsSettings();
        checkSettings(expected, doesnotexist);
    }

    @Test
    void loadNoFile() {
        checkSettings(generateExpectedDefaultFsSettings(), FsSettingsLoader.load());
    }

    @Test
    void loadJsonSimple() throws IOException {
        FsSettings settings = new FsSettingsLoader(configPath).read("json-simple");
        FsSettings expected = generateExpectedDefaultFsSettings();
        checkSettings(expected, settings);
    }

    @Test
    void loadJsonFull() throws IOException {
        testLoadFullSettings("json-full");
    }

    @Test
    void loadYamlFull() throws IOException {
        testLoadFullSettings("yaml-full");
    }

    @Test
    void loadYamlSplit() throws IOException {
        testLoadFullSettings("yaml-split");
    }

    @Test
    void loadYamlSimple() throws IOException {
        FsSettings settings = new FsSettingsLoader(configPath).read("yaml-simple");
        FsSettings expected = generateExpectedDefaultFsSettings();
        checkSettings(expected, settings);
    }

    private void testLoadFullSettings(String jobName) throws IOException {
        FsSettings settings = new FsSettingsLoader(configPath).read(jobName);
        FsSettings expected = generateExpectedDefaultFsSettings();
        expected.setName("test");
        expected.getFs().setUrl("/path/to/docs");
        expected.getFs().setUpdateRate(TimeValue.timeValueMinutes(5));
        expected.getFs().setIncludes(List.of("*.doc", "*.xls"));
        expected.getFs().setExcludes(List.of("resume.doc"));
        expected.getFs().setFilters(List.of(".*foo.*", "^4\\d{3}([\\ \\-]?)\\d{4}\\1\\d{4}\\1\\d{4}$"));
        expected.getFs().setJsonSupport(true);
        expected.getFs().setAddAsInnerObject(true);
        expected.getFs().setXmlSupport(true);
        expected.getFs().setFollowSymlinks(true);
        expected.getFs().setRemoveDeleted(false);
        expected.getFs().setContinueOnError(true);
        expected.getFs().setIgnoreAbove(new ByteSizeValue(512, ByteSizeUnit.MB));
        expected.getFs().setFilenameAsId(true);
        expected.getFs().setIndexContent(false);
        expected.getFs().setAddFilesize(false);
        expected.getFs().setAttributesSupport(true);
        expected.getFs().setAclSupport(true);
        expected.getFs().setLangDetect(true);
        expected.getFs().setStoreSource(true);
        expected.getFs().setIndexedChars(new Percentage(10000.0));
        expected.getFs().setRawMetadata(true);
        expected.getFs().setChecksum("MD5");
        expected.getFs().setIndexFolders(false);
        expected.getFs().setTikaConfigPath("/path/to/tika-config.xml");
        Ocr ocr = new Ocr();
        ocr.setEnabled(false);
        ocr.setLanguage("fra");
        ocr.setPath("/path/to/tesseract/if/not/available/in/PATH");
        ocr.setDataPath("/path/to/tesseract/tessdata/if/needed");
        ocr.setOutputType("txt");
        ocr.setPdfStrategy("auto");
        ocr.setPageSegMode(1);
        ocr.setPreserveInterwordSpacing(true);
        expected.getFs().setOcr(ocr);
        Tags tags = new Tags();
        tags.setMetaFilename("meta_tags.json");
        tags.setStaticMetaFilename("/path/to/metadatafile.yml");
        expected.setTags(tags);
        expected.setServer(new Server());
        expected.getServer().setHostname("localhost");
        expected.getServer().setPort(22);
        expected.getServer().setUsername("dadoonet");
        expected.getServer().setPassword("password");
        expected.getServer().setProtocol("ssh");
        expected.getServer().setPemPath("/path/to/pemfile");
        expected.getElasticsearch().setUrls(List.of("https://127.0.0.1:9200", "https://127.0.0.1:9201"));
        expected.getElasticsearch().setIndex("test_docs");
        expected.getElasticsearch().setIndexFolder("test_folder");
        expected.getElasticsearch().setBulkSize(1000);
        expected.getElasticsearch().setFlushInterval(TimeValue.timeValueSeconds(5));
        expected.getElasticsearch().setByteSize(new ByteSizeValue(10, ByteSizeUnit.MB));
        expected.getElasticsearch().setApiKey("VnVhQ2ZHY0JDZGJrUW0tZTVhT3g6dWkybHAyYXhUTm1zeWFrdzl0dk5udw==");
        expected.getElasticsearch().setUsername("elastic");
        expected.getElasticsearch().setPassword("password");
        expected.getElasticsearch().setCaCertificate("/path/to/ca.crt");
        expected.getElasticsearch().setPipeline("my_pipeline");
        expected.getElasticsearch().setPushTemplates(true);
        expected.getElasticsearch().setSemanticSearch(true);
        expected.setRest(new Rest());
        expected.getRest().setUrl("http://127.0.0.1:8080");
        expected.getRest().setEnableCors(true);

        checkSettings(expected, settings);
    }

    private void checkSettings(FsSettings expected, FsSettings settings) {
        logger.debug("Settings loaded: {}", settings);
        logger.debug("Settings expected: {}", expected);

        if (expected.getFs() != null) {
            Assertions.assertThat(settings.getFs().getOcr())
                    .as("Checking Ocr")
                    .isEqualTo(expected.getFs().getOcr());
        }
        Assertions.assertThat(settings.getFs()).as("Checking Fs").isEqualTo(expected.getFs());
        Assertions.assertThat(settings.getServer()).as("Checking Server").isEqualTo(expected.getServer());
        Assertions.assertThat(settings.getTags()).as("Checking Tags").isEqualTo(expected.getTags());
        Assertions.assertThat(settings.getElasticsearch())
                .as("Checking Elasticsearch")
                .isEqualTo(expected.getElasticsearch());
        Assertions.assertThat(settings.getRest()).as("Checking Rest").isEqualTo(expected.getRest());
        Assertions.assertThat(settings).as("Checking whole settings").isEqualTo(expected);
    }

    private FsSettings generateExpectedDefaultFsSettings() {
        FsSettings expected = new FsSettings();
        expected.setName("fscrawler");

        Fs fs = new Fs();
        fs.setUrl("/tmp/es");
        fs.setUpdateRate(TimeValue.timeValueMinutes(15));
        fs.setExcludes(List.of("*/~*"));
        fs.setRemoveDeleted(true);
        fs.setIndexContent(true);
        fs.setAddFilesize(true);
        fs.setIndexFolders(true);

        Ocr ocr = new Ocr();
        ocr.setEnabled(true);
        ocr.setLanguage("eng");
        ocr.setOutputType("txt");
        ocr.setPdfStrategy("ocr_and_text");
        ocr.setPageSegMode(1);
        fs.setOcr(ocr);
        expected.setFs(fs);

        Server server = new Server();
        server.setProtocol("local");
        server.setPort(0);
        expected.setServer(server);

        Tags tags = new Tags();
        tags.setMetaFilename(".meta.yml");
        expected.setTags(tags);

        Elasticsearch es = new Elasticsearch();
        es.setUrls(List.of("https://127.0.0.1:9200"));
        es.setIndex(expected.getName() + "_docs");
        es.setIndexFolder(expected.getName() + "_folder");
        es.setBulkSize(100);
        es.setFlushInterval(TimeValue.timeValueSeconds(5));
        es.setByteSize(new ByteSizeValue(10, ByteSizeUnit.MB));
        es.setSslVerification(true);
        es.setPushTemplates(true);
        expected.setElasticsearch(es);

        Rest rest = new Rest();
        rest.setUrl("http://127.0.0.1:8080");
        rest.setEnableCors(false);
        expected.setRest(rest);

        return expected;
    }

    // System properties set here (name, fs.url, fs.xml_support) are cleared before and after each test by
    // cleanupSystemProperties(), so no per-test save/restore is needed.
    @Test
    void withDefaultNamesForEnvVariables() throws Exception {
        System.setProperty("name", "foo");
        System.setProperty("fs.url", "/tmp/test");
        System.setProperty("fs.xml_support", "true");

        FsSettings settings = new FsSettingsLoader(configPath).read("yaml-env-vars");
        FsSettings expected = generateExpectedDefaultFsSettings();
        expected.setName("myname");
        expected.getElasticsearch().setIndex("myname_docs");
        expected.getElasticsearch().setIndexFolder("myname_folder");
        expected.getFs().setUrl("/tmp/test");
        expected.getFs().setXmlSupport(true);
        checkSettings(expected, settings);
    }
}
