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

import java.time.Instant;

/**
 * Represents File attributes
 */
public class File {
    private String extension;
    private String contentType;
    private Instant lastModified;
    private Instant indexingDate;
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

    public Instant getLastModified() {
        return lastModified;
    }

    public void setLastModified(Instant lastModified) {
        this.lastModified = lastModified;
    }

    public Instant getIndexingDate() {
        return indexingDate;
    }

    public void setIndexingDate(Instant indexingDate) {
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

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        File file = (File) o;

        if (extension != null ? !extension.equals(file.extension) : file.extension != null) return false;
        if (contentType != null ? !contentType.equals(file.contentType) : file.contentType != null) return false;
        if (lastModified != null ? !lastModified.equals(file.lastModified) : file.lastModified != null) return false;
        if (indexingDate != null ? !indexingDate.equals(file.indexingDate) : file.indexingDate != null) return false;
        if (filesize != null ? !filesize.equals(file.filesize) : file.filesize != null) return false;
        if (filename != null ? !filename.equals(file.filename) : file.filename != null) return false;
        if (url != null ? !url.equals(file.url) : file.url != null) return false;
        if (indexedChars != null ? !indexedChars.equals(file.indexedChars) : file.indexedChars != null) return false;
        return checksum != null ? checksum.equals(file.checksum) : file.checksum == null;

    }

    @Override
    public int hashCode() {
        int result = extension != null ? extension.hashCode() : 0;
        result = 31 * result + (contentType != null ? contentType.hashCode() : 0);
        result = 31 * result + (lastModified != null ? lastModified.hashCode() : 0);
        result = 31 * result + (indexingDate != null ? indexingDate.hashCode() : 0);
        result = 31 * result + (filesize != null ? filesize.hashCode() : 0);
        result = 31 * result + (filename != null ? filename.hashCode() : 0);
        result = 31 * result + (url != null ? url.hashCode() : 0);
        result = 31 * result + (indexedChars != null ? indexedChars.hashCode() : 0);
        result = 31 * result + (checksum != null ? checksum.hashCode() : 0);
        return result;
    }
}
