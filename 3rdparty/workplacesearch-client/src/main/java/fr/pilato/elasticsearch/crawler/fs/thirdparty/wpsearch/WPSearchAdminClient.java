/*
 * Licensed to David Pilato under one or more contributor
 * license agreements. See the NOTICE file distributed with
 * this work for additional information regarding copyright
 * ownership. Elasticsearch licenses this file to you under
 * the Apache License, Version 2.0 (the "License"); you may
 * not use this file except in compliance with the License.
 * You may obtain a copy of the License at
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

package fr.pilato.elasticsearch.crawler.fs.thirdparty.wpsearch;

import com.fasterxml.jackson.databind.json.JsonMapper;
import jakarta.ws.rs.HttpMethod;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.Closeable;
import java.io.IOException;
import java.net.URL;
import java.util.Map;

import static fr.pilato.elasticsearch.crawler.fs.thirdparty.wpsearch.WPSearchClient.DEFAULT_HOST;

/**
 * This class is useless at the moment as we don't have an Admin API yet
 * TODO: implement when we have an Admin API for Workplace Search
 */
public class WPSearchAdminClient implements Closeable {

    private static final Logger logger = LogManager.getLogger(WPSearchAdminClient.class);
    private static final String DEFAULT_USERNAME = "enterprise_search";
    private static final String DEFAULT_PASSWORD = "changeme";

    private String host = DEFAULT_HOST;
    private String username = DEFAULT_USERNAME;
    private String password = DEFAULT_PASSWORD;

    /**
     * Define a specific host. Defaults to "http://localhost:3002"
     * @param host  If we need to change the default host
     * @return the current instance
     */
    public WPSearchAdminClient withHost(String host) {
        this.host = host;
        return this;
    }

    /**
     * Define the username. Defaults to "enterprise_search"
     * @param username  If we need to change the default username
     * @return the current instance
     */
    public WPSearchAdminClient withUsername(String username) {
        this.username = username;
        return this;
    }

    /**
     * Define the password. Defaults to "changeme"
     * @param password  If we need to change the default password
     * @return the current instance
     */
    public WPSearchAdminClient withPassword(String password) {
        this.password = password;
        return this;
    }

    public void start() throws Exception {
        // Create the client
        login(username, password);
    }

    @Override
    public void close() {
        // Close the client
    }

    private void checkStarted() {
        // This method is empty as we are waiting for an Admin API for Workplace Search
    }

    public Map<String, Object> createCustomSource(String sourceName) throws Exception {
        checkStarted();

        String response = callApi(HttpMethod.POST, "/ws/org/sources/form_create",
                "{service_type: \"custom\", name: \"" + sourceName + "\"}");
        // TODO: remove when we have an Admin API for Workplace Search
        response = "{" +
                "\"id\":\"FAKE_ID\"," +
                "\"acces_token\":\"FAKE_TOKEN\"," +
                "\"key\":\"FAKE_KEY\"" +
                "}";
        JsonMapper mapper = JsonMapper.builder().build();
        Map<String, Object> map = mapper.readValue(response, Map.class);

        logger.debug("Source [{}] created. id={}, acces_token={}, key={}",
                sourceName, map.get("id"), map.get("accessToken"), map.get("key"));

        return map;
    }

    public void removeCustomSource(String id) throws Exception {
        checkStarted();

        // Delete the source
        callApi(HttpMethod.DELETE, "/ws/org/sources/" + id, null);
    }

    public void login(String username, String password) {
        logger.debug("login to Workplace Search as user {}", username);
    }

    private String callApi(String method, String url, String data)
            throws IOException {
        logger.debug("Calling {}", url);

        logger.debug("Faking a {} call to {}", method, new URL(host + url));

        // Create a web request with
//        url: host + url
//        httpMethod: method;
//        requestBody: data
//        additionalHeader: Content-Type: application/json;charset=UTF-8
//        additionalHeader: Cookie: session.getName()=session.getValue()
//        additionalHeader: x-csrf-token: csrfToken
//        additionalHeader: Accept: application/json, text/plain, */*
        return null;
    }
}
