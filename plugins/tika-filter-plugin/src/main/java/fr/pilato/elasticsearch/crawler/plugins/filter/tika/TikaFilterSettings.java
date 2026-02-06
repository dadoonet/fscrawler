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

import fr.pilato.elasticsearch.crawler.fs.settings.plugin.PluginSettings;

import java.util.Objects;

/**
 * Settings for the Tika filter plugin.
 * <p>
 * Example configuration file (01-tika.yaml):
 * <pre>
 * version: 1
 * id: content-extraction
 * type: tika
 * index_content: true
 * indexed_chars: 100000
 * lang_detect: false
 * store_source: false
 * raw_metadata: false
 * ocr:
 *   enabled: true
 *   language: eng
 *   pdf_strategy: ocr_and_text
 *   output_type: txt
 * </pre>
 */
public class TikaFilterSettings extends PluginSettings {

    public static final int CURRENT_VERSION = 1;

    // ========== Conditional execution ==========

    /**
     * MVEL condition for when this filter should be applied.
     */
    private String when;

    // ========== Content extraction settings ==========

    /**
     * Whether to index the document content.
     */
    private Boolean indexContent;

    /**
     * Maximum number of characters to index.
     * Can be a number or a percentage (e.g., "0.5" for 50%).
     */
    private String indexedChars;

    /**
     * Whether to detect the document language.
     */
    private Boolean langDetect;

    /**
     * Whether to store the original file as base64 in the source field.
     */
    private Boolean storeSource;

    /**
     * Whether to include raw metadata without field name normalization.
     */
    private Boolean rawMetadata;

    /**
     * Path to a custom Tika configuration file.
     */
    private String tikaConfigPath;

    // ========== OCR settings ==========

    private OcrSettings ocr;

    /**
     * OCR (Optical Character Recognition) settings.
     */
    public static class OcrSettings {
        private Boolean enabled;
        private String language;
        private String path;
        private String dataPath;
        private String outputType;
        private String pdfStrategy;
        private String pageSegMode;
        private Boolean preserveInterwordSpacing;

        public Boolean getEnabled() {
            return enabled;
        }

        public void setEnabled(Boolean enabled) {
            this.enabled = enabled;
        }

        public String getLanguage() {
            return language;
        }

        public void setLanguage(String language) {
            this.language = language;
        }

        public String getPath() {
            return path;
        }

        public void setPath(String path) {
            this.path = path;
        }

        public String getDataPath() {
            return dataPath;
        }

        public void setDataPath(String dataPath) {
            this.dataPath = dataPath;
        }

        public String getOutputType() {
            return outputType;
        }

        public void setOutputType(String outputType) {
            this.outputType = outputType;
        }

        public String getPdfStrategy() {
            return pdfStrategy;
        }

        public void setPdfStrategy(String pdfStrategy) {
            this.pdfStrategy = pdfStrategy;
        }

        public String getPageSegMode() {
            return pageSegMode;
        }

        public void setPageSegMode(String pageSegMode) {
            this.pageSegMode = pageSegMode;
        }

        public Boolean getPreserveInterwordSpacing() {
            return preserveInterwordSpacing;
        }

        public void setPreserveInterwordSpacing(Boolean preserveInterwordSpacing) {
            this.preserveInterwordSpacing = preserveInterwordSpacing;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            OcrSettings that = (OcrSettings) o;
            return Objects.equals(enabled, that.enabled) &&
                    Objects.equals(language, that.language) &&
                    Objects.equals(path, that.path) &&
                    Objects.equals(dataPath, that.dataPath) &&
                    Objects.equals(outputType, that.outputType) &&
                    Objects.equals(pdfStrategy, that.pdfStrategy) &&
                    Objects.equals(pageSegMode, that.pageSegMode) &&
                    Objects.equals(preserveInterwordSpacing, that.preserveInterwordSpacing);
        }

        @Override
        public int hashCode() {
            return Objects.hash(enabled, language, path, dataPath, outputType, 
                    pdfStrategy, pageSegMode, preserveInterwordSpacing);
        }

        @Override
        public String toString() {
            return "OcrSettings{" +
                    "enabled=" + enabled +
                    ", language='" + language + '\'' +
                    ", pdfStrategy='" + pdfStrategy + '\'' +
                    '}';
        }
    }

    // ========== PluginSettings implementation ==========

    @Override
    public String getExpectedType() {
        return TikaFilterPlugin.TYPE;
    }

    @Override
    public int getCurrentVersion() {
        return CURRENT_VERSION;
    }

    // ========== Getters and Setters ==========

    public String getWhen() {
        return when;
    }

    public void setWhen(String when) {
        this.when = when;
    }

    public Boolean getIndexContent() {
        return indexContent;
    }

    public void setIndexContent(Boolean indexContent) {
        this.indexContent = indexContent;
    }

    public String getIndexedChars() {
        return indexedChars;
    }

    public void setIndexedChars(String indexedChars) {
        this.indexedChars = indexedChars;
    }

    public Boolean getLangDetect() {
        return langDetect;
    }

    public void setLangDetect(Boolean langDetect) {
        this.langDetect = langDetect;
    }

    public Boolean getStoreSource() {
        return storeSource;
    }

    public void setStoreSource(Boolean storeSource) {
        this.storeSource = storeSource;
    }

    public Boolean getRawMetadata() {
        return rawMetadata;
    }

    public void setRawMetadata(Boolean rawMetadata) {
        this.rawMetadata = rawMetadata;
    }

    public String getTikaConfigPath() {
        return tikaConfigPath;
    }

    public void setTikaConfigPath(String tikaConfigPath) {
        this.tikaConfigPath = tikaConfigPath;
    }

    public OcrSettings getOcr() {
        return ocr;
    }

    public void setOcr(OcrSettings ocr) {
        this.ocr = ocr;
    }

    // ========== Object methods ==========

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        if (!super.equals(o)) return false;
        TikaFilterSettings that = (TikaFilterSettings) o;
        return Objects.equals(when, that.when) &&
                Objects.equals(indexContent, that.indexContent) &&
                Objects.equals(indexedChars, that.indexedChars) &&
                Objects.equals(langDetect, that.langDetect) &&
                Objects.equals(storeSource, that.storeSource) &&
                Objects.equals(rawMetadata, that.rawMetadata) &&
                Objects.equals(tikaConfigPath, that.tikaConfigPath) &&
                Objects.equals(ocr, that.ocr);
    }

    @Override
    public int hashCode() {
        return Objects.hash(super.hashCode(), when, indexContent, indexedChars, 
                langDetect, storeSource, rawMetadata, tikaConfigPath, ocr);
    }

    @Override
    public String toString() {
        return "TikaFilterSettings{" +
                "version=" + getVersion() +
                ", id='" + getId() + '\'' +
                ", type='" + getType() + '\'' +
                ", indexContent=" + indexContent +
                ", indexedChars='" + indexedChars + '\'' +
                ", langDetect=" + langDetect +
                ", ocr=" + ocr +
                '}';
    }
}
