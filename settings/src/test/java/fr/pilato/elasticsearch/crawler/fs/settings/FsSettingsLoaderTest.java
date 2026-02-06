package fr.pilato.elasticsearch.crawler.fs.settings;

import fr.pilato.elasticsearch.crawler.fs.framework.*;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.junit.Test;

import java.io.IOException;
import java.net.URISyntaxException;
import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.AssertionsForClassTypes.assertThatExceptionOfType;

public class FsSettingsLoaderTest {

    private static final Logger logger = LogManager.getLogger();

    // Get the config path from resources
    Path configPath;

    public FsSettingsLoaderTest() throws URISyntaxException {
        configPath = Path.of(FsSettingsLoaderTest.class.getResource("/config").toURI());
    }

    @Test
    public void loadWrongSettings() {
        assertThatExceptionOfType(FsCrawlerIllegalConfigurationException.class).isThrownBy(() ->
                // This should fail
                new FsSettingsLoader(configPath).read("yaml-wrong"));
    }

    @Test
    public void loadDeprecatedElasticsearchNodesSettings() throws IOException {
        FsSettings fsSettings = new FsSettingsLoader(configPath).read("yaml-deprecated");
        assertThat(fsSettings.getName()).isEqualTo("test_deprecated_elasticsearch");
    }

    @Test
    public void loadNonExistingSettings() throws IOException {
        FsSettings doesnotexist = new FsSettingsLoader(configPath).read("doesnotexist");
        FsSettings expected = generateExpectedDefaultFsSettings();
        checkSettings(expected, doesnotexist);
    }

    @Test
    public void loadNoFile() {
        checkSettings(generateExpectedDefaultFsSettings(), FsSettingsLoader.load());
    }

    @Test
    public void loadJsonSimple() throws IOException {
        FsSettings settings = new FsSettingsLoader(configPath).read("json-simple");
        FsSettings expected = generateExpectedDefaultFsSettings();
        checkSettings(expected, settings);
    }

    @Test
    public void loadJsonFull() throws IOException {
        testLoadFullSettings("json-full");
    }

    @Test
    public void loadYamlFull() throws IOException {
        testLoadFullSettings("yaml-full");
    }

    @Test
    public void loadYamlSplit() throws IOException {
        testLoadFullSettings("yaml-split");
    }

    @Test
    public void loadYamlSimple() throws IOException {
        FsSettings settings = new FsSettingsLoader(configPath).read("yaml-simple");
        FsSettings expected = generateExpectedDefaultFsSettings();
        checkSettings(expected, settings);
    }

    @Test
    public void withSystemPropertiesOverridingYaml() throws Exception {
        // System properties should override yaml file values
        System.setProperty("name", "foo");
        System.setProperty("fs.url", "/tmp/test");
        System.setProperty("fs.xml_support", "true");

        try {
            FsSettings settings = new FsSettingsLoader(configPath).read("yaml-env-vars");
            FsSettings expected = generateExpectedDefaultFsSettings();
            // System property overrides the yaml file value "myname"
            expected.setName("foo");

            // Elasticsearch index name depends on the crawler name value ${name}
            expected.getElasticsearch().setIndex("foo_docs");
            expected.getElasticsearch().setIndexFolder("foo_folder");

            // This is set by the system property
            expected.getFs().setUrl("/tmp/test");

            // This is set by the system property
            expected.getFs().setXmlSupport(true);
            checkSettings(expected, settings);
        } finally {
            // Remove the system properties
            System.clearProperty("name");
            System.clearProperty("fs.url");
            System.clearProperty("fs.xml_support");
        }
    }

    @Test
    public void withSystemPropertyOverridingSslVerification() throws Exception {
        // System property should override ssl_verification from defaults
        System.setProperty("elasticsearch.ssl_verification", "false");

        try {
            FsSettings settings = FsSettingsLoader.load();
            // ssl_verification defaults to true, but system property should override it
            assertThat(settings.getElasticsearch().isSslVerification())
                    .as("ssl_verification should be overridden by system property")
                    .isFalse();
        } finally {
            System.clearProperty("elasticsearch.ssl_verification");
        }
    }

    @Test
    public void withSystemPropertyOverridingSslVerificationWithConfigFile() throws Exception {
        // System property should override ssl_verification even when config file sets it to true
        System.setProperty("elasticsearch.ssl_verification", "false");

        try {
            // yaml-simple has default ssl_verification=true
            FsSettings settings = new FsSettingsLoader(configPath).read("yaml-simple");
            // ssl_verification is true in defaults, but system property should override it
            assertThat(settings.getElasticsearch().isSslVerification())
                    .as("ssl_verification should be overridden by system property")
                    .isFalse();
        } finally {
            System.clearProperty("elasticsearch.ssl_verification");
        }
    }

    @Test
    public void withV2SystemPropertyOverrideAfterMigration() throws Exception {
        // System property should override v2 output settings after migration from v1
        System.setProperty("outputs[0].elasticsearch.ssl_verification", "false");

        try {
            // Load v1 settings which will be migrated to v2
            FsSettings settings = new FsSettingsLoader(configPath).read("yaml-simple");
            
            // After migration, outputs should exist
            assertThat(settings.getOutputs())
                    .as("outputs should exist after migration")
                    .isNotNull()
                    .isNotEmpty();
            
            // Check rawConfig has the override applied
            var rawConfig = settings.getOutputs().get(0).getRawConfig();
            assertThat(rawConfig)
                    .as("rawConfig should exist")
                    .isNotNull();
            
            @SuppressWarnings("unchecked")
            var esConfig = (java.util.Map<String, Object>) rawConfig.get("elasticsearch");
            assertThat(esConfig)
                    .as("elasticsearch config should exist in rawConfig")
                    .isNotNull();
            assertThat(esConfig.get("ssl_verification"))
                    .as("ssl_verification should be overridden to false")
                    .isEqualTo(false);
        } finally {
            System.clearProperty("outputs[0].elasticsearch.ssl_verification");
        }
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

        // Compare v1 legacy fields - these are what the loader tests verify
        if (expected.getFs() != null) {
            assertThat(settings.getFs().getOcr()).as("Checking Ocr").isEqualTo(expected.getFs().getOcr());
        }
        assertThat(settings.getFs()).as("Checking Fs").isEqualTo(expected.getFs());
        assertThat(settings.getServer()).as("Checking Server").isEqualTo(expected.getServer());
        assertThat(settings.getTags()).as("Checking Tags").isEqualTo(expected.getTags());
        assertThat(settings.getElasticsearch()).as("Checking Elasticsearch").isEqualTo(expected.getElasticsearch());
        assertThat(settings.getRest()).as("Checking Rest").isEqualTo(expected.getRest());
        
        // Note: We don't check inputs/filters/outputs here because:
        // 1. The loader migrates v1->v2 automatically based on source format
        // 2. Migration results depend on source YAML/JSON structure
        // Pipeline fields are tested separately in FsSettingsMigratorTest
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
}
