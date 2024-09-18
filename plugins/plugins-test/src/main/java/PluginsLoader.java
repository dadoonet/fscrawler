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
import com.jayway.jsonpath.DocumentContext;
import fr.pilato.elasticsearch.crawler.plugins.FsCrawlerExtensionFsProvider;
import fr.pilato.elasticsearch.crawler.plugins.FsCrawlerPluginsManager;
import org.apache.commons.io.IOUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.InputStream;
import java.net.URL;
import java.nio.charset.StandardCharsets;

import static fr.pilato.elasticsearch.crawler.fs.framework.JsonUtil.parseJsonAsDocumentContext;

public class PluginsLoader {
    private static final Logger logger = LogManager.getLogger(PluginsLoader.class);

    public static void main(String[] args) throws Exception {
        FsCrawlerPluginsManager pluginsManager = new FsCrawlerPluginsManager();
        pluginsManager.loadPlugins();
        pluginsManager.startPlugins();

        {
            URL url = PluginsLoader.class.getResource("/test.txt");
            String path = url.getPath();
            String settings = "{\n" +
                    "  \"type\": \"local\",\n" +
                    "  \"local\": {\n" +
                    "    \"url\": \""+ path +"\"\n" +
                    "  }\n" +
                    "}";

            DocumentContext document = parseJsonAsDocumentContext(settings);
            String type = document.read("$.type");
            FsCrawlerExtensionFsProvider fsProvider = pluginsManager.findFsProvider(type);
            fsProvider.settings(settings);
            fsProvider.start();
            InputStream inputStream = fsProvider.readFile();
            String string = IOUtils.toString(inputStream, StandardCharsets.UTF_8);
            logger.info(string);
            fsProvider.stop();
        }

        {
            String settings = "{\n" +
                    "  \"type\": \"s3\",\n" +
                    "  \"s3\": {\n" +
                    "    \"url\": \"/foo/bar.txt\"\n" +
                    "  }\n" +
                    "}";

            DocumentContext document = parseJsonAsDocumentContext(settings);
            String type = document.read("$.type");
            FsCrawlerExtensionFsProvider fsProvider = pluginsManager.findFsProvider(type);
            fsProvider.settings(settings);
            fsProvider.start();
            InputStream inputStream = fsProvider.readFile();
            String string = IOUtils.toString(inputStream, StandardCharsets.UTF_8);
            logger.info(string);
            fsProvider.stop();
        }
    }
}
