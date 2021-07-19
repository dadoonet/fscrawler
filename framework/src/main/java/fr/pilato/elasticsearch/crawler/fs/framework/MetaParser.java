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
import com.fasterxml.jackson.core.Version;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.module.SimpleModule;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.jayway.jsonpath.Configuration;
import com.jayway.jsonpath.Option;
import com.jayway.jsonpath.spi.json.JacksonJsonProvider;
import com.jayway.jsonpath.spi.json.JsonProvider;
import com.jayway.jsonpath.spi.mapper.JacksonMappingProvider;
import com.jayway.jsonpath.spi.mapper.MappingProvider;

import java.util.EnumSet;
import java.util.Set;

/**
 * Helper to parse from and to Json
 */
public class MetaParser {

    public static final ObjectMapper prettyMapper;
    public static final ObjectMapper mapper;
    public static final ObjectMapper ymlMapper;

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

        // Initialize Json Path
        Configuration.setDefaults(new Configuration.Defaults() {

            private final JsonProvider jsonProvider = new JacksonJsonProvider(mapper);
            private final MappingProvider mappingProvider = new JacksonMappingProvider(mapper);

            @Override
            public JsonProvider jsonProvider() {
                return jsonProvider;
            }

            @Override
            public MappingProvider mappingProvider() {
                return mappingProvider;
            }

            @Override
            public Set<Option> options() {
                return EnumSet.noneOf(Option.class);
            }
        });
    }
}
