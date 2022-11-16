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
package fr.pilato.elasticsearch.crawler.fs.test.framework;

import org.apache.commons.io.FileUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Path;

class FsCrawlerUtilForTests {
    private static final Logger logger = LogManager.getLogger(FsCrawlerUtilForTests.class);

    private static final String CLASSPATH_RESOURCES_ROOT = "/fr/pilato/elasticsearch/crawler/fs/_default/";
    private static final String[] MAPPING_RESOURCES = {
            "6/_settings.json", "6/_settings_folder.json",
            "7/_settings.json", "7/_settings_folder.json", "7/_wpsearch_settings.json",
            "8/_settings.json", "8/_settings_folder.json", "8/_wpsearch_settings.json"
    };

    private FsCrawlerUtilForTests() {

    }

    /**
     * Copy default resources files which are available as project resources under
     * fr.pilato.elasticsearch.crawler.fs._default package to a given configuration path
     * under a _default subdirectory.
     * @param configPath The config path which is by default .fscrawler
     * @throws IOException If copying does not work
     */
    static void copyDefaultResources(Path configPath) throws IOException {
        Path targetResourceDir = configPath.resolve("_default");

        for (String filename : MAPPING_RESOURCES) {
            Path target = targetResourceDir.resolve(filename);
            if (target.toFile().exists()) {
                logger.debug("Mapping [{}] already exists", filename);
            } else {
                logger.debug("Copying [{}]...", filename);
                copyResourceFile(CLASSPATH_RESOURCES_ROOT + filename, target);
            }
        }
    }

    /**
     * Copy a single resource file from the classpath or from a JAR.
     * @param target The target
     * @throws IOException If copying does not work
     */
    private static void copyResourceFile(String source, Path target) throws IOException {
        InputStream resource = FsCrawlerUtilForTests.class.getResourceAsStream(source);
        FileUtils.copyInputStreamToFile(resource, target.toFile());
    }
}
