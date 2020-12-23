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

package fr.pilato.elasticsearch.crawler.fs.beans;

import java.io.IOException;
import java.util.Map;

import static fr.pilato.elasticsearch.crawler.fs.framework.MetaParser.prettyMapper;

public class DocParser {

    public static String toJson(Doc doc) {
        try {
            return prettyMapper.writeValueAsString(doc);
        } catch (IOException e) {
            // TODO Fix that code. We should log here and return null.
            throw new RuntimeException(e);
        }
    }

    public static Doc fromJson(String json) throws IOException {
        return prettyMapper.readValue(json, Doc.class);
    }

    public static Map<String, Object> asMap(String json) throws IOException {
        //noinspection unchecked
        return (Map<String, Object>) prettyMapper.readValue(json, Map.class);
    }
}
