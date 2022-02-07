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

package fr.pilato.elasticsearch.crawler.fs.framework;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.jayway.jsonpath.Configuration;
import com.jayway.jsonpath.DocumentContext;
import com.jayway.jsonpath.JsonPath;
import com.jayway.jsonpath.Option;
import com.jayway.jsonpath.Predicate;
import com.jayway.jsonpath.spi.json.JacksonJsonProvider;
import com.jayway.jsonpath.spi.json.JsonProvider;
import com.jayway.jsonpath.spi.json.JsonSmartJsonProvider;
import com.jayway.jsonpath.spi.mapper.JacksonMappingProvider;
import com.jayway.jsonpath.spi.mapper.JsonSmartMappingProvider;
import com.jayway.jsonpath.spi.mapper.MappingProvider;

import java.io.IOException;
import java.io.InputStream;
import java.util.EnumSet;
import java.util.Map;
import java.util.Set;

import static fr.pilato.elasticsearch.crawler.fs.framework.MetaParser.mapper;

public class JsonUtil {

    public static String serialize(Object object) {
        try {
            return mapper.writeValueAsString(object);
        } catch (JsonProcessingException e) {
            throw new RuntimeException(e);
        }
    }

    public static <T> T deserialize(String json, Class<T> clazz) {
        try {
            return mapper.readValue(json, clazz);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    public static Map<String, Object> asMap(InputStream stream) {
        try {
            return mapper.readValue(stream, new TypeReference<>() {});
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private static Configuration configuration = new Configuration.ConfigurationBuilder()
            .jsonProvider(new JsonSmartJsonProvider())
            .mappingProvider(new JsonSmartMappingProvider())
            .build();

    /**
     * Parse a JSON Document using JSON Path
     * @param json  json to parse
     * @return an Object which can be used as an input for {@link com.jayway.jsonpath.JsonPath#read(Object, String, Predicate...)}
     * @deprecated Use {@link #parseJsonAsDocumentContext(String)} instead
     */
    @Deprecated
    public static Object parseJson(String json) {
        return configuration.jsonProvider().parse(json);
    }

    /**
     * Parse a JSON Document using JSON Path and return a DocumentContext
     * @param json  json to parse
     * @return an Object which can be used as an input for {@link com.jayway.jsonpath.DocumentContext#read(String, Predicate...)}
     */
    public static DocumentContext parseJsonAsDocumentContext(String json) {
        return JsonPath.using(configuration).parse(json);
    }
}
