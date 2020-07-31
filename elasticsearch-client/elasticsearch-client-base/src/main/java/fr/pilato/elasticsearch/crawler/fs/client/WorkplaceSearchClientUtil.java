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

import fr.pilato.elasticsearch.crawler.fs.settings.FsSettings;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.nio.file.Path;
import java.util.Objects;

public abstract class WorkplaceSearchClientUtil {

    protected static final Logger logger = LogManager.getLogger(WorkplaceSearchClientUtil.class);

    private WorkplaceSearchClientUtil() {

    }

    /**
     * Try to find a client version in the classpath
     * @param config Path to FSCrawler configuration files (elasticsearch templates)
     * @param settings FSCrawler settings. Can not be null.
     * @return A Client instance
     */
    public static WorkplaceSearchClient getInstance(Path config, FsSettings settings) {

        Objects.requireNonNull(settings, "settings can not be null");

        for (int i = 7; i >= 1; i--) {
            logger.debug("Trying to find a client version {}", i);

            try {
                return getInstance(config, settings, i);
            } catch (ClassNotFoundException ignored) {
            }
        }

        return null;
    }

    /**
     * Try to find a client version in the classpath
     * @param config Path to FSCrawler configuration files (elasticsearch templates)
     * @param settings FSCrawler settings. Can not be null.
     * @param version Version to load
     * @return A Client instance
     */
    public static WorkplaceSearchClient getInstance(Path config, FsSettings settings, int version) throws ClassNotFoundException {
        Objects.requireNonNull(settings, "settings can not be null");

        if (settings.getWorkplaceSearch() == null) {
            return null;
        }

        Class<WorkplaceSearchClient> clazz = null;
        try {
            clazz = findClass(version);
        } catch (ClassNotFoundException e) {
            logger.trace("WorkplaceSearchClient class not found for version {} in the classpath. Skipping...", version);
        }

        if (clazz == null) {
            throw new ClassNotFoundException("Can not find any WorkplaceSearchClient in the classpath. " +
                    "Did you forget to add the elasticsearch client library?");
        }

        logger.trace("Found [{}] class as the elasticsearch client implementation.", clazz.getName());
        try {
            Constructor<? extends WorkplaceSearchClient> constructor = clazz.getConstructor(Path.class, FsSettings.class);
            return constructor.newInstance(config, settings);
        } catch (NoSuchMethodException e) {
            throw new IllegalArgumentException("Class " + clazz.getName() + " does not have the expected ctor (Path, FsSettings).", e);
        } catch (IllegalAccessException|InstantiationException| InvocationTargetException e) {
            throw new IllegalArgumentException("Can not create an instance of " + clazz.getName(), e);
        }
    }

    /**
     * Try to load an WorkplaceSearchClient class
     * @param version Version number to use. It's only the major part of the version. Like 5 or 6.
     * @return The Client class
     */
    private static Class<WorkplaceSearchClient> findClass(int version) throws ClassNotFoundException {
        String className = "fr.pilato.elasticsearch.crawler.fs.client.v" + version + ".WorkplaceSearchClientV" + version;
        logger.trace("Trying to find a class named [{}]", className);
        Class<?> aClass = Class.forName(className);
        boolean isImplementingInterface = WorkplaceSearchClient.class.isAssignableFrom(aClass);
        if (!isImplementingInterface) {
            throw new IllegalArgumentException("Class " + className + " does not implement " + WorkplaceSearchClient.class.getName() + " interface");
        }

        return (Class<WorkplaceSearchClient>) aClass;
    }
}
