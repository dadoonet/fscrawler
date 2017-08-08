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

package fr.pilato.elasticsearch.crawler.fs.meta.doc;

import java.util.Date;

/**
 * Represents File attributes
 */
public class File {

    /**
     * Generated json field names
     */
    static public final class FIELD_NAMES {
        public static final String EXTENSION = "extension";
        public static final String CONTENT_TYPE = "content_type";
        public static final String LAST_MODIFIED = "last_modified";
        public static final String INDEXING_DATE = "indexing_date";
        public static final String FILESIZE = "filesize";
        public static final String FILENAME = "filename";
        public static final String URL = "url";
        public static final String INDEXED_CHARS = "indexed_chars";
        public static final String CHECKSUM = "checksum";
    }

    private String extension;
    private String contentType;
    private Date lastModified;
    private Date indexingDate;
    private Long filesize;
    private String filename;
    private String url;
    private Integer indexedChars;
    private String checksum;

    public String getExtension() {
        return extension;
    }

    public void setExtension(String extension) {
        this.extension = extension;
    }

    public String getContentType() {
        return contentType;
    }

    public void setContentType(String contentType) {
        this.contentType = contentType;
    }

    public Date getLastModified() {
        return lastModified;
    }

    public void setLastModified(Date lastModified) {
        this.lastModified = lastModified;
    }

    public Date getIndexingDate() {
        return indexingDate;
    }

    public void setIndexingDate(Date indexingDate) {
        this.indexingDate = indexingDate;
    }

    public Long getFilesize() {
        return filesize;
    }

    public void setFilesize(Long filesize) {
        this.filesize = filesize;
    }

    public String getFilename() {
        return filename;
    }

    public void setFilename(String filename) {
        this.filename = filename;
    }

    public String getUrl() {
        return url;
    }

    public void setUrl(String url) {
        this.url = url;
    }

    public Integer getIndexedChars() {
        return indexedChars;
    }

    public void setIndexedChars(Integer indexedChars) {
        this.indexedChars = indexedChars;
    }

    public void setChecksum(String checksum) {
        this.checksum = checksum;
    }

    public String getChecksum() {
        return checksum;
    }
}
