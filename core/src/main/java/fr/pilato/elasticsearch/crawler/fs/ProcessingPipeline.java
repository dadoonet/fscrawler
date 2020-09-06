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

import fr.pilato.elasticsearch.crawler.fs.client.ElasticsearchClient;
import fr.pilato.elasticsearch.crawler.fs.settings.FsSettings;

import java.security.MessageDigest;
import java.util.Map;

/**
 * Custom pipeline implementations will implement this interface.
 * A pipeline will find the document with basic metadata in the context,
 * and can do various processing, and should end with indexing the document
 * to Elasticsearch through the {@link EsIndexProcessor}
 */
public interface ProcessingPipeline {
    /**
     * Process one file
     * @param ctx the context in which to find the file inputstream as well as
     *            other details about the file
     */
    void processFile(FsCrawlerContext ctx);

    /**
     * Initialize the pipeline with settings and ES client objects
     */
    void init(Config config);

    public static class Config {
        private final FsSettings fsSettings;
        private final ElasticsearchClient esClient;
        private MessageDigest messageDigest;
        private Map<String, Object> configMap;

        public Config(FsSettings fsSettings,
                      ElasticsearchClient esClient,
                      MessageDigest messageDigest,
                      Map<String,Object> configMap) {
            this.fsSettings = fsSettings;
            this.esClient = esClient;
            this.messageDigest = messageDigest;
            this.configMap = configMap;
        }

        public FsSettings getFsSettings() {
            return fsSettings;
        }

        public ElasticsearchClient getEsClient() {
            return esClient;
        }

        public Map<String, Object> getConfigMap() {
            return configMap;
        }

        public MessageDigest getMessageDigest() {
            return messageDigest;
        }
    }
}
