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

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.Version;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.module.SimpleModule;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.jayway.jsonpath.Configuration;
import com.jayway.jsonpath.DocumentContext;
import com.jayway.jsonpath.JsonPath;
import com.jayway.jsonpath.Predicate;
import com.jayway.jsonpath.spi.json.JsonSmartJsonProvider;
import com.jayway.jsonpath.spi.mapper.JsonSmartMappingProvider;

import java.io.IOException;
import java.io.InputStream;
import java.util.Map;

public class JsonUtil {

    public static final ObjectMapper prettyMapper;
    public static final ObjectMapper mapper;
    public static final ObjectMapper ymlMapper;
    public static final Configuration configuration = new Configuration.ConfigurationBuilder()
            .jsonProvider(new JsonSmartJsonProvider())
            .mappingProvider(new JsonSmartMappingProvider())
            .build();


    static {
        SimpleModule fscrawler = new SimpleModule("FsCrawler", new Version(2, 0, 0, null,
                "fr.pilato.elasticsearch.crawler", "fscrawler"));
        fscrawler.addSerializer(new TimeValueSerializer());
        fscrawler.addDeserializer(TimeValue.class, new TimeValueDeserializer());
        fscrawler.addSerializer(new PercentageSerializer());
        fscrawler.addDeserializer(Percentage.class, new PercentageDeserializer());
        fscrawler.addSerializer(new ByteSizeValueSerializer());
        fscrawler.addDeserializer(ByteSizeValue.class, new ByteSizeValueDeserializer());

        mapper = new ObjectMapper();
        mapper.registerModule(new JavaTimeModule());
        mapper.registerModule(fscrawler);
        mapper.setPropertyNamingStrategy(PropertyNamingStrategies.SNAKE_CASE);
        mapper.configure(SerializationFeature.WRITE_DATE_TIMESTAMPS_AS_NANOSECONDS, false);
        mapper.configure(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS, false);
        mapper.configure(DeserializationFeature.READ_DATE_TIMESTAMPS_AS_NANOSECONDS, false);
        mapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
        mapper.setSerializationInclusion(JsonInclude.Include.NON_EMPTY);

        prettyMapper = new ObjectMapper();
        prettyMapper.registerModule(new JavaTimeModule());
        prettyMapper.registerModule(fscrawler);
        prettyMapper.configure(SerializationFeature.INDENT_OUTPUT, true);
        prettyMapper.setPropertyNamingStrategy(PropertyNamingStrategies.SNAKE_CASE);
        prettyMapper.configure(SerializationFeature.WRITE_DATE_TIMESTAMPS_AS_NANOSECONDS, false);
        prettyMapper.configure(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS, false);
        prettyMapper.configure(DeserializationFeature.READ_DATE_TIMESTAMPS_AS_NANOSECONDS, false);
        prettyMapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
        prettyMapper.setSerializationInclusion(JsonInclude.Include.NON_EMPTY);

        YAMLFactory yamlFactory = new YAMLFactory();
        ymlMapper = new ObjectMapper(yamlFactory);
        ymlMapper.registerModule(new JavaTimeModule());
        ymlMapper.registerModule(fscrawler);
        ymlMapper.configure(SerializationFeature.INDENT_OUTPUT, true);
        ymlMapper.setPropertyNamingStrategy(PropertyNamingStrategies.SNAKE_CASE);
        ymlMapper.configure(SerializationFeature.WRITE_DATE_TIMESTAMPS_AS_NANOSECONDS, false);
        ymlMapper.configure(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS, false);
        ymlMapper.configure(DeserializationFeature.READ_DATE_TIMESTAMPS_AS_NANOSECONDS, false);
        ymlMapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
        ymlMapper.setSerializationInclusion(JsonInclude.Include.NON_EMPTY);
    }

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

    /**
     * Parse a JSON Document using JSON Path and return a DocumentContext
     * @param json  json to parse
     * @return an Object which can be used as an input for {@link com.jayway.jsonpath.DocumentContext#read(String, Predicate...)}
     */
    public static DocumentContext parseJsonAsDocumentContext(String json) {
        return JsonPath.using(configuration).parse(json);
    }
}
