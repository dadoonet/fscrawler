/*
 * Licensed to David Pilato (the "Author") under one
 * or more contributor license agreements. See the NOTICE file
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
 *
 * Made from 🇫🇷🇪🇺 with ❤️ - 2011-2026
 */
package fr.pilato.elasticsearch.crawler.fs.beans;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import fr.pilato.elasticsearch.crawler.fs.framework.FsCrawlerIllegalConfigurationException;
import fr.pilato.elasticsearch.crawler.fs.framework.JsonUtil;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.Iterator;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class DocUtils {

    private static final Logger logger = LogManager.getLogger(DocUtils.class);

    /**
     * Merge a json document with tags coming from a file
     *
     * @param doc The document to merge
     * @param filename The filename to read
     * @return The merged document
     * @throws FsCrawlerIllegalConfigurationException If the tags can not be parsed
     */
    public static Doc getMergedDoc(Doc doc, Path filename) throws FsCrawlerIllegalConfigurationException {
        if (filename == null) {
            return doc;
        }

        try (InputStream tags = Files.newInputStream(filename, StandardOpenOption.READ)) {
            return getMergedDoc(doc, filename.getFileName().toString(), tags);
        } catch (IOException e) {
            logger.error("Error parsing tags", e);
            throw new FsCrawlerIllegalConfigurationException("Error parsing tags: " + e.getMessage());
        }
    }

    /**
     * Merge a json document with tags coming from a file
     *
     * @param doc The document to merge
     * @param filename The filename to read
     * @return The merged document
     * @throws FsCrawlerIllegalConfigurationException If the tags can not be parsed
     */
    public static Doc getMergedDoc(Doc doc, String filename, InputStream tags)
            throws FsCrawlerIllegalConfigurationException {
        if (filename == null) {
            return doc;
        }
        logger.trace("Reading tags from {}", filename);
        // We test if the extension is .json or .yml/.yaml
        if (filename.endsWith(".json")) {
            return getMergedDoc(doc, tags, JsonUtil.prettyMapper);
        } else {
            return getMergedDoc(doc, tags, JsonUtil.ymlMapper);
        }
    }

    /**
     * Merge a json document with tags
     *
     * @param doc The document to merge
     * @param tags The tags to merge
     * @return The merged document
     * @throws FsCrawlerIllegalConfigurationException If the tags can not be parsed
     */
    public static Doc getMergedDoc(Doc doc, InputStream tags, ObjectMapper mapper)
            throws FsCrawlerIllegalConfigurationException {
        if (tags == null) {
            return doc;
        }

        try {
            JsonNode tagsNode = mapper.readTree(tags);
            if (tagsNode.isEmpty()) {
                return doc;
            }
            JsonNode docNode = mapper.convertValue(doc, JsonNode.class);
            JsonNode mergedNode = merge(tagsNode, docNode);
            return mapper.treeToValue(mergedNode, Doc.class);
        } catch (Exception e) {
            logger.error("Error parsing tags", e);
            throw new FsCrawlerIllegalConfigurationException("Error parsing tags: " + e.getMessage());
        }
    }

    /**
     * Merges two json nodes into one. Main node overwrites the update node's values in the case if both nodes have the
     * same key.
     *
     * @param mainNode Json node that rules over update node
     * @param updateNode Json node that is subordinate to main node
     * @return the merged nodes
     */
    private static JsonNode merge(JsonNode mainNode, JsonNode updateNode) {
        Iterator<String> fieldNames = updateNode.fieldNames();

        while (fieldNames.hasNext()) {
            String fieldName = fieldNames.next();
            JsonNode jsonNode = mainNode.get(fieldName);

            if (jsonNode != null) {
                if (jsonNode.isObject()) {
                    merge(jsonNode, updateNode.get(fieldName));
                } else if (jsonNode.isArray()) {
                    for (int i = 0; i < jsonNode.size(); i++) {
                        merge(jsonNode.get(i), updateNode.get(fieldName).get(i));
                    }
                }
            } else {
                if (mainNode instanceof ObjectNode objectNode) {
                    // Overwrite field
                    JsonNode value = updateNode.get(fieldName);
                    objectNode.set(fieldName, value);
                }
            }
        }

        return mainNode;
    }

    public static String prettyPrint(Doc doc) {
        try {
            return JsonUtil.prettyMapper.writeValueAsString(doc);
        } catch (JsonProcessingException e) {
            logger.warn("Can not pretty print the document as json", e);
            return null;
        }
    }
}
