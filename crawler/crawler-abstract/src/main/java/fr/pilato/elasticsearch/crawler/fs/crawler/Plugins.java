/*
 * Licensed to Elasticsearch under one or more contributor
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

package fr.pilato.elasticsearch.crawler.fs.crawler;

import fr.pilato.elasticsearch.crawler.fs.client.ElasticsearchClientManager;
import fr.pilato.elasticsearch.crawler.fs.settings.FsSettings;
import fr.pilato.elasticsearch.crawler.fs.settings.Server;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.reflections.Reflections;
import org.reflections.scanners.SubTypesScanner;
import org.reflections.scanners.TypeAnnotationsScanner;
import org.reflections.util.ClasspathHelper;
import org.reflections.util.ConfigurationBuilder;
import org.reflections.util.FilterBuilder;

import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

import static org.reflections.ReflectionUtils.getConstructors;
import static org.reflections.ReflectionUtils.withParameters;

public class Plugins {

    private static final Logger logger = LogManager.getLogger(Plugins.class);

    // Register Crawler plugins
    private static Map<String, Class<? extends FsParserAbstract>> plugins = new HashMap<>();

    private static Reflections reflections = new Reflections(new ConfigurationBuilder()
            .filterInputsBy(new FilterBuilder().includePackage("fr.pilato.elasticsearch.crawler.fs"))
            .setUrls(ClasspathHelper.forPackage("fr.pilato.elasticsearch.crawler.fs"))
            .setScanners(new SubTypesScanner(), new TypeAnnotationsScanner()));

    private static void addPlugin(String name, Class<? extends FsParserAbstract> clazz) {
        logger.info("Registering plugin [{}]", name);
        plugins.put(name, clazz);
    }

    /**
     * Create a Parser instance from the FSCrawler settings using reflexion
     * @param fsSettings FSCrawler settings
     * @param configDir Configuration directory
     * @param esClientManager The Elasticsearch Client Manager
     * @param loop How many time we want to run the crawler? -1 for infinite
     * @return a Parser instance if the protocol is supported and the plugin is available
     */
    public static FsParserAbstract createParser(FsSettings fsSettings, Path configDir, ElasticsearchClientManager esClientManager,
                                                int loop) {
        String pluginName = (fsSettings.getServer() == null || fsSettings.getServer().getProtocol() == null) ? Server.PROTOCOL.LOCAL : fsSettings.getServer().getProtocol();
        Class<? extends FsParserAbstract> pluginClass = plugins.get(pluginName);

        // Non supported protocol
        if (pluginClass == null) {
            throw new RuntimeException(fsSettings.getServer().getProtocol() + " is not supported yet.");
        }

        // Create an instance
        try {
            Set<Constructor> constructors = getConstructors(pluginClass, withParameters(FsSettings.class, Path.class,
                    ElasticsearchClientManager.class, Integer.class));
            Constructor<? extends FsParserAbstract> constructor = constructors.iterator().next();
            return constructor.newInstance(fsSettings, configDir, esClientManager, loop);
        } catch (IllegalAccessException | InstantiationException | InvocationTargetException e) {
            throw new RuntimeException("Can not create an instance of " + pluginClass, e);
        }
    }

    /**
     * Register all plugins available on the classpath
     */
    public static void registerPlugins() {
        Set<Class<? extends FsParserAbstract>> plugins =
                reflections.getSubTypesOf(FsParserAbstract.class);
        for (Class<? extends FsParserAbstract> aClass : plugins) {
            FsParserPlugin annotation = aClass.getAnnotation(FsParserPlugin.class);
            if (annotation == null) {
                throw new RuntimeException("Class " + aClass.getName() + " is missing @FsParserPlugin annotation.");
            }
            addPlugin(annotation.name(), aClass);
        }
    }

    /**
     * Get the list of currently loaded plugins
     * @return list of currently loaded plugins
     */
    public static Map<String, Class<? extends FsParserAbstract>> getPlugins() {
        return plugins;
    }
}
