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
import java.util.Properties;

import static fr.pilato.elasticsearch.crawler.fs.framework.FsCrawlerUtil.readPropertiesFromClassLoader;

public abstract class ElasticsearchClientUtil {

    protected static final Logger logger = LogManager.getLogger(ElasticsearchClientUtil.class);

    /**
     * Decode a cloudId to a Node representation. This helps when using
     * official elasticsearch as a service: https://cloud.elastic.co
     * The cloudId can be found from the cloud console.
     * @param cloudId The cloud ID to decode.
     * @return A Node running on https://address:443
     */
    public static Node decodeCloudId(String cloudId) {
        // 1. Ignore anything before `:`.
        String id = cloudId.substring(cloudId.indexOf(':')+1);

        // 2. base64 decode
        String decoded = new String(Base64.getDecoder().decode(id));

        // 3. separate based on `$`
        String[] words = decoded.split("\\$");

        // 4. form the URLs
        return Node.builder().setHost(words[1] + "." + words[0]).setPort(443).setScheme(Node.Scheme.HTTPS).build();
    }

    private final static String ES_CLIENT_PROPERTIES = "fr/pilato/elasticsearch/crawler/fs/client/fscrawler-client.properties";
    private final static String ES_CLIENT_CLASS_PROPERTY = "es-client.class";

    /**
     * Reads the classpath to get the right instance to be injected as the Client
     * implementation
     * @param config Path to FSCrawler configuration files (elasticsearch templates)
     * @param settings FSCrawler settings. Can not be null.
     * @return A Client instance
     */
    public static ElasticsearchClient getInstance(Path config, FsSettings settings) {
        return getInstance(config, settings, ES_CLIENT_PROPERTIES);
    }

    /**
     * Reads the classpath to get the right instance to be injected as the Client
     * implementation
     * @param config Path to FSCrawler configuration files (elasticsearch templates)
     * @param settings FSCrawler settings. Can not be null.
     * @param propertyFile Property file name. For test purpose only.
     * @return A Client instance
     */
    static ElasticsearchClient getInstance(Path config, FsSettings settings, String propertyFile) {
        Objects.requireNonNull(settings, "settings can not be null");

        String className;

        try {
            Properties properties = readPropertiesFromClassLoader(propertyFile);
            className = properties.getProperty(ES_CLIENT_CLASS_PROPERTY);
        } catch (NullPointerException npe) {
            throw new IllegalArgumentException("Can not find " + propertyFile + " in the classpath. " +
                    "Did you forget to add the elasticsearch client library?");
        }

        if (className == null) {
            throw new IllegalArgumentException("Can not find " + ES_CLIENT_CLASS_PROPERTY + " property in " +
                    propertyFile + " file.");
        }

        try {
            Class<?> aClass = Class.forName(className);
            boolean isImplementingInterface = ElasticsearchClient.class.isAssignableFrom(aClass);
            if (!isImplementingInterface) {
                throw new IllegalArgumentException("Class " + className + " does not implement " + ElasticsearchClient.class.getName() + " interface");
            }

            Class<ElasticsearchClient> clazz = (Class<ElasticsearchClient>) aClass;

            Constructor<? extends ElasticsearchClient> constructor = clazz.getConstructor(Path.class, FsSettings.class);
            return constructor.newInstance(config, settings);
        } catch (ClassNotFoundException e) {
            throw new IllegalArgumentException("Class " + className + " not found.", e);
        } catch (NoSuchMethodException e) {
            throw new IllegalArgumentException("Class " + className + " does not have the expected ctor (Path, FsSettings).", e);
        } catch (IllegalAccessException|InstantiationException| InvocationTargetException e) {
            throw new IllegalArgumentException("Can not create an instance of " + className, e);
        }
    }
}
