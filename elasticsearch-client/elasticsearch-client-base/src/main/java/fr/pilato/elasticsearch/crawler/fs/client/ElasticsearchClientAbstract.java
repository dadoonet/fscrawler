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

package fr.pilato.elasticsearch.crawler.fs.client;

import fr.pilato.elasticsearch.crawler.fs.settings.FsSettings;

import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.nio.file.Path;
import java.util.Objects;

public abstract class ElasticsearchClientAbstract implements ElasticsearchClientBase {

    /**
     * Reads the classpath to get the right instance to be injected as the Client
     * implementation
     * @return A Client instance
     */
    public static ElasticsearchClientBase getInstance(Path config, FsSettings settings) {
        Objects.requireNonNull(settings, "settings can not be null");
        // String className = "fr.pilato.elasticsearch.crawler.fs.client.v6.ElasticsearchClientV6";
        String className = "fr.pilato.elasticsearch.crawler.fs.client.v5.ElasticsearchClientV5";
        try {
            Class<?> aClass = Class.forName(className);
            Class<?>[] interfaces = aClass.getInterfaces();
            boolean isImplementingInterface = false;
            for (Class<?> anInterface : interfaces) {
                if (anInterface.equals(ElasticsearchClientBase.class)) {
                    isImplementingInterface = true;
                    break;
                }
            }
            if (!isImplementingInterface) {
                throw new IllegalArgumentException("Class " + className + " does not implement " + ElasticsearchClientBase.class.getName() + " interface");
            }

            Class<ElasticsearchClientBase> clazz = (Class<ElasticsearchClientBase>) aClass;

            Constructor<? extends ElasticsearchClientBase> constructor = clazz.getConstructor(Path.class, FsSettings.class);
            return constructor.newInstance(config, settings);
        } catch (ClassNotFoundException e) {
            throw new IllegalArgumentException("Class " + className + " not found.", e);
        } catch (NoSuchMethodException e) {
            throw new IllegalArgumentException("Class " + className + " does not have the expected ctor (Path, FsSettings).", e);
        } catch (IllegalAccessException|InstantiationException|InvocationTargetException e) {
            throw new IllegalArgumentException("Can not create an instance of " + className, e);
        }
    }

}
