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

package fr.pilato.elasticsearch.crawler.fs.tika;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.xml.XmlMapper;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.IOException;
import java.io.InputStream;
import java.util.Map;

import static fr.pilato.elasticsearch.crawler.fs.framework.JsonUtil.mapper;

/**
 * Parse a XML document and generate a FSCrawler Doc
 */
@SuppressWarnings("unchecked")
public class XmlDocParser {

    private final static Logger logger = LogManager.getLogger(XmlDocParser.class);
    private static final ObjectMapper xmlMapper;

    static {
        xmlMapper = new XmlMapper();
    }

    public static String generate(InputStream inputStream) {
        logger.trace("Converting XML document");
        // Extracting XML content
        // See #185: https://github.com/dadoonet/fscrawler/issues/185

        Map<String, Object> map = generateMap(inputStream);

        // Serialize to JSON
        try {
            String json = mapper.writeValueAsString(map);
            logger.trace("Generated JSON: {}", json);
            return json;
        } catch (JsonProcessingException e) {
            // TODO Fix that code. We should log here and return null.
            throw new RuntimeException(e);
        }
    }

    /**
     * Extracting XML content. See #185: <a href="https://github.com/dadoonet/fscrawler/issues/185">https://github.com/dadoonet/fscrawler/issues/185</a>
     * @param inputStream The XML Stream
     * @return The XML Content as a map
     */
    public static Map<String, Object> generateMap(InputStream inputStream) {
        logger.trace("Converting XML document");
        Map<String, Object> map = asMap(inputStream);

        logger.trace("Generated JSON: {}", map);
        return map;
    }

    private static Map<String, Object> asMap(InputStream stream) {
        try {
            return (Map<String, Object>) xmlMapper.readValue(stream, Object.class);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }
}
