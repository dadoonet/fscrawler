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

package fr.pilato.elasticsearch.crawler.plugins.filter.json;

import fr.pilato.elasticsearch.crawler.fs.beans.Doc;
import fr.pilato.elasticsearch.crawler.fs.framework.JsonUtil;
import fr.pilato.elasticsearch.crawler.plugins.FsCrawlerPluginException;
import fr.pilato.elasticsearch.crawler.plugins.pipeline.PipelineContext;
import fr.pilato.elasticsearch.crawler.plugins.pipeline.filter.AbstractFilterPlugin;
import org.pf4j.Extension;

import java.io.InputStream;
import java.util.Map;

/**
 * Filter plugin that parses JSON files and adds the content to the document.
 * The JSON content is stored in the doc.object field as a Map.
 * <p>
 * Configuration example:
 * <pre>
 * filters:
 *   - type: "json"
 *     id: "json-parser"
 * </pre>
 */
@Extension
public class JsonFilterPlugin extends AbstractFilterPlugin {

    public static final String TYPE = "json";

    @Override
    public String getType() {
        return TYPE;
    }

    @Override
    protected void configureTypeSpecific(Map<String, Object> typeConfig) {
        // JSON filter has no specific configuration for now
        logger.debug("JSON filter [{}] configured", id);
    }

    @Override
    public void validateConfiguration() throws FsCrawlerPluginException {
        // JSON filter doesn't require specific configuration
        logger.debug("JSON filter [{}] configuration validated", id);
    }

    @Override
    public Doc process(InputStream inputStream, Doc doc, PipelineContext context) throws FsCrawlerPluginException {
        logger.debug("Processing document [{}] with JSON filter [{}]", context.getFilename(), id);

        try {
            // Parse JSON content and add to document
            @SuppressWarnings("unchecked")
            Map<String, Object> jsonContent = JsonUtil.asMap(inputStream);
            doc.setObject(jsonContent);

            logger.debug("JSON filter [{}] successfully parsed document [{}]", id, context.getFilename());
            return doc;
        } catch (Exception e) {
            throw new FsCrawlerPluginException("Failed to parse JSON document: " + e.getMessage(), e);
        }
    }

    @Override
    public boolean requiresInputStream() {
        return true;
    }
}
