/*
 * Licensed to David Pilato (the "Author") under one
 * or more contributor license agreements. See the NOTICE file
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
 *
 * Made from 🇫🇷🇪🇺 with ❤️ - 2011-2026
 */
package fr.pilato.elasticsearch.crawler.plugins.fs.s3;

import com.carrotsearch.randomizedtesting.jupiter.DetectThreadLeaks;
import fr.pilato.elasticsearch.crawler.fs.beans.Doc;
import fr.pilato.elasticsearch.crawler.fs.settings.FsSettingsLoader;
import fr.pilato.elasticsearch.crawler.fs.test.framework.AbstractFSCrawlerTestCase;
import fr.pilato.elasticsearch.crawler.fs.test.framework.DisabledIfNoDocker;
import fr.pilato.elasticsearch.crawler.fs.test.framework.JNACleanerThreadFilter;
import fr.pilato.elasticsearch.crawler.fs.test.framework.MinioThreadFilter;
import fr.pilato.elasticsearch.crawler.fs.test.framework.TestContainerThreadFilter;
import fr.pilato.elasticsearch.crawler.fs.test.framework.WindowsSpecificThreadFilter;
import fr.pilato.elasticsearch.crawler.plugins.FsCrawlerExtensionFsProvider;
import io.minio.BucketExistsArgs;
import io.minio.MakeBucketArgs;
import io.minio.MinioClient;
import io.minio.PutObjectArgs;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import org.apache.commons.io.IOUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.MinIOContainer;

@DetectThreadLeaks.ExcludeThreads({
    WindowsSpecificThreadFilter.class,
    TestContainerThreadFilter.class,
    JNACleanerThreadFilter.class,
    MinioThreadFilter.class
})
@DisabledIfNoDocker
class FsS3PluginIT extends AbstractFSCrawlerTestCase {
    private static final Logger logger = LogManager.getLogger();
    private static MinIOContainer container;
    private static String s3Url = "http://localhost:9000";
    private static String s3Username = "minioadmin";
    private static String s3Password = "minioadmin";

    @BeforeEach
    void startMinioContainer() {
        logger.info("Starting Minio");
        container = new MinIOContainer("minio/minio");
        container.start();
        s3Url = container.getS3URL();
        s3Username = container.getUserName();
        s3Password = container.getPassword();
        logger.info("Minio started on {} with username {} and password {}.", s3Url, s3Username, s3Password);
    }

    @AfterEach
    void stopMinioContainer() {
        if (container != null) {
            container.close();
            container = null;
            logger.info("Minio stopped.");
        }
    }

    private static void createBucket(String objectName, String object) {
        logger.info("Create fake bucket [{}]; [{}]", objectName, object);
        try (MinioClient minioClient = MinioClient.builder()
                .endpoint(s3Url)
                .credentials(s3Username, s3Password)
                .build()) {
            if (!minioClient.bucketExists(
                    BucketExistsArgs.builder().bucket("foo").build())) {
                minioClient.makeBucket(MakeBucketArgs.builder().bucket("foo").build());
            }
            minioClient.putObject(
                    PutObjectArgs.builder().contentType("text/plain").bucket("foo").object(objectName).stream(
                                    IOUtils.toInputStream(object, StandardCharsets.UTF_8), (long) object.length(), -1L)
                            .build());
        } catch (Exception e) {
            logger.warn("Could not create bucket [{}]; [{}]: {}", objectName, object, e.getMessage());
        }
    }

    @Test
    void minio() throws Exception {
        String text = "Hello Foo world!";
        createBucket("foo.txt", text);
        createBucket("bar.txt", "This one should be ignored.");

        logger.info("Starting Test");
        try (FsCrawlerExtensionFsProvider provider = new FsS3Plugin.FsCrawlerExtensionFsProviderS3()) {
            provider.start(FsSettingsLoader.load(), """
                    {
                      "type": "s3",
                      "s3": {
                        "url": "%s",
                        "bucket": "foo",
                        "object": "foo.txt",
                        "access_key": "%s",
                        "secret_key": "%s"
                      }
                    }""".formatted(s3Url, s3Username, s3Password));
            InputStream inputStream = provider.readFile();
            String object = IOUtils.toString(inputStream, StandardCharsets.UTF_8);
            Assertions.assertThat(object).isEqualTo(text);
            Doc doc = provider.createDocument();
            Assertions.assertThat(doc.getFile().getFilename()).isEqualTo("foo.txt");
            Assertions.assertThat(doc.getFile().getFilesize()).isEqualTo(16L);
        }
    }
}
