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

package fr.pilato.elasticsearch.crawler.fs.client;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.elasticsearch.client.Response;

import java.io.IOException;
import java.io.InputStream;
import java.util.Map;

import static fr.pilato.elasticsearch.crawler.fs.meta.MetaParser.mapper;
import static fr.pilato.elasticsearch.crawler.fs.meta.MetaParser.prettyMapper;

public class JsonUtil {

    public static ObjectMapper getMapper() {
        return getMapper(false);
    }

    public static ObjectMapper getMapper(boolean pretty) {
        if (pretty) {
            return prettyMapper;
        }
        return mapper;
    }

    public static String serialize(Object object) {
        try {
            return getMapper().writeValueAsString(object);
        } catch (JsonProcessingException e) {
            throw new RuntimeException(e);
        }
    }

    public static <T> T deserialize(Response response, Class<T> clazz) {
        try {
            if (response.getEntity() == null) {
                return null;
            }
            return deserialize(response.getEntity().getContent(), clazz);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    public static <T> T deserialize(InputStream stream, Class<T> clazz) {
        try {
            return getMapper().readValue(stream, clazz);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    public static Map<String, Object> asMap(Response response) {
        try {
            if (response.getEntity() == null) {
                return null;
            }
            return asMap(response.getEntity().getContent());
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    public static Map<String, Object> asMap(InputStream stream) {
        try {
            return getMapper().readValue(stream, new TypeReference<Map<String, Object>>(){});
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }
}
