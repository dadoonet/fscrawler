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

import java.util.Map;

/**
 * Represents a document we indexed
 */
public class Doc {

    /**
     * Generated json field names
     */
    static public final class FIELD_NAMES {
        public static final String CONTENT = "content";
        public static final String ATTACHMENT = "attachment";
        public static final String META = "meta";
        public static final String FILE = "file";
        public static final String PATH = "path";
        public static final String ATTRIBUTES = "attributes";
    }

    private String content;
    private String attachment;
    private Meta meta;
    private File file;
    private Path path;
    private Attributes attributes;
    private Map object;

    public Doc() {
        meta = new Meta();
        file = new File();
        path = new Path();
    }

    public Map getObject() {
        return object;
    }

    public void setObject(Map newObject) {
        this.object = newObject;
    }

    public String getContent() {
        return content;
    }

    public void setContent(String content) {
        this.content = content;
    }

    public String getAttachment() {
        return attachment;
    }

    public void setAttachment(String attachment) {
        this.attachment = attachment;
    }

    public Meta getMeta() {
        return meta;
    }

    public void setMeta(Meta meta) {
        this.meta = meta;
    }

    public File getFile() {
        return file;
    }

    public void setFile(File file) {
        this.file = file;
    }

    public Path getPath() {
        return path;
    }

    public void setPath(Path path) {
        this.path = path;
    }

    public Attributes getAttributes() {
        return attributes;
    }

    public void setAttributes(Attributes attributes) {
        this.attributes = attributes;
    }
}
