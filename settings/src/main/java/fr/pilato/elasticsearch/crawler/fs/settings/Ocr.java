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

import jakarta.annotation.Nullable;
import org.github.gestalt.config.annotations.Config;

import java.util.Objects;

public class Ocr {
    // Is OCR enabled or disabled in general
    @Config(defaultVal = "true")
    private boolean enabled;
    // Path to tesseract program
    @Config
    @Nullable private String path;
    // Path to tesseract data
    @Config
    @Nullable private String dataPath;
    // Language dictionary to be used.
    @Config(defaultVal = "eng")
    private String language;
    // Output Type. Can be txt (default) or hocr. null means the default value.
    @Config
    @Nullable private String outputType;
    // Pdf OCR Strategy
    @Config(defaultVal = "ocr_and_text")
    private String pdfStrategy;
    // PDF Page Seg Mode
    @Config
    private int pageSegMode;
    // Preserve interword spacing
    @Config(defaultVal = "false")
    private boolean preserveInterwordSpacing;

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

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public Integer getPageSegMode() {
        return pageSegMode;
    }

    public Boolean getPreserveInterwordSpacing() {
        return this.preserveInterwordSpacing;
    }

    public void setPreserveInterwordSpacing( Boolean preserveInterwordSpacing) {
        this.preserveInterwordSpacing = preserveInterwordSpacing;
    }

    public void setPageSegMode( Integer pageSegMode) {
        this.pageSegMode = pageSegMode;
    }

    /**
     * Get the PDF Strategy. Could be "no_ocr", "auto", "ocr_only" or "ocr_and_text" (default)
     * @return the PDF Strategy
     */
    public String getPdfStrategy() {
        return pdfStrategy;
    }

    /**
     * Set the PDF Strategy.
     * @param pdfStrategy the PDF Strategy. Could be "no_ocr", "auto", "ocr_only" or "ocr_and_text"
     */
    public void setPdfStrategy(String pdfStrategy) {
        this.pdfStrategy = pdfStrategy;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Ocr ocr = (Ocr) o;
        return enabled == ocr.enabled &&
                Objects.equals(language, ocr.language) &&
                Objects.equals(path, ocr.path) &&
                Objects.equals(dataPath, ocr.dataPath) &&
                Objects.equals(outputType, ocr.outputType) &&
                Objects.equals(pdfStrategy, ocr.pdfStrategy) &&
                Objects.equals(pageSegMode, ocr.pageSegMode) &&
                Objects.equals(preserveInterwordSpacing, ocr.preserveInterwordSpacing);
    }

    @Override
    public int hashCode() {
        return Objects.hash(language, path, dataPath, outputType, enabled, pdfStrategy, pageSegMode, preserveInterwordSpacing);
    }

    @Override
    public String toString() {
        return "Ocr{" + "language='" + language + '\'' +
                ", path='" + path + '\'' +
                ", dataPath='" + dataPath + '\'' +
                ", outputType='" + outputType + '\'' +
                ", enabled=" + enabled +
                ", pdfStrategy='" + pdfStrategy + '\'' +
                ", pageSegMode='" + pageSegMode + '\'' +
                ", preserveInterwordSpacing='" + preserveInterwordSpacing + '\'' +
                '}';
    }
}
