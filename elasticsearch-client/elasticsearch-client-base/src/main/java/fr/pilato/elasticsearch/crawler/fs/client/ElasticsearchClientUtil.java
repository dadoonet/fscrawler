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

import fr.pilato.elasticsearch.crawler.fs.settings.Elasticsearch.Node;
import fr.pilato.elasticsearch.crawler.fs.settings.FsSettings;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.nio.file.Path;
import java.util.Base64;
import java.util.Objects;

public abstract class ElasticsearchClientUtil {

    protected static final Logger logger = LogManager.getLogger(ElasticsearchClientUtil.class);

    /**
     * Decode a cloudId to a Node representation. This helps when using
     * official elasticsearch as a service: https://cloud.elastic.co
     * The cloudId can be found from the cloud console.
     *
     * @param cloudId The cloud ID to decode.
     * @return A Node running on https://address:443
     */
    public static String decodeCloudId(String cloudId) {
        // 1. Ignore anything before `:`.
        String id = cloudId.substring(cloudId.indexOf(':') + 1);

        // 2. base64 decode
        String decoded = new String(Base64.getDecoder().decode(id));

        // 3. separate based on `$`
        String[] words = decoded.split("\\$");

        // 4. form the URLs
        return "https://" + words[1] + "." + words[0] + ":443";
    }

    /**
     * Try to find a client version in the classpath
     * @param config Path to FSCrawler configuration files (elasticsearch templates)
     * @param settings FSCrawler settings. Can not be null.
     * @return A Client instance
     */
    public static ElasticsearchClient getInstance(Path config, FsSettings settings) {

        Objects.requireNonNull(settings, "settings can not be null");

        for (int i = 6; i >= 1; i--) {
            logger.debug("Trying to find a client version {}", i);

            try {
                return getInstance(config, settings, i);
            } catch (ClassNotFoundException ignored) {
            }
        }

        throw new IllegalArgumentException("Can not find any ElasticsearchClient in the classpath. " +
                "Did you forget to add the elasticsearch client library?");
    }

    /**
     * Try to find a client version in the classpath
     * @param config Path to FSCrawler configuration files (elasticsearch templates)
     * @param settings FSCrawler settings. Can not be null.
     * @param version Version to load
     * @return A Client instance
     */
    public static ElasticsearchClient getInstance(Path config, FsSettings settings, int version) throws ClassNotFoundException {
        Objects.requireNonNull(settings, "settings can not be null");

        Class<ElasticsearchClient> clazz = null;
        try {
            clazz = findClass(version);
        } catch (ClassNotFoundException e) {
            logger.trace("ElasticsearchClient class not found for version {} in the classpath. Skipping...", version);
        }

        if (clazz == null) {
            throw new ClassNotFoundException("Can not find any ElasticsearchClient in the classpath. " +
                    "Did you forget to add the elasticsearch client library?");
        }

        logger.trace("Found [{}] class as the elasticsearch client implementation.", clazz.getName());
        try {
            Constructor<? extends ElasticsearchClient> constructor = clazz.getConstructor(Path.class, FsSettings.class);
            return constructor.newInstance(config, settings);
        } catch (NoSuchMethodException e) {
            throw new IllegalArgumentException("Class " + clazz.getName() + " does not have the expected ctor (Path, FsSettings).", e);
        } catch (IllegalAccessException|InstantiationException| InvocationTargetException e) {
            throw new IllegalArgumentException("Can not create an instance of " + clazz.getName(), e);
        }
    }

    /**
     * Try to load an ElasticsearchClient class
     * @param version Version number to use. It's only the major part of the version. Like 5 or 6.
     * @return The Client class
     */
    private static Class<ElasticsearchClient> findClass(int version) throws ClassNotFoundException {
        String className = "fr.pilato.elasticsearch.crawler.fs.client.v" + version + ".ElasticsearchClientV" + version;
        logger.trace("Trying to find a class named [{}]", className);
        Class<?> aClass = Class.forName(className);
        boolean isImplementingInterface = ElasticsearchClient.class.isAssignableFrom(aClass);
        if (!isImplementingInterface) {
            throw new IllegalArgumentException("Class " + className + " does not implement " + ElasticsearchClient.class.getName() + " interface");
        }

        return (Class<ElasticsearchClient>) aClass;
    }
}
