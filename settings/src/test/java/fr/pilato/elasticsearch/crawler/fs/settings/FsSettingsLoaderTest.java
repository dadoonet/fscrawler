package fr.pilato.elasticsearch.crawler.fs.settings;

import fr.pilato.elasticsearch.crawler.fs.framework.*;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.junit.Test;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.AssertionsForClassTypes.assertThatExceptionOfType;

public class FsSettingsLoaderTest {

    private static final Logger logger = LogManager.getLogger();

    // Get the config path from resources
    Path configPath = Path.of(FsSettingsLoaderTest.class.getResource("/config").getPath());

    @Test
    public void loadWrongSettings() {
        assertThatExceptionOfType(FsCrawlerIllegalConfigurationException.class).isThrownBy(() ->
                // This should fail
                new FsSettingsLoader(configPath).read("yaml-wrong"));
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
    public void withDefaultNamesForEnvVariables() throws Exception {
        // Create environment variables (system properties)
        System.setProperty("FSCRAWLER_NAME", "foo");
        System.setProperty("FSCRAWLER_FS_URL", "/tmp/test");

        try {
            FsSettings settings = new FsSettingsLoader(configPath).read("yaml-env-vars");
            FsSettings expected = generateExpectedDefaultFsSettings();
            // Although a value is set by a system property, the yaml file takes precedence
            expected.setName("myname");
            expected.getElasticsearch().setIndex("myname");
            expected.getElasticsearch().setIndexFolder("myname_folder");

            // This is set by the system property
            expected.getFs().setUrl("/tmp/test");
            checkSettings(expected, settings);
        } finally {
            // Remove the environment variable
            System.clearProperty("FSCRAWLER_NAME");
            System.clearProperty("FSCRAWLER_FS_URL");
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
        expected.setTags(tags);
        expected.setServer(new Server());
        expected.getServer().setHostname("localhost");
        expected.getServer().setPort(22);
        expected.getServer().setUsername("dadoonet");
        expected.getServer().setPassword("password");
        expected.getServer().setProtocol("ssh");
        expected.getServer().setPemPath("/path/to/pemfile");
        expected.getElasticsearch().setNodes(List.of(
                new ServerUrl("https://127.0.0.1:9200"),
                new ServerUrl("https://127.0.0.1:9201")
        ));
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
        expected.getRest().setUrl("http://127.0.0.1:8080/fscrawler");
        expected.getRest().setEnableCors(true);

        checkSettings(expected, settings);
    }

    private void checkSettings(FsSettings expected, FsSettings settings) {
        logger.debug("Settings loaded: {}", settings);
        logger.debug("Settings expected: {}", expected);

        if (expected.getFs() != null) {
            assertThat(settings.getFs().getOcr()).as("Checking Ocr").isEqualTo(expected.getFs().getOcr());
        }
        assertThat(settings.getFs()).as("Checking Fs").isEqualTo(expected.getFs());
        assertThat(settings.getServer()).as("Checking Server").isEqualTo(expected.getServer());
        assertThat(settings.getTags()).as("Checking Tags").isEqualTo(expected.getTags());
        assertThat(settings.getElasticsearch()).as("Checking Elasticsearch").isEqualTo(expected.getElasticsearch());
        assertThat(settings.getRest()).as("Checking Rest").isEqualTo(expected.getRest());
        assertThat(settings).as("Checking whole settings").isEqualTo(expected);
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
        es.setNodes(List.of(new ServerUrl("https://127.0.0.1:9200")));
        es.setIndex(expected.getName());
        es.setIndexFolder(expected.getName() + "_folder");
        es.setApiKey("");
        es.setBulkSize(100);
        es.setFlushInterval(TimeValue.timeValueSeconds(5));
        es.setByteSize(new ByteSizeValue(10, ByteSizeUnit.MB));
        es.setSslVerification(true);
        es.setPushTemplates(true);
        expected.setElasticsearch(es);

        Rest rest = new Rest();
        rest.setUrl("http://127.0.0.1:8080/fscrawler");
        rest.setEnableCors(false);
        expected.setRest(rest);

        return expected;
    }
}
