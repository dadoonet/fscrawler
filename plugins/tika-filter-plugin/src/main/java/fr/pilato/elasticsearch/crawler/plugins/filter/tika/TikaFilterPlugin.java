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
import fr.pilato.elasticsearch.crawler.fs.settings.FsSettings;
import fr.pilato.elasticsearch.crawler.fs.tika.TikaDocParser;
import fr.pilato.elasticsearch.crawler.plugins.FsCrawlerPluginException;
import fr.pilato.elasticsearch.crawler.plugins.pipeline.PipelineContext;
import fr.pilato.elasticsearch.crawler.plugins.pipeline.filter.AbstractFilterPlugin;
import org.pf4j.Extension;

import java.io.InputStream;
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

    // Configuration from tika section
    private String indexedChars;
    private Boolean ocrEnabled;
    private String ocrLanguage;
    private String ocrPath;
    private String ocrDataPath;
    private String ocrOutputType;

    @Override
    public String getType() {
        return TYPE;
    }

    @Override
    protected void configureTypeSpecific(Map<String, Object> typeConfig) {
        // Read Tika-specific configuration
        this.indexedChars = getConfigValue(typeConfig, "indexed_chars", String.class, null);
        
        // OCR settings
        @SuppressWarnings("unchecked")
        Map<String, Object> ocrConfig = (Map<String, Object>) typeConfig.get("ocr");
        if (ocrConfig != null) {
            this.ocrEnabled = getConfigValue(ocrConfig, "enabled", Boolean.class, null);
            this.ocrLanguage = getConfigValue(ocrConfig, "language", String.class, null);
            this.ocrPath = getConfigValue(ocrConfig, "path", String.class, null);
            this.ocrDataPath = getConfigValue(ocrConfig, "data_path", String.class, null);
            this.ocrOutputType = getConfigValue(ocrConfig, "output_type", String.class, null);
        }

        // Fallback to global settings
        if (globalSettings != null && globalSettings.getFs() != null) {
            if (indexedChars == null && globalSettings.getFs().getIndexedChars() != null) {
                indexedChars = globalSettings.getFs().getIndexedChars().toString();
            }
            if (globalSettings.getFs().getOcr() != null) {
                if (ocrEnabled == null) {
                    ocrEnabled = globalSettings.getFs().getOcr().isEnabled();
                }
                if (ocrLanguage == null) {
                    ocrLanguage = globalSettings.getFs().getOcr().getLanguage();
                }
                if (ocrPath == null) {
                    ocrPath = globalSettings.getFs().getOcr().getPath();
                }
                if (ocrDataPath == null) {
                    ocrDataPath = globalSettings.getFs().getOcr().getDataPath();
                }
                if (ocrOutputType == null) {
                    ocrOutputType = globalSettings.getFs().getOcr().getOutputType();
                }
            }
        }

        logger.debug("Tika filter [{}] configured with indexed_chars: {}, ocr.enabled: {}", 
                id, indexedChars, ocrEnabled);
    }

    @Override
    public void validateConfiguration() throws FsCrawlerPluginException {
        // Tika filter doesn't require specific configuration
        logger.debug("Tika filter [{}] configuration validated", id);
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
