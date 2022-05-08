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

package fr.pilato.elasticsearch.crawler.fs.test.integration;

import fr.pilato.elasticsearch.crawler.fs.rest.RestJsonProvider;
import jakarta.ws.rs.client.Client;
import jakarta.ws.rs.client.ClientBuilder;
import jakarta.ws.rs.client.Entity;
import jakarta.ws.rs.client.Invocation;
import jakarta.ws.rs.client.WebTarget;
import jakarta.ws.rs.core.MediaType;
import org.glassfish.jersey.jackson.JacksonFeature;
import org.glassfish.jersey.media.multipart.FormDataMultiPart;
import org.glassfish.jersey.media.multipart.MultiPartFeature;
import org.junit.AfterClass;
import org.junit.BeforeClass;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Map;

public abstract class AbstractRestITCase extends AbstractITCase {

    protected volatile static WebTarget target;
    protected volatile static Client client;

    public static <T> T get(String path, Class<T> clazz) {
        if (staticLogger.isDebugEnabled()) {
            String response = target.path(path).request().get(String.class);
            staticLogger.debug("Rest response: {}", response);
        }
        return target.path(path).request().get(clazz);
    }

    public static <T> T post(WebTarget target, String path, FormDataMultiPart mp, Class<T> clazz, Map<String, Object> params) {
        WebTarget targetPath = target.path(path);
        // TODO check this as it does not seem to produce anything
        params.forEach(targetPath::queryParam);

        return targetPath.request(MediaType.MULTIPART_FORM_DATA)
                .accept(MediaType.APPLICATION_JSON)
                .post(Entity.entity(mp, mp.getMediaType()), clazz);
    }

    public static <T> T put(WebTarget target, String path, FormDataMultiPart mp, Class<T> clazz, Map<String, Object> params) {
        WebTarget targetPath = target.path(path);
        // TODO check this as it does not seem to produce anything
        params.forEach(targetPath::queryParam);

        return targetPath.request(MediaType.MULTIPART_FORM_DATA)
                .accept(MediaType.APPLICATION_JSON)
                .put(Entity.entity(mp, mp.getMediaType()), clazz);
    }

    public static <T> T delete(WebTarget target, String path, Class<T> clazz, Map<String, Object> params) {
        WebTarget targetPath = target.path(path);
        Invocation.Builder builder = targetPath.request();
        // Sadly headers by default only support ISO-8859-1 and not UTF-8: https://www.jmix.io/cuba-blog/utf-8-in-http-headers/
        // So we need to hack around this and support rfc6266 https://datatracker.ietf.org/doc/html/rfc6266#section-5
        params.forEach((k, v) -> {
            builder.header(k, v);
            builder.header(k + "*", "UTF-8''" + URLEncoder.encode((String) v, StandardCharsets.UTF_8));
        });

        // params.forEach(builder::property);
        return builder.delete(clazz);
    }

    @BeforeClass
    public static void startRestClient() {
        // create the client
        client = ClientBuilder.newBuilder()
                .register(MultiPartFeature.class)
                .register(RestJsonProvider.class)
                .register(JacksonFeature.class)
                .build();

        target = client.target("http://127.0.0.1:" + testRestPort + "/fscrawler");
    }

    @AfterClass
    public static void stopRestClient() {
        if (client != null) {
            client.close();
            client = null;
        }
    }
}
