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

import java.util.Locale;
import java.util.Objects;

public class Rest {

    protected static final Logger logger = LogManager.getLogger(Rest.class);

    public enum Scheme {
        HTTP,
        HTTPS;

        public static Scheme parse(String value) {
            return valueOf(value.toUpperCase(Locale.ROOT));
        }

        public String toLowerCase() {
            return this.toString().toLowerCase(Locale.ROOT);
        }
    }

    public static final Rest DEFAULT = new Rest("http://127.0.0.1:8080/fscrawler");

    public Rest() {

    }

    public Rest(String url) {
        this.url = url;
    }

    @Deprecated
    public Rest(Scheme scheme, String host, int port, String endpoint) {
        this.scheme = scheme;
        this.host = host;
        this.port = port;
        this.endpoint = endpoint;
    }

    private String url;

    @Deprecated
    private String endpoint;
    @Deprecated
    private Scheme scheme = Scheme.HTTP;
    @Deprecated
    private String host;
    @Deprecated
    private int port;

    public void setHost(String host) {
        this.host = host;
    }

    public void setPort(int port) {
        this.port = port;
    }

    public void setScheme(Scheme scheme) {
        this.scheme = scheme;
    }

    public void setEndpoint(String endpoint) {
        this.endpoint = endpoint;
    }

    /**
     * Get the server URL: Scheme://host:port/endpoint
     * @return the server URL
     */
    public String getUrl() {
        // If we are using deprecated settings, let's warn the user to move to url param
        if (host != null || endpoint != null) {
            String tmpUrl = scheme.toLowerCase() + "://" + host + ":" + port + "/" + endpoint;
            logger.warn("rest.[scheme, host, port, endpoint] has been deprecated and will be removed in a coming version. " +
                    "Use rest: { \"url\": \"{}\" } instead", tmpUrl);
            return tmpUrl;
        }
        return url;
    }

    public void setUrl(String url) {
        this.url = url;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Rest rest = (Rest) o;
        return url.equals(rest.url);
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
