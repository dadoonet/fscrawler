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

package fr.pilato.elasticsearch.crawler.fs.beans;

import java.util.Map;

/**
 * Represents a document we indexed
 */
public class Doc {

    private String content;
    private String attachment;
    private Meta meta;
    private File file;
    private Path path;
    private Attributes attributes;
    private Map<String, Object> object;
    private Map<String, Object> external;

    public Doc() {
        meta = new Meta();
        file = new File();
        path = new Path();
        external = null;
    }

    public Doc(String content) {
        this();
        this.content = content;
    }

    public Map<String, Object> getObject() {
        return object;
    }

    public void setObject(Map<String, Object> newObject) {
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

    public Map<String, Object> getExternal() {
        return external;
    }

    public void setExternal(Map<String, Object> external) {
        this.external = external;
    }
}
