package fr.pilato.elasticsearch.crawler.fs.settings;

import fr.pilato.elasticsearch.crawler.fs.settings.pipeline.FilterSection;
import fr.pilato.elasticsearch.crawler.fs.settings.pipeline.InputSection;
import fr.pilato.elasticsearch.crawler.fs.settings.pipeline.OutputSection;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.junit.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests for FsSettingsMigrator - version detection and migration from v1 to v2 format.
 */
public class FsSettingsMigratorTest {

    private static final Logger logger = LogManager.getLogger();

    @Test
    public void testDetectVersionV1() {
        // V1 format has no version field and no inputs/filters/outputs
        FsSettings v1Settings = createV1LocalSettings();
        int version = FsSettingsMigrator.detectVersion(v1Settings);
        assertThat(version).isEqualTo(FsSettingsMigrator.VERSION_1);
    }

    @Test
    public void testDetectVersionV2WithVersionField() {
        // V2 format with explicit version field
        FsSettings v2Settings = new FsSettings();
        v2Settings.setVersion(2);
        v2Settings.setName("test");
        int version = FsSettingsMigrator.detectVersion(v2Settings);
        assertThat(version).isEqualTo(FsSettingsMigrator.VERSION_2);
    }

    @Test
    public void testDetectVersionV2WithInputs() {
        // V2 format detected by presence of inputs list
        FsSettings v2Settings = new FsSettings();
        v2Settings.setName("test");
        InputSection input = new InputSection();
        input.setType("local");
        v2Settings.setInputs(List.of(input));
        int version = FsSettingsMigrator.detectVersion(v2Settings);
        assertThat(version).isEqualTo(FsSettingsMigrator.VERSION_2);
    }

    @Test
    public void testMigrateV1ToV2_LocalInput() {
        // Load v1 settings with local filesystem
        FsSettings v1Settings = createV1LocalSettings();
        
        // Migrate to v2
        FsSettings v2Settings = FsSettingsMigrator.migrateV1ToV2(v1Settings);
        
        // Verify version is set to 2
        assertThat(v2Settings.getVersion()).isEqualTo(FsSettingsMigrator.VERSION_2);
        
        // Verify input section was created
        assertThat(v2Settings.getInputs()).hasSize(1);
        InputSection input = v2Settings.getInputs().get(0);
        assertThat(input.getType()).isEqualTo("local");
        assertThat(input.getId()).isEqualTo("default");
        
        // Verify filter section was created
        assertThat(v2Settings.getFilters()).hasSize(1);
        FilterSection filter = v2Settings.getFilters().get(0);
        assertThat(filter.getType()).isEqualTo("tika");
        assertThat(filter.getId()).isEqualTo("default");
        
        // Verify output section was created
        assertThat(v2Settings.getOutputs()).hasSize(1);
        OutputSection output = v2Settings.getOutputs().get(0);
        assertThat(output.getType()).isEqualTo("elasticsearch");
        assertThat(output.getId()).isEqualTo("default");
    }

    @Test
    public void testMigrateV1ToV2_SshInput() {
        // Load v1 settings with SSH server
        FsSettings v1Settings = createV1SshSettings();
        
        // Migrate to v2
        FsSettings v2Settings = FsSettingsMigrator.migrateV1ToV2(v1Settings);
        
        // Verify input type is SSH
        assertThat(v2Settings.getInputs()).hasSize(1);
        InputSection input = v2Settings.getInputs().get(0);
        assertThat(input.getType()).isEqualTo("ssh");
    }

    @Test
    public void testMigrateV1ToV2_FtpInput() {
        // Load v1 settings with FTP server
        FsSettings v1Settings = createV1FtpSettings();
        
        // Migrate to v2
        FsSettings v2Settings = FsSettingsMigrator.migrateV1ToV2(v1Settings);
        
        // Verify input type is FTP
        assertThat(v2Settings.getInputs()).hasSize(1);
        InputSection input = v2Settings.getInputs().get(0);
        assertThat(input.getType()).isEqualTo("ftp");
    }

    @Test
    public void testMigrateV1ToV2_JsonFilter() {
        // Load v1 settings with JSON support enabled
        FsSettings v1Settings = createV1LocalSettings();
        v1Settings.getFs().setJsonSupport(true);
        
        // Migrate to v2
        FsSettings v2Settings = FsSettingsMigrator.migrateV1ToV2(v1Settings);
        
        // Verify filter type is JSON
        assertThat(v2Settings.getFilters()).hasSize(1);
        FilterSection filter = v2Settings.getFilters().get(0);
        assertThat(filter.getType()).isEqualTo("json");
    }

    @Test
    public void testMigrateV1ToV2_XmlFilter() {
        // Load v1 settings with XML support enabled
        FsSettings v1Settings = createV1LocalSettings();
        v1Settings.getFs().setXmlSupport(true);
        
        // Migrate to v2
        FsSettings v2Settings = FsSettingsMigrator.migrateV1ToV2(v1Settings);
        
        // Verify filter type is XML
        assertThat(v2Settings.getFilters()).hasSize(1);
        FilterSection filter = v2Settings.getFilters().get(0);
        assertThat(filter.getType()).isEqualTo("xml");
    }

    @Test
    public void testMigrateV1ToV2_NoContentFilter() {
        // Load v1 settings with index content disabled and no OCR
        FsSettings v1Settings = createV1LocalSettings();
        v1Settings.getFs().setIndexContent(false);
        v1Settings.getFs().getOcr().setEnabled(false);
        
        // Migrate to v2
        FsSettings v2Settings = FsSettingsMigrator.migrateV1ToV2(v1Settings);
        
        // Verify filter type is none
        assertThat(v2Settings.getFilters()).hasSize(1);
        FilterSection filter = v2Settings.getFilters().get(0);
        assertThat(filter.getType()).isEqualTo("none");
    }

    @Test
    public void testGenerateV2Yaml() {
        // Migrate a v1 config
        FsSettings v1Settings = createV1LocalSettings();
        FsSettings v2Settings = FsSettingsMigrator.migrateV1ToV2(v1Settings);
        
        // Generate YAML
        String yaml = FsSettingsMigrator.generateV2Yaml(v2Settings);
        
        logger.debug("Generated YAML:\n{}", yaml);
        
        // Verify YAML contains expected elements
        assertThat(yaml).contains("version: 2");
        assertThat(yaml).contains("name: \"test_local\"");
        assertThat(yaml).contains("inputs:");
        assertThat(yaml).contains("type: \"local\"");
        assertThat(yaml).contains("filters:");
        assertThat(yaml).contains("type: \"tika\"");
        assertThat(yaml).contains("outputs:");
        assertThat(yaml).contains("type: \"elasticsearch\"");
    }

    // Helper methods to create test settings

    private FsSettings createV1LocalSettings() {
        FsSettings settings = new FsSettings();
        settings.setName("test_local");
        
        Fs fs = new Fs();
        fs.setUrl("/tmp/data");
        Ocr ocr = new Ocr();
        ocr.setEnabled(true);
        fs.setOcr(ocr);
        settings.setFs(fs);
        
        Server server = new Server();
        server.setProtocol("local");
        settings.setServer(server);
        
        Elasticsearch es = new Elasticsearch();
        es.setUrls(List.of("https://localhost:9200"));
        es.setIndex("test_docs");
        settings.setElasticsearch(es);
        
        return settings;
    }

    private FsSettings createV1SshSettings() {
        FsSettings settings = createV1LocalSettings();
        settings.setName("test_ssh");
        settings.getServer().setProtocol("ssh");
        settings.getServer().setHostname("ssh.example.com");
        settings.getServer().setPort(22);
        settings.getServer().setUsername("user");
        return settings;
    }

    private FsSettings createV1FtpSettings() {
        FsSettings settings = createV1LocalSettings();
        settings.setName("test_ftp");
        settings.getServer().setProtocol("ftp");
        settings.getServer().setHostname("ftp.example.com");
        settings.getServer().setPort(21);
        settings.getServer().setUsername("user");
        return settings;
    }
}
