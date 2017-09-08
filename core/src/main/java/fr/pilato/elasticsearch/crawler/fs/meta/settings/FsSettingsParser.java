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

package fr.pilato.elasticsearch.crawler.fs.meta.settings;

import com.fasterxml.jackson.core.JsonProcessingException;
import fr.pilato.elasticsearch.crawler.fs.meta.MetaParser;

import java.io.IOException;

public class FsSettingsParser extends MetaParser {

    public static String toJson(FsSettings settings) throws JsonProcessingException {
        return prettyMapper.writeValueAsString(settings);
    }

    public static FsSettings fromJson(String json) throws IOException {
        return prettyMapper.readValue(json, FsSettings.class);
    }
}
