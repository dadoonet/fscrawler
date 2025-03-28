package fr.pilato.elasticsearch.crawler.fs.settings;

import fr.pilato.elasticsearch.crawler.fs.framework.*;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.junit.Test;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;

import static org.junit.Assert.assertEquals;

public class FsSettingsLoaderTest {

    private static final Logger logger = LogManager.getLogger();

    // Get the config path from resources
    Path configPath = Path.of(FsSettingsLoaderTest.class.getResource("/config").getPath());

    @Test(expected = FsCrawlerIllegalConfigurationException.class)
    public void testLoad() throws IOException {
        // This should fail
        new FsSettingsLoader(configPath).read("doesnotexist");
    }

    @Test
    public void testLoadJsonSimple() throws IOException {
        FsSettings settings = new FsSettingsLoader(configPath).read("json-simple");
        FsSettings expected = new FsSettings();
        expected.setName("test");
        checkSettings(expected, settings);
    }

    @Test
    public void testLoadJsonComplete() throws IOException {
        FsSettings settings = new FsSettingsLoader(configPath).read("json-full");
        FsSettings expected = new FsSettings();
        expected.setName("test");
        // Change the expected object to be conform to the fscraller-full.test.yml file
        expected.setFs(new Fs());
        expected.getFs().setUrl("/path/to/docs");
        expected.getFs().setUpdateRate(TimeValue.timeValueMinutes(5));
        expected.getFs().setIncludes(List.of("*.doc", "*.xls"));
        expected.getFs().setExcludes(List.of("resume.doc"));
        expected.getFs().setFilters(List.of(".*foo.*", "^4\\d{3}([\\ \\-]?)\\d{4}\\1\\d{4}\\1\\d{4}$"));
        expected.getFs().setJsonSupport(false);
        expected.getFs().setAddAsInnerObject(false);
        expected.getFs().setXmlSupport(false);
        expected.getFs().setFollowSymlinks(false);
        expected.getFs().setRemoveDeleted(true);
        expected.getFs().setContinueOnError(false);
        expected.getFs().setIgnoreAbove(new ByteSizeValue(512, ByteSizeUnit.MB));
        expected.getFs().setFilenameAsId(true);
        expected.getFs().setIndexContent(true);
        expected.getFs().setAddFilesize(true);
        expected.getFs().setAttributesSupport(false);
        expected.getFs().setLangDetect(false);
        expected.getFs().setStoreSource(false);
        expected.getFs().setIndexedChars(new Percentage(10000.0));
        expected.getFs().setRawMetadata(false);
        expected.getFs().setChecksum("MD5");
        expected.getFs().setIndexFolders(true);
        expected.getFs().setTikaConfigPath("/path/to/tika-config.xml");
        expected.getFs().getOcr().setEnabled(true);
        expected.getFs().getOcr().setLanguage("eng");
        expected.getFs().getOcr().setPath("/path/to/tesseract/if/not/available/in/PATH");
        expected.getFs().getOcr().setDataPath("/path/to/tesseract/tessdata/if/needed");
        expected.getFs().getOcr().setOutputType("txt");
        expected.getFs().getOcr().setPdfStrategy("ocr_and_text");
        expected.getFs().getOcr().setPageSegMode(1);
        expected.getFs().getOcr().setPreserveInterwordSpacing(false);
        expected.setTags(new Tags("meta_tags.json"));
        expected.setServer(new Server());
        expected.getServer().setHostname("localhost");
        expected.getServer().setPort(22);
        expected.getServer().setUsername("dadoonet");
        expected.getServer().setPassword("password");
        expected.getServer().setProtocol("ssh");
        expected.getServer().setPemPath("/path/to/pemfile");
        expected.setElasticsearch(new Elasticsearch());
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

    @Test
    public void testLoadYamlComplete() throws IOException {
        FsSettings settings = new FsSettingsLoader(configPath).read("yaml-full");
        FsSettings expected = new FsSettings();
        expected.setName("test");
        // Change the expected object to be conform to the fscraller-full.test.yml file
        expected.setFs(new Fs());
        expected.getFs().setUrl("/path/to/docs");
        expected.getFs().setUpdateRate(TimeValue.timeValueMinutes(5));
        expected.getFs().setIncludes(List.of("*.doc", "*.xls"));
        expected.getFs().setExcludes(List.of("resume.doc"));
        expected.getFs().setFilters(List.of(".*foo.*", "^4\\d{3}([\\ \\-]?)\\d{4}\\1\\d{4}\\1\\d{4}$"));
        expected.getFs().setJsonSupport(false);
        expected.getFs().setAddAsInnerObject(false);
        expected.getFs().setXmlSupport(false);
        expected.getFs().setFollowSymlinks(false);
        expected.getFs().setRemoveDeleted(true);
        expected.getFs().setContinueOnError(false);
        expected.getFs().setIgnoreAbove(new ByteSizeValue(512, ByteSizeUnit.MB));
        expected.getFs().setFilenameAsId(true);
        expected.getFs().setIndexContent(true);
        expected.getFs().setAddFilesize(true);
        expected.getFs().setAttributesSupport(false);
        expected.getFs().setLangDetect(false);
        expected.getFs().setStoreSource(false);
        expected.getFs().setIndexedChars(new Percentage(10000.0));
        expected.getFs().setRawMetadata(false);
        expected.getFs().setChecksum("MD5");
        expected.getFs().setIndexFolders(true);
        expected.getFs().setTikaConfigPath("/path/to/tika-config.xml");
        expected.getFs().getOcr().setEnabled(true);
        expected.getFs().getOcr().setLanguage("eng");
        expected.getFs().getOcr().setPath("/path/to/tesseract/if/not/available/in/PATH");
        expected.getFs().getOcr().setDataPath("/path/to/tesseract/tessdata/if/needed");
        expected.getFs().getOcr().setOutputType("txt");
        expected.getFs().getOcr().setPdfStrategy("ocr_and_text");
        expected.getFs().getOcr().setPageSegMode(1);
        expected.getFs().getOcr().setPreserveInterwordSpacing(false);
        expected.setTags(new Tags("meta_tags.json"));
        expected.setServer(new Server());
        expected.getServer().setHostname("localhost");
        expected.getServer().setPort(22);
        expected.getServer().setUsername("dadoonet");
        expected.getServer().setPassword("password");
        expected.getServer().setProtocol("ssh");
        expected.getServer().setPemPath("/path/to/pemfile");
        expected.setElasticsearch(new Elasticsearch());
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

    @Test
    public void testLoadYamlSimple() throws IOException {
        FsSettings settings = new FsSettingsLoader(configPath).read("yaml-simple");
        FsSettings expected = new FsSettings();
        expected.setName("test");
        checkSettings(expected, settings);
    }

    @Test
    public void testLoadYamlSplit() throws IOException {
        FsSettings settings = new FsSettingsLoader(configPath).read("yaml-split");
        FsSettings expected = new FsSettings();
        expected.setName("test");
        // Change the expected object to be conform to the fscraller-full.test.yml file
        expected.setFs(new Fs());
        expected.getFs().setUrl("/path/to/docs");
        expected.getFs().setUpdateRate(TimeValue.timeValueMinutes(5));
        expected.getFs().setIncludes(List.of("*.doc", "*.xls"));
        expected.getFs().setExcludes(List.of("resume.doc"));
        expected.getFs().setFilters(List.of(".*foo.*", "^4\\d{3}([\\ \\-]?)\\d{4}\\1\\d{4}\\1\\d{4}$"));
        expected.getFs().setJsonSupport(false);
        expected.getFs().setAddAsInnerObject(false);
        expected.getFs().setXmlSupport(false);
        expected.getFs().setFollowSymlinks(false);
        expected.getFs().setRemoveDeleted(true);
        expected.getFs().setContinueOnError(false);
        expected.getFs().setIgnoreAbove(new ByteSizeValue(512, ByteSizeUnit.MB));
        expected.getFs().setFilenameAsId(true);
        expected.getFs().setIndexContent(true);
        expected.getFs().setAddFilesize(true);
        expected.getFs().setAttributesSupport(false);
        expected.getFs().setLangDetect(false);
        expected.getFs().setStoreSource(false);
        expected.getFs().setIndexedChars(new Percentage(10000.0));
        expected.getFs().setRawMetadata(false);
        expected.getFs().setChecksum("MD5");
        expected.getFs().setIndexFolders(true);
        expected.getFs().setTikaConfigPath("/path/to/tika-config.xml");
        expected.getFs().getOcr().setEnabled(true);
        expected.getFs().getOcr().setLanguage("eng");
        expected.getFs().getOcr().setPath("/path/to/tesseract/if/not/available/in/PATH");
        expected.getFs().getOcr().setDataPath("/path/to/tesseract/tessdata/if/needed");
        expected.getFs().getOcr().setOutputType("txt");
        expected.getFs().getOcr().setPdfStrategy("ocr_and_text");
        expected.getFs().getOcr().setPageSegMode(1);
        expected.getFs().getOcr().setPreserveInterwordSpacing(false);
        expected.setTags(new Tags("meta_tags.json"));
        expected.setServer(new Server());
        expected.getServer().setHostname("localhost");
        expected.getServer().setPort(22);
        expected.getServer().setUsername("dadoonet");
        expected.getServer().setPassword("password");
        expected.getServer().setProtocol("ssh");
        expected.getServer().setPemPath("/path/to/pemfile");
        expected.setElasticsearch(new Elasticsearch());
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
        logger.info("Settings loaded: {}", settings);
        logger.info("Settings expected: {}", expected);

        assertEquals("Checking Fs", expected.getFs(), settings.getFs());
        assertEquals("Checking Server", expected.getServer(), settings.getServer());
        assertEquals("Checking Tags", expected.getTags(), settings.getTags());
        assertEquals("Checking Elasticsearch", expected.getElasticsearch(), settings.getElasticsearch());
        assertEquals("Checking Rest", expected.getRest(), settings.getRest());
        assertEquals("Checking whole settings", expected, settings);
    }
}
