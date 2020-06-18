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

import java.util.Objects;

public class Ocr {
    // Language dictionary to be used.
    private String language = "eng";
    // Path to tesseract program
    private String path = null;
    // Path to tesseract data
    private String dataPath = null;
    // Output Type. Can be txt (default) or hocr. null means the default value.
    private String outputType = null;
    // Is OCR enabled or disabled in general
    private boolean enabled = true;
    // Pdf OCR Strategy
    private String pdfStrategy = "ocr_and_text";

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {

        private String language = "eng";
        private String path = null;
        private String dataPath = null;
        private String outputType = null;
        private boolean enabled = true;
        private String pdfStrategy = "ocr_and_text";

        public Builder setLanguage(String language) {
            this.language = language;
            return this;
        }

        public Builder setPath(String path) {
            this.path = path;
            return this;
        }

        public Builder setDataPath(String dataPath) {
            this.dataPath = dataPath;
            return this;
        }

        public Builder setOutputType(String outputType) {
            this.outputType = outputType;
            return this;
        }

        public Builder setEnabled(boolean enabled) {
            this.enabled = enabled;
            return this;
        }

        /**
         * Set the PDF Strategy.
         * @param pdfStrategy the PDF Strategy. Could be "no_ocr", "ocr_only" or "ocr_and_text"
         */
        public Builder setPdfStrategy(String pdfStrategy) {
            this.pdfStrategy = pdfStrategy;
            return this;
        }

        public Ocr build() {
            return new Ocr(language, path, dataPath, outputType, pdfStrategy, enabled);
        }

    }

    public Ocr( ) {

    }

    private Ocr(String language, String path, String dataPath, String outputType, String pdfStrategy, boolean enabled) {
        this.language = language;
        this.path = path;
        this.dataPath = dataPath;
        this.outputType = outputType;
        this.pdfStrategy = pdfStrategy;
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

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
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
                Objects.equals(pdfStrategy, ocr.pdfStrategy);
    }

    @Override
    public int hashCode() {
        return Objects.hash(language, path, dataPath, outputType, enabled, pdfStrategy);
    }

    @Override
    public String toString() {
        return "Ocr{" + "language='" + language + '\'' +
                ", path='" + path + '\'' +
                ", dataPath='" + dataPath + '\'' +
                ", outputType='" + outputType + '\'' +
                ", enabled=" + enabled +
                ", pdfStrategy='" + pdfStrategy + '\'' +
                '}';
    }
}
