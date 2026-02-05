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

package fr.pilato.elasticsearch.crawler.plugins.filter.none;

import fr.pilato.elasticsearch.crawler.fs.beans.Doc;
import fr.pilato.elasticsearch.crawler.plugins.FsCrawlerPluginException;
import fr.pilato.elasticsearch.crawler.plugins.pipeline.PipelineContext;
import fr.pilato.elasticsearch.crawler.plugins.pipeline.filter.AbstractFilterPlugin;
import org.pf4j.Extension;

import java.io.InputStream;
import java.util.Map;

/**
 * Filter plugin that passes documents through without extracting content.
 * This is useful when you want to index file metadata without content.
 * <p>
 * Configuration example:
 * <pre>
 * filters:
 *   - type: "none"
 *     id: "no-content-extraction"
 * </pre>
 */
@Extension
public class NoneFilterPlugin extends AbstractFilterPlugin {

    public static final String TYPE = "none";

    @Override
    public String getType() {
        return TYPE;
    }

    @Override
    protected void configureTypeSpecific(Map<String, Object> typeConfig) {
        // None filter has no specific configuration
        logger.debug("None filter [{}] configured - documents will pass through without content extraction", id);
    }

    @Override
    public void validateConfiguration() throws FsCrawlerPluginException {
        // None filter doesn't require specific configuration
        logger.debug("None filter [{}] configuration validated", id);
    }

    @Override
    public Doc process(InputStream inputStream, Doc doc, PipelineContext context) throws FsCrawlerPluginException {
        logger.debug("Passing document [{}] through None filter [{}] without content extraction", context.getFilename(), id);
        // Simply return the document as-is without processing the input stream
        return doc;
    }

    @Override
    public boolean requiresInputStream() {
        // None filter doesn't need to read the input stream
        return false;
    }
}
