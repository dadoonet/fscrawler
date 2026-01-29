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

import fr.pilato.elasticsearch.crawler.fs.framework.MetaFileHandler;

import java.io.IOException;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;

import static fr.pilato.elasticsearch.crawler.fs.framework.JsonUtil.prettyMapper;

/**
 * Stores ACL hashes per document so we can detect ACL-only changes.
 */
public class FsAclsFileHandler extends MetaFileHandler {

    private static final String FILENAME = "_acl_cache.json";

    public FsAclsFileHandler(Path root) {
        super(root);
    }

    public Map<String, String> read(String jobName) throws IOException {
        try {
            return prettyMapper.readValue(readFile(jobName, FILENAME), prettyMapper.getTypeFactory().constructMapType(HashMap.class, String.class, String.class));
        } catch (NoSuchFileException e) {
            return new HashMap<>();
        }
    }

    public void write(String jobName, Map<String, String> cache) throws IOException {
        if (cache == null || cache.isEmpty()) {
            removeFile(jobName, FILENAME);
        } else {
            writeFile(jobName, FILENAME, prettyMapper.writeValueAsString(cache));
        }
    }
}
