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

package fr.pilato.elasticsearch.crawler.plugins.filter.tika;

import fr.pilato.elasticsearch.crawler.fs.beans.Doc;
import fr.pilato.elasticsearch.crawler.fs.framework.FsCrawlerIllegalConfigurationException;
import fr.pilato.elasticsearch.crawler.fs.settings.Fs;
import fr.pilato.elasticsearch.crawler.fs.settings.FsSettings;
import fr.pilato.elasticsearch.crawler.fs.settings.Ocr;
import fr.pilato.elasticsearch.crawler.fs.settings.plugin.PluginSettingsLoader;
import fr.pilato.elasticsearch.crawler.fs.settings.plugin.PluginSettingsWriter;
import fr.pilato.elasticsearch.crawler.fs.tika.TikaDocParser;
import fr.pilato.elasticsearch.crawler.plugins.FsCrawlerPluginException;
import fr.pilato.elasticsearch.crawler.plugins.pipeline.PipelineContext;
import fr.pilato.elasticsearch.crawler.plugins.pipeline.filter.AbstractFilterPlugin;
import org.pf4j.Extension;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Path;
import java.util.Map;

/**
 * Filter plugin that uses Apache Tika to extract content and metadata from documents.
 * This is the default filter for most document types (PDF, Office documents, etc.).
 * <p>
 * Configuration example:
 * <pre>
 * filters:
 *   - type: "tika"
 *     id: "content-extraction"
 *     tika:
 *       indexed_chars: 100000
 *       ocr:
 *         enabled: true
 *         language: "eng"
 * </pre>
 */
@Extension
public class TikaFilterPlugin extends AbstractFilterPlugin {

    public static final String TYPE = "tika";
    public static final String DEFAULT_YAML_RESOURCE = "/fr/pilato/elasticsearch/crawler/plugins/filter/tika/tika-filter-default.yaml";
    public static final String DEFAULT_PROPERTIES_RESOURCE = "/fr/pilato/elasticsearch/crawler/plugins/filter/tika/tika-filter-default.properties";

    // Typed settings for per-plugin configuration
    private TikaFilterSettings settings;

    // Configuration fields (populated from settings or legacy config)
    private Boolean indexContent;
    private String indexedChars;
    private Boolean langDetect;
    private Boolean storeSource;
    private Boolean rawMetadata;
    private String tikaConfigPath;
    private Boolean ocrEnabled;
    private String ocrLanguage;
    private String ocrPath;
    private String ocrDataPath;
    private String ocrOutputType;
    private String ocrPdfStrategy;

    @Override
    public String getType() {
        return TYPE;
    }

    // ========== Per-Plugin Configuration (new approach) ==========

    @Override
    public boolean supportsPerPluginConfig() {
        return true;
    }

    @Override
    public String getDefaultYamlResource() {
        return DEFAULT_YAML_RESOURCE;
    }

    @Override
    public String getDefaultPropertiesResource() {
        return DEFAULT_PROPERTIES_RESOURCE;
    }

    @Override
    public String getDefaultSettingsFilename() {
        return "01-tika.yaml";
    }

    @Override
    public String getPluginCategory() {
        return "filters";
    }

    @Override
    public String getDescription() {
        return "Tika content extraction configuration";
    }

    @Override
    public void loadSettings(Path configFile) throws IOException, FsCrawlerIllegalConfigurationException {
        logger.debug("Loading Tika filter settings from [{}]", configFile);
        this.settings = PluginSettingsLoader.load(configFile, TikaFilterSettings.class);
        applySettings();
    }

    @Override
    public void saveSettings(Path configFile) throws IOException {
        if (settings == null) {
            settings = createSettingsFromCurrentConfig();
        }
        logger.debug("Saving Tika filter settings to [{}]", configFile);
        PluginSettingsWriter.write(settings, configFile);
    }

    @Override
    public void migrateFromV1(FsSettings v1Settings) {
        logger.debug("Migrating Tika filter settings from V1 FsSettings");
        settings = new TikaFilterSettings();
        settings.setVersion(TikaFilterSettings.CURRENT_VERSION);
        settings.setId(id != null ? id : "default");
        settings.setType(TYPE);

        if (v1Settings.getFs() != null) {
            Fs fs = v1Settings.getFs();
            settings.setIndexContent(fs.isIndexContent());
            if (fs.getIndexedChars() != null) {
                settings.setIndexedChars(fs.getIndexedChars().toString());
            }
            settings.setLangDetect(fs.isLangDetect());
            settings.setStoreSource(fs.isStoreSource());
            settings.setRawMetadata(fs.isRawMetadata());
            settings.setTikaConfigPath(fs.getTikaConfigPath());

            if (fs.getOcr() != null) {
                TikaFilterSettings.OcrSettings ocrSettings = new TikaFilterSettings.OcrSettings();
                ocrSettings.setEnabled(fs.getOcr().isEnabled());
                ocrSettings.setLanguage(fs.getOcr().getLanguage());
                ocrSettings.setPath(fs.getOcr().getPath());
                ocrSettings.setDataPath(fs.getOcr().getDataPath());
                ocrSettings.setOutputType(fs.getOcr().getOutputType());
                ocrSettings.setPdfStrategy(fs.getOcr().getPdfStrategy());
                settings.setOcr(ocrSettings);
            }
        }

        applySettings();
    }

    /**
     * Applies the loaded settings to the plugin's internal state.
     */
    private void applySettings() {
        this.id = settings.getId();
        this.indexContent = settings.getIndexContent();
        this.indexedChars = settings.getIndexedChars();
        this.langDetect = settings.getLangDetect();
        this.storeSource = settings.getStoreSource();
        this.rawMetadata = settings.getRawMetadata();
        this.tikaConfigPath = settings.getTikaConfigPath();

        if (settings.getOcr() != null) {
            this.ocrEnabled = settings.getOcr().getEnabled();
            this.ocrLanguage = settings.getOcr().getLanguage();
            this.ocrPath = settings.getOcr().getPath();
            this.ocrDataPath = settings.getOcr().getDataPath();
            this.ocrOutputType = settings.getOcr().getOutputType();
            this.ocrPdfStrategy = settings.getOcr().getPdfStrategy();
        }

        if (settings.getWhen() != null) {
            setWhen(settings.getWhen());
        }

        logger.debug("Tika filter [{}] configured with indexed_chars: {}, ocr.enabled: {}",
                id, indexedChars, ocrEnabled);
    }

    /**
     * Creates a settings object from the current plugin configuration.
     */
    private TikaFilterSettings createSettingsFromCurrentConfig() {
        TikaFilterSettings s = new TikaFilterSettings();
        s.setVersion(TikaFilterSettings.CURRENT_VERSION);
        s.setId(id);
        s.setType(TYPE);
        s.setWhen(getWhen());
        s.setIndexContent(indexContent);
        s.setIndexedChars(indexedChars);
        s.setLangDetect(langDetect);
        s.setStoreSource(storeSource);
        s.setRawMetadata(rawMetadata);
        s.setTikaConfigPath(tikaConfigPath);

        if (ocrEnabled != null || ocrLanguage != null) {
            TikaFilterSettings.OcrSettings ocrSettings = new TikaFilterSettings.OcrSettings();
            ocrSettings.setEnabled(ocrEnabled);
            ocrSettings.setLanguage(ocrLanguage);
            ocrSettings.setPath(ocrPath);
            ocrSettings.setDataPath(ocrDataPath);
            ocrSettings.setOutputType(ocrOutputType);
            ocrSettings.setPdfStrategy(ocrPdfStrategy);
            s.setOcr(ocrSettings);
        }

        return s;
    }

    // ========== Legacy Configuration (backward compatibility) ==========

    @Override
    protected void configureTypeSpecific(Map<String, Object> typeConfig) {
        // Read Tika-specific configuration
        this.indexContent = getConfigValue(typeConfig, "index_content", Boolean.class, null);
        this.indexedChars = getConfigValue(typeConfig, "indexed_chars", String.class, null);
        this.langDetect = getConfigValue(typeConfig, "lang_detect", Boolean.class, null);
        this.storeSource = getConfigValue(typeConfig, "store_source", Boolean.class, null);
        this.rawMetadata = getConfigValue(typeConfig, "raw_metadata", Boolean.class, null);
        this.tikaConfigPath = getConfigValue(typeConfig, "tika_config_path", String.class, null);
        
        // OCR settings
        @SuppressWarnings("unchecked")
        Map<String, Object> ocrConfig = (Map<String, Object>) typeConfig.get("ocr");
        if (ocrConfig != null) {
            this.ocrEnabled = getConfigValue(ocrConfig, "enabled", Boolean.class, null);
            this.ocrLanguage = getConfigValue(ocrConfig, "language", String.class, null);
            this.ocrPath = getConfigValue(ocrConfig, "path", String.class, null);
            this.ocrDataPath = getConfigValue(ocrConfig, "data_path", String.class, null);
            this.ocrOutputType = getConfigValue(ocrConfig, "output_type", String.class, null);
            this.ocrPdfStrategy = getConfigValue(ocrConfig, "pdf_strategy", String.class, null);
        }

        // Fallback to global settings
        if (globalSettings != null && globalSettings.getFs() != null) {
            Fs fs = globalSettings.getFs();
            if (indexContent == null) {
                indexContent = fs.isIndexContent();
            }
            if (indexedChars == null && fs.getIndexedChars() != null) {
                indexedChars = fs.getIndexedChars().toString();
            }
            if (langDetect == null) {
                langDetect = fs.isLangDetect();
            }
            if (storeSource == null) {
                storeSource = fs.isStoreSource();
            }
            if (rawMetadata == null) {
                rawMetadata = fs.isRawMetadata();
            }
            if (tikaConfigPath == null) {
                tikaConfigPath = fs.getTikaConfigPath();
            }
            if (fs.getOcr() != null) {
                Ocr ocr = fs.getOcr();
                if (ocrEnabled == null) {
                    ocrEnabled = ocr.isEnabled();
                }
                if (ocrLanguage == null) {
                    ocrLanguage = ocr.getLanguage();
                }
                if (ocrPath == null) {
                    ocrPath = ocr.getPath();
                }
                if (ocrDataPath == null) {
                    ocrDataPath = ocr.getDataPath();
                }
                if (ocrOutputType == null) {
                    ocrOutputType = ocr.getOutputType();
                }
                if (ocrPdfStrategy == null) {
                    ocrPdfStrategy = ocr.getPdfStrategy();
                }
            }
        }

        logger.debug("Tika filter [{}] configured with indexed_chars: {}, ocr.enabled: {}", 
                id, indexedChars, ocrEnabled);
    }

    // ========== Validation ==========

    @Override
    public void validateConfiguration() throws FsCrawlerPluginException {
        // Tika filter doesn't require specific configuration
        logger.debug("Tika filter [{}] configuration validated", id);
    }

    /**
     * Returns the typed settings for this plugin.
     * @return the settings, or null if not loaded via per-plugin config
     */
    public TikaFilterSettings getSettings() {
        return settings;
    }

    @Override
    public Doc process(InputStream inputStream, Doc doc, PipelineContext context) throws FsCrawlerPluginException {
        logger.debug("Processing document [{}] with Tika filter [{}]", context.getFilename(), id);

        try {
            // Get file size from context for Tika processing
            long fileSize = context.getSize();

            // Use the global settings which have been enriched with our config
            // TikaDocParser.generate expects FsSettings
            FsSettings settings = buildSettingsForTika();
            
            TikaDocParser.generate(settings, inputStream, doc, fileSize);

            logger.debug("Tika filter [{}] successfully processed document [{}]", id, context.getFilename());
            return doc;
        } catch (Exception e) {
            throw new FsCrawlerPluginException("Failed to process document with Tika: " + e.getMessage(), e);
        }
    }

    /**
     * Builds FsSettings with our configuration for TikaDocParser.
     */
    private FsSettings buildSettingsForTika() {
        // Use global settings as base and override with our config
        if (globalSettings != null) {
            // TikaDocParser uses globalSettings directly, so we return it
            // The config from this plugin is already merged into globalSettings' Fs section
            return globalSettings;
        }

        // Create minimal settings if no global settings
        FsSettings settings = new FsSettings();
        settings.setName(id);
        return settings;
    }

    @Override
    public boolean requiresInputStream() {
        return true;
    }
}
