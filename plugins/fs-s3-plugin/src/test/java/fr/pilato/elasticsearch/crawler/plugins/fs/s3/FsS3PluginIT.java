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
package fr.pilato.elasticsearch.crawler.plugins.fs.s3;

import com.carrotsearch.randomizedtesting.ThreadFilter;
import com.carrotsearch.randomizedtesting.annotations.ThreadLeakFilters;
import fr.pilato.elasticsearch.crawler.fs.test.framework.AbstractFSCrawlerTestCase;
import fr.pilato.elasticsearch.crawler.plugins.FsCrawlerExtensionFsProvider;
import io.minio.BucketExistsArgs;
import io.minio.MakeBucketArgs;
import io.minio.MinioClient;
import io.minio.PutObjectArgs;
import io.minio.errors.*;
import org.apache.commons.io.IOUtils;
import org.junit.*;
import org.testcontainers.containers.MinIOContainer;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;


@ThreadLeakFilters(filters = {
        AbstractFSCrawlerTestCase.TestContainerThreadFilter.class,
        AbstractFSCrawlerTestCase.JNACleanerThreadFilter.class,
        FsS3PluginIT.MinioThreadFilter.class
})
public class FsS3PluginIT extends AbstractFSCrawlerTestCase {
    private static MinIOContainer container;
    private static String s3Url = "http://localhost:9000";
    private static String s3Username = "minioadmin";
    private static String s3Password = "minioadmin";

    @Before
    public void startMinioContainer() {
        staticLogger.info("Starting Minio");
        container = new MinIOContainer("minio/minio");
        container.start();
        s3Url = container.getS3URL();
        s3Username = container.getUserName();
        s3Password = container.getPassword();
        staticLogger.info("Minio started on {} with username {} and password {}.", s3Url, s3Username, s3Password);
    }

    @After
    public void stopMinioContainer() throws Exception {
        if (container != null) {
            container.close();
            container = null;
            staticLogger.info("Minio stopped.");
        }
    }

    private static void createBucket(String objectName, String object) {
        staticLogger.info("Create fake bucket [{}]; [{}]", objectName, object);
        try (MinioClient minioClient = MinioClient.builder()
                .endpoint(s3Url)
                .credentials(s3Username, s3Password)
                .build()) {
            if (!minioClient.bucketExists(BucketExistsArgs.builder().bucket("foo").build())) {
                minioClient.makeBucket(MakeBucketArgs.builder().bucket("foo").build());
            }
            minioClient.putObject(PutObjectArgs.builder()
                    .contentType("text/plain")
                    .bucket("foo")
                    .object(objectName)
                    .stream(IOUtils.toInputStream(object, StandardCharsets.UTF_8), object.length(), -1)
                    .build());
        } catch (Exception e) {
            staticLogger.warn("Could not create bucket [{}]; [{}]: {}", objectName, object, e.getMessage());
        }
    }

    @Test
    public void testMinio() throws Exception {
        String text = "Hello Foo world!";
        createBucket("foo.txt", text);
        createBucket("bar.txt", "This one should be ignored.");

        logger.info("Starting Test");
        try (FsCrawlerExtensionFsProvider provider = new FsS3Plugin.FsCrawlerExtensionFsProviderS3()) {
            provider.settings("{\n" +
                    "  \"type\": \"s3\",\n" +
                    "  \"s3\": {\n" +
                    "    \"url\": \"" + s3Url + "\",\n" +
                    "    \"bucket\": \"foo\",\n" +
                    "    \"object\": \"foo.txt\",\n" +
                    "    \"access_key\": \"" + s3Username + "\",\n" +
                    "    \"secret_key\": \"" + s3Password + "\"\n" +
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

    /**
     * This is temporary until https://github.com/minio/minio-java/issues/1584 is solved
     */
    static public class MinioThreadFilter implements ThreadFilter {
        @Override
        public boolean reject(Thread t) {
            return "Okio Watchdog".equals(t.getName())
                    || "OkHttp TaskRunner".equals(t.getName())
                    || "ForkJoinPool.commonPool-worker-1".equals(t.getName());
        }
    }
}
