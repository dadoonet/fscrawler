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

package fr.pilato.elasticsearch.crawler.fs.settings;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.Base64;
import java.util.Objects;

/**
 * This class represents a ServerUrl which is basically just a String which
 * can be either an url or a cloud id.
 * This is used in the Elasticsearch.Node class.
 */
public class ServerUrl {

    protected static final Logger logger = LogManager.getLogger(ServerUrl.class);

    private String url;
    private String cloudId;

    public ServerUrl() {

    }

    public ServerUrl(String urlOrCloudId) {
        // We check if the String starts with https:// or http://
        // In which case this is a URL, otherwise it's a cloud id
        String asLowerCase = urlOrCloudId.toLowerCase();
        if (asLowerCase.startsWith("http://") || asLowerCase.startsWith("https://")) {
            this.url = urlOrCloudId;
        } else {
            this.cloudId = urlOrCloudId;
        }
    }

    /**
     * Decode a cloudId to a Node representation. This helps when using
     * official elasticsearch as a service: <a href="https://cloud.elastic.co">https://cloud.elastic.co</a>
     * The cloudId can be found from the cloud console.
     *
     * @param cloudId The cloud ID to decode.
     * @return A Node running on <a href="https://address">https://address</a>
     */
    public static String decodeCloudId(String cloudId) {
        // 1. Ignore anything before `:`.
        String id = cloudId.substring(cloudId.indexOf(':') + 1);

        // 2. base64 decode
        String decoded = new String(Base64.getDecoder().decode(id));

        // 3. separate based on `$`
        String[] words = decoded.split("\\$");

        // 4. form the URLs
        return "https://" + words[1] + "." + words[0];
    }

    /**
     * Get the server URL: Scheme://host:port/endpoint
     * @return the server URL
     */
    public String getUrl() {
        return url;
    }

    public void setUrl(String url) {
        this.url = url;
    }

    public String getCloudId() {
        return cloudId;
    }

    public void setCloudId(String cloudId) {
        this.cloudId = cloudId;
    }

    /**
     * Returns either the original url or the decoded one based on the cloud id
     * if it was provided.
     * @return a url (decoded from a cloud id or not)
     */
    public String decodedUrl() {
        if (cloudId != null) {
            return decodeCloudId(cloudId);
        }
        return url;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        ServerUrl serverUrl = (ServerUrl) o;
        return Objects.equals(url, serverUrl.url) &&
                Objects.equals(cloudId, serverUrl.cloudId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(url);
    }

    @Override
    public String toString() {
        return url;
    }
}
