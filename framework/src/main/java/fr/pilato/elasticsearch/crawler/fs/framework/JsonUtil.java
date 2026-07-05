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
package fr.pilato.elasticsearch.crawler.fs.framework;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.jayway.jsonpath.Configuration;
import com.jayway.jsonpath.DocumentContext;
import com.jayway.jsonpath.JsonPath;
import com.jayway.jsonpath.Predicate;
import com.jayway.jsonpath.spi.json.JsonSmartJsonProvider;
import com.jayway.jsonpath.spi.mapper.JsonSmartMappingProvider;
import java.io.InputStream;
import java.util.Map;
import tools.jackson.core.JacksonException;
import tools.jackson.core.Version;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.PropertyNamingStrategies;
import tools.jackson.databind.SerializationFeature;
import tools.jackson.databind.cfg.DateTimeFeature;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.module.SimpleModule;
import tools.jackson.dataformat.yaml.YAMLMapper;

public class JsonUtil {

    private JsonUtil() {
        // Utility class, do not instantiate
    }

    public static final JsonMapper prettyMapper;
    public static final JsonMapper mapper;
    public static final ObjectMapper ymlMapper;
    public static final Configuration configuration = new Configuration.ConfigurationBuilder()
            .jsonProvider(new JsonSmartJsonProvider())
            .mappingProvider(new JsonSmartMappingProvider())
            .build();

    static {
        SimpleModule fscrawler = new SimpleModule(
                "FsCrawler", new Version(2, 0, 0, null, "fr.pilato.elasticsearch.crawler", "fscrawler"));
        fscrawler.addSerializer(new TimeValueSerializer());
        fscrawler.addDeserializer(TimeValue.class, new TimeValueDeserializer());
        fscrawler.addSerializer(new PercentageSerializer());
        fscrawler.addDeserializer(Percentage.class, new PercentageDeserializer());
        fscrawler.addSerializer(new ByteSizeValueSerializer());
        fscrawler.addDeserializer(ByteSizeValue.class, new ByteSizeValueDeserializer());

        // Jackson 3 mappers are immutable: they are configured through their builder.
        // The java.time support is now built into jackson-databind, so no JavaTimeModule registration is needed.
        mapper = JsonMapper.builder()
                .addModule(fscrawler)
                .propertyNamingStrategy(PropertyNamingStrategies.SNAKE_CASE)
                .disable(DateTimeFeature.WRITE_DATE_TIMESTAMPS_AS_NANOSECONDS)
                .disable(DateTimeFeature.WRITE_DATES_AS_TIMESTAMPS)
                .disable(DateTimeFeature.READ_DATE_TIMESTAMPS_AS_NANOSECONDS)
                .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
                // Jackson 2's setDefaultPropertyInclusion(NON_EMPTY) applied to both value and content
                // (map/collection entries); reproduce it so empty map values are omitted too.
                .changeDefaultPropertyInclusion(incl -> incl.withValueInclusion(JsonInclude.Include.NON_EMPTY)
                        .withContentInclusion(JsonInclude.Include.NON_EMPTY))
                .build();

        prettyMapper = JsonMapper.builder()
                .addModule(fscrawler)
                .enable(SerializationFeature.INDENT_OUTPUT)
                .propertyNamingStrategy(PropertyNamingStrategies.SNAKE_CASE)
                .disable(DateTimeFeature.WRITE_DATE_TIMESTAMPS_AS_NANOSECONDS)
                .disable(DateTimeFeature.WRITE_DATES_AS_TIMESTAMPS)
                .disable(DateTimeFeature.READ_DATE_TIMESTAMPS_AS_NANOSECONDS)
                .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
                // Jackson 2's setDefaultPropertyInclusion(NON_EMPTY) applied to both value and content
                // (map/collection entries); reproduce it so empty map values are omitted too.
                .changeDefaultPropertyInclusion(incl -> incl.withValueInclusion(JsonInclude.Include.NON_EMPTY)
                        .withContentInclusion(JsonInclude.Include.NON_EMPTY))
                .build();

        ymlMapper = YAMLMapper.builder()
                .addModule(fscrawler)
                .enable(SerializationFeature.INDENT_OUTPUT)
                .propertyNamingStrategy(PropertyNamingStrategies.SNAKE_CASE)
                .disable(DateTimeFeature.WRITE_DATE_TIMESTAMPS_AS_NANOSECONDS)
                .disable(DateTimeFeature.WRITE_DATES_AS_TIMESTAMPS)
                .disable(DateTimeFeature.READ_DATE_TIMESTAMPS_AS_NANOSECONDS)
                .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
                // Jackson 2's setDefaultPropertyInclusion(NON_EMPTY) applied to both value and content
                // (map/collection entries); reproduce it so empty map values are omitted too.
                .changeDefaultPropertyInclusion(incl -> incl.withValueInclusion(JsonInclude.Include.NON_EMPTY)
                        .withContentInclusion(JsonInclude.Include.NON_EMPTY))
                .build();
    }

    public static String serialize(Object object) {
        try {
            return mapper.writeValueAsString(object);
        } catch (JacksonException e) {
            throw new RuntimeException(e);
        }
    }

    public static <T> T deserialize(String json, Class<T> clazz) {
        try {
            return mapper.readValue(json, clazz);
        } catch (JacksonException e) {
            throw new RuntimeException(e);
        }
    }

    public static Map<String, Object> asMap(InputStream stream) {
        try {
            return mapper.readValue(stream, new TypeReference<>() {});
        } catch (JacksonException e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * Parse a JSON Document using JSON Path and return a DocumentContext
     *
     * @param json json to parse
     * @return an Object which can be used as an input for {@link com.jayway.jsonpath.DocumentContext#read(String,
     *     Predicate...)}
     */
    public static DocumentContext parseJsonAsDocumentContext(String json) {
        return JsonPath.using(configuration).parse(json);
    }

    /**
     * Parse a JSON Document using JSON Path and return a DocumentContext
     *
     * @param jsonStream json stream to parse
     * @return an Object which can be used as an input for {@link com.jayway.jsonpath.DocumentContext#read(String,
     *     Predicate...)}
     */
    public static DocumentContext parseJsonAsDocumentContext(InputStream jsonStream) {
        return JsonPath.using(configuration).parse(jsonStream);
    }
}
