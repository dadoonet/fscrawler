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
package fr.pilato.elasticsearch.crawler.plugins.fs.http;

import com.carrotsearch.randomizedtesting.annotations.ThreadLeakFilters;
import fr.pilato.elasticsearch.crawler.fs.test.framework.AbstractFSCrawlerTestCase;
import fr.pilato.elasticsearch.crawler.plugins.FsCrawlerExtensionFsProvider;
import org.apache.commons.io.IOUtils;
import org.junit.*;
import org.testcontainers.containers.NginxContainer;
import org.testcontainers.containers.wait.strategy.HttpWaitStrategy;
import org.testcontainers.utility.MountableFile;

import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.*;


@ThreadLeakFilters(filters = {
        AbstractFSCrawlerTestCase.TestContainerThreadFilter.class,
        AbstractFSCrawlerTestCase.JNACleanerThreadFilter.class
})
public class FsHttpPluginIT extends AbstractFSCrawlerTestCase {

    private final static String text = "Hello Foo world!";

    private void createFile(Path root, String objectName, String object) throws IOException {
        staticLogger.info("Create fake content [{}]; [{}]", objectName, object);
        Path file = root.resolve(objectName);
        Files.writeString(file, object);
    }

    @Test
    public void testReadFileFromNginx() throws Exception {
        staticLogger.info("Starting Nginx from {}", rootTmpDir);
        Path nginxRoot = rootTmpDir.resolve("nginx-root");
        Files.createDirectory(nginxRoot);
        Files.writeString(nginxRoot.resolve("index.html"), "<html><body>Hello World!</body></html>");
        createFile(nginxRoot, "foo.txt", text);
        createFile(nginxRoot, "bar.txt", "This one should be ignored.");

        try (NginxContainer<?> container = new NginxContainer<>("nginx")) {
            container.waitingFor(new HttpWaitStrategy());
            container.start();
            container.copyFileToContainer(MountableFile.forHostPath(nginxRoot), "/usr/share/nginx/html");
            URL url = container.getBaseUrl("http", 80);
            staticLogger.info("Nginx started on {}.", url);

            logger.info("Starting Test");
            try (FsCrawlerExtensionFsProvider provider = new FsHttpPlugin.FsCrawlerExtensionFsProviderHttp()) {
                provider.settings("{\n" +
                        "  \"type\": \"http\",\n" +
                        "  \"http\": {\n" +
                        "    \"url\": \"" + url + "/foo.txt\"\n" +
                        "  }\n" +
                        "}");
                provider.start();
                InputStream inputStream = provider.readFile();
                String object = IOUtils.toString(inputStream, StandardCharsets.UTF_8);
                assertThat(object, is(text));
                assertThat(provider.getFilename(), is("foo.txt"));
                assertThat(provider.getFilesize(), is(16L));
            }
        }
    }

    @Test
    public void testReadTxtFileFromElasticCo() throws Exception {
        logger.info("Starting Test");
        try (FsCrawlerExtensionFsProvider provider = new FsHttpPlugin.FsCrawlerExtensionFsProviderHttp()) {
            provider.settings("{\n" +
                    "  \"type\": \"http\",\n" +
                    "  \"http\": {\n" +
                    "    \"url\": \"https://www.elastic.co/robots.txt\"\n" +
                    "  }\n" +
                    "}");
            provider.start();
            InputStream inputStream = provider.readFile();
            String object = IOUtils.toString(inputStream, StandardCharsets.UTF_8);
            assertThat(object, containsString("Sitemap"));
            assertThat(provider.getFilename(), is("robots.txt"));
            assertThat(provider.getFilesize(), greaterThan(100L));
        }
    }
}
