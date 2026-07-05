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
package fr.pilato.elasticsearch.crawler.fs.rest;

import fr.pilato.elasticsearch.crawler.fs.framework.JsonUtil;
import jakarta.ws.rs.ext.ContextResolver;
import jakarta.ws.rs.ext.Provider;
import org.apache.logging.log4j.LogManager;
import tools.jackson.databind.json.JsonMapper;

/**
 * Supplies the FSCrawler {@link JsonMapper} to the Jackson 3 Jakarta-RS provider. The provider looks up a
 * {@code ContextResolver<JsonMapper>} (not {@code ObjectMapper}), so this must be typed on {@link JsonMapper} to be
 * picked up; otherwise the provider falls back to a default mapper and our custom (de)serializers are ignored.
 */
@Provider
public class RestJsonProvider implements ContextResolver<JsonMapper> {

    // We initialize the object mapper depending on debug mode
    private static final JsonMapper jsonMapper =
            LogManager.getLogger(RestJsonProvider.class).isDebugEnabled() ? JsonUtil.prettyMapper : JsonUtil.mapper;

    @Override
    public JsonMapper getContext(Class<?> type) {
        return jsonMapper;
    }
}
