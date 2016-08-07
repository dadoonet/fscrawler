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

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.xml.XmlMapper;
import fr.pilato.elasticsearch.crawler.fs.meta.MetaParser;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.IOException;
import java.io.InputStream;
import java.util.Map;

/**
 * Parse a XML document and generate a FSCrawler Doc
 */
public class XmlDocParser {

    private final static Logger logger = LogManager.getLogger(XmlDocParser.class);
    public static final ObjectMapper xmlMapper;

    static {
        xmlMapper = new XmlMapper();
    }


    public static Map<String, Object> asMap(InputStream stream) {
        try {
            return xmlMapper.readValue(stream, new TypeReference<Map<String, Object>>(){});
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    public static String generate(InputStream inputStream) throws IOException {
        logger.trace("Converting XML document [{}]");
        // Extracting XML content
        // See #185: https://github.com/dadoonet/fscrawler/issues/185

        Map<String, Object> map = asMap(inputStream);

        // Serialize to JSON
        String json = MetaParser.mapper.writeValueAsString(map);

        logger.trace("Generated JSON: {}", json);
        return json;
    }
}
