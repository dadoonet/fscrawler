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

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonToken;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.deser.std.UntypedObjectDeserializer;
import com.fasterxml.jackson.databind.module.SimpleModule;
import com.fasterxml.jackson.dataformat.xml.XmlMapper;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static fr.pilato.elasticsearch.crawler.fs.framework.MetaParser.mapper;

/**
 * Parse a XML document and generate a FSCrawler Doc
 */
public class XmlDocParser {

    private final static Logger logger = LogManager.getLogger(XmlDocParser.class);
    private static final ObjectMapper xmlMapper;

    static {
        xmlMapper = new XmlMapper();
        xmlMapper.registerModule(new SimpleModule()
                .addDeserializer(Object.class, new FixedUntypedObjectDeserializer()));
    }

    public static String generate(InputStream inputStream) throws IOException {
        logger.trace("Converting XML document [{}]");
        // Extracting XML content
        // See #185: https://github.com/dadoonet/fscrawler/issues/185

        Map<String, Object> map = generateMap(inputStream);

        // Serialize to JSON
        String json = mapper.writeValueAsString(map);

        logger.trace("Generated JSON: {}", json);
        return json;
    }

    /**
     * Extracting XML content. See #185: https://github.com/dadoonet/fscrawler/issues/185
     * @param inputStream The XML Stream
     * @return The XML Content as a map
     */
    public static Map<String, Object> generateMap(InputStream inputStream) {
        logger.trace("Converting XML document [{}]");
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

    // This code is coming from the gist provided at https://github.com/FasterXML/jackson-dataformat-xml/issues/205
    @SuppressWarnings({"deprecation", "serial"})
    public static class FixedUntypedObjectDeserializer extends UntypedObjectDeserializer {
        @Override
        @SuppressWarnings({"unchecked", "rawtypes"})
        protected Object mapObject(JsonParser p, DeserializationContext ctx) throws IOException {
            String firstKey;

            JsonToken t = p.getCurrentToken();

            if (t == JsonToken.START_OBJECT) {
                firstKey = p.nextFieldName();
            } else if (t == JsonToken.FIELD_NAME) {
                firstKey = p.getCurrentName();
            } else {
                if (t != JsonToken.END_OBJECT) {
                    throw ctx.mappingException(handledType(), p.getCurrentToken());
                }
                firstKey = null;
            }

            // empty map might work; but caller may want to modify... so better
            // just give small modifiable
            Map<String, Object> resultMap = new LinkedHashMap<>(2);
            if (firstKey == null) {
                return resultMap;
            }

            p.nextToken();
            resultMap.put(firstKey, deserialize(p, ctx));

            String nextKey;
            while ((nextKey = p.nextFieldName()) != null) {
                p.nextToken();
                if (resultMap.containsKey(nextKey)) {
                    Object listObject = resultMap.get(nextKey);

                    if (!(listObject instanceof List)) {
                        listObject = new ArrayList<>();
                        ((List) listObject).add(resultMap.get(nextKey));
                        resultMap.put(nextKey, listObject);
                    }

                    ((List) listObject).add(deserialize(p, ctx));
                } else {
                    resultMap.put(nextKey, deserialize(p, ctx));
                }
            }

            return resultMap;
        }
    }
}
