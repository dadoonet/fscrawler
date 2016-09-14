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

package fr.pilato.elasticsearch.crawler.fs.meta;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.core.Version;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.PropertyNamingStrategy;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.module.SimpleModule;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import fr.pilato.elasticsearch.crawler.fs.meta.settings.Percentage;
import fr.pilato.elasticsearch.crawler.fs.meta.settings.PercentageDeserializer;
import fr.pilato.elasticsearch.crawler.fs.meta.settings.PercentageSerializer;
import fr.pilato.elasticsearch.crawler.fs.meta.settings.TimeValue;
import fr.pilato.elasticsearch.crawler.fs.meta.settings.TimeValueDeserializer;
import fr.pilato.elasticsearch.crawler.fs.meta.settings.TimeValueSerializer;

/**
 * Helper to parse from and to Json
 */
public class MetaParser {

    public static final ObjectMapper prettyMapper;
    public static final ObjectMapper mapper;

    static {
        SimpleModule fscrawler = new SimpleModule("FsCrawler", new Version(2, 0, 0, null,
                "fr.pilato.elasticsearch.crawler", "fscrawler"));
        fscrawler.addSerializer(new TimeValueSerializer());
        fscrawler.addDeserializer(TimeValue.class, new TimeValueDeserializer());
        fscrawler.addSerializer(new PercentageSerializer());
        fscrawler.addDeserializer(Percentage.class, new PercentageDeserializer());

        mapper = new ObjectMapper();
        mapper.registerModule(new JavaTimeModule());
        mapper.registerModule(fscrawler);
        mapper.setPropertyNamingStrategy(PropertyNamingStrategy.CAMEL_CASE_TO_LOWER_CASE_WITH_UNDERSCORES);
        mapper.configure(SerializationFeature.WRITE_DATE_TIMESTAMPS_AS_NANOSECONDS, false);
        mapper.configure(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS, false);
        mapper.configure(DeserializationFeature.READ_DATE_TIMESTAMPS_AS_NANOSECONDS, false);
        mapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
        mapper.setSerializationInclusion(JsonInclude.Include.NON_EMPTY);


        prettyMapper = new ObjectMapper();
        prettyMapper.registerModule(new JavaTimeModule());
        prettyMapper.registerModule(fscrawler);
        prettyMapper.configure(SerializationFeature.INDENT_OUTPUT, true);
        prettyMapper.setPropertyNamingStrategy(PropertyNamingStrategy.CAMEL_CASE_TO_LOWER_CASE_WITH_UNDERSCORES);
        prettyMapper.configure(SerializationFeature.WRITE_DATE_TIMESTAMPS_AS_NANOSECONDS, false);
        prettyMapper.configure(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS, false);
        prettyMapper.configure(DeserializationFeature.READ_DATE_TIMESTAMPS_AS_NANOSECONDS, false);
        prettyMapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
        prettyMapper.setSerializationInclusion(JsonInclude.Include.NON_EMPTY);
    }

}
