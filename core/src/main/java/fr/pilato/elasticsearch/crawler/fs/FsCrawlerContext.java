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
package fr.pilato.elasticsearch.crawler.fs;

import fr.pilato.elasticsearch.crawler.fs.beans.Doc;
import fr.pilato.elasticsearch.crawler.fs.crawler.FileAbstractModel;

import java.io.InputStream;
import java.util.HashMap;
import java.util.Map;

/**
 * A class that keeps necessary context for the processing pipeline.
 * Each processor will typically update the 'doc' as needed.
 */
public class FsCrawlerContext {
    private final FileAbstractModel file;
    private final String filepath;
    private final InputStream inputStream;
    private final String fullFilename;
    private final Map<String, Object> extraDoc;
    private Doc doc;

    public FsCrawlerContext(Builder builder) {
        this.file = builder.file;
        this.filepath = builder.filepath;
        this.inputStream = builder.inputStream;
        this.fullFilename = builder.fullFilename;
        this.doc = builder.doc;
        this.extraDoc = builder.extraDoc;
    }

    public FileAbstractModel getFile() {
        return file;
    }

    public String getFilepath() {
        return filepath;
    }

    public void setDoc() {
        this.doc = new Doc();
    }

    public Doc getDoc() {
        return doc;
    }

    public InputStream getInputStream() {
        return inputStream;
    }

    public String getFullFilename() {
        return fullFilename;
    }

    public Map<String, Object> getExtraDoc() {
        return extraDoc;
    }


    public static class Builder {
        private FileAbstractModel file;
        private String filepath;
        private InputStream inputStream;
        private String fullFilename;
        private Doc doc = new Doc();
        private Map<String,Object> extraDoc = new HashMap<>();

        public Builder withFileModel(FileAbstractModel file) {
            this.file = file;
            return this;
        }

        public Builder withFilePath(String filepath) {
            this.filepath = filepath;
            return this;
        }

        public Builder withInputStream(InputStream inputStream) {
            this.inputStream = inputStream;
            return this;
        }

        public Builder withFullFilename(String fullFilename) {
            this.fullFilename = fullFilename;
            return this;
        }

        public Builder withDoc(Doc doc) {
            this.doc = doc;
            return this;
        }

        public Builder withExtraDoc(Map<String,Object> extraDoc) {
            this.extraDoc = extraDoc;
            return this;
        }

        public FsCrawlerContext build() {
            return new FsCrawlerContext(this);
        }

    }
}
