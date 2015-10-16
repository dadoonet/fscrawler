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

import com.fasterxml.jackson.core.Version;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.PropertyNamingStrategy;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.module.SimpleModule;
import fr.pilato.elasticsearch.crawler.fs.meta.settings.*;

/**
 * Helper to parse from and to Json
 */
public class MetaParser {

    protected static final ObjectMapper mapper;

    static {
        mapper = new ObjectMapper();
        SimpleModule fscrawler = new SimpleModule("FsCrawler", new Version(2, 0, 0, null,
                "fr.pilato.elasticsearch.crawler", "fscrawler"));
        fscrawler.addSerializer(new TimeValueSerializer());
        fscrawler.addDeserializer(TimeValue.class, new TimeValueDeserializer());
        fscrawler.addSerializer(new PercentageSerializer());
        fscrawler.addDeserializer(Percentage.class, new PercentageDeserializer());
        mapper.registerModule(fscrawler);
        mapper.configure(SerializationFeature.INDENT_OUTPUT, true);
        mapper.setPropertyNamingStrategy(PropertyNamingStrategy.CAMEL_CASE_TO_LOWER_CASE_WITH_UNDERSCORES);
    }

}
