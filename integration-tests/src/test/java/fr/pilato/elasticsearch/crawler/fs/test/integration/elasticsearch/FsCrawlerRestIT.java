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

package fr.pilato.elasticsearch.crawler.fs.test.integration.elasticsearch;

import com.jayway.jsonpath.JsonPath;
import com.jayway.jsonpath.PathNotFoundException;
import fr.pilato.elasticsearch.crawler.fs.client.ESSearchHit;
import fr.pilato.elasticsearch.crawler.fs.client.ESSearchRequest;
import fr.pilato.elasticsearch.crawler.fs.client.ESSearchResponse;
import fr.pilato.elasticsearch.crawler.fs.framework.TimeValue;
import fr.pilato.elasticsearch.crawler.fs.framework.Version;
import fr.pilato.elasticsearch.crawler.fs.rest.DeleteResponse;
import fr.pilato.elasticsearch.crawler.fs.rest.ServerStatusResponse;
import fr.pilato.elasticsearch.crawler.fs.rest.UploadResponse;
import fr.pilato.elasticsearch.crawler.fs.settings.FsSettings;
import fr.pilato.elasticsearch.crawler.fs.settings.Rest;
import fr.pilato.elasticsearch.crawler.fs.test.integration.AbstractRestITCase;
import io.minio.MakeBucketArgs;
import io.minio.MinioClient;
import io.minio.PutObjectArgs;
import io.minio.errors.MinioException;
import jakarta.ws.rs.core.MediaType;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.glassfish.jersey.media.multipart.FormDataMultiPart;
import org.glassfish.jersey.media.multipart.file.FileDataBodyPart;
import org.junit.Test;
import org.testcontainers.containers.MinIOContainer;
import org.testcontainers.containers.NginxContainer;
import org.testcontainers.containers.wait.strategy.HttpWaitStrategy;
import org.testcontainers.utility.MountableFile;

import java.io.IOException;
import java.net.URL;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Predicate;

import static org.hamcrest.CoreMatchers.notNullValue;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.*;

public class FsCrawlerRestIT extends AbstractRestITCase {
    private static final Logger logger = LogManager.getLogger();

    public FsSettings getFsSettings() throws IOException {
        return FsSettings.builder(getCrawlerName())
                .setRest(new Rest("http://127.0.0.1:" + getRestPort() + "/fscrawler"))
                .setElasticsearch(elasticsearchConfiguration)
                .build();
    }

    @Test
    public void testCallRoot() {
        ServerStatusResponse status = get("/", ServerStatusResponse.class);
        assertThat(status.getVersion(), is(Version.getVersion()));
        assertThat(status.getElasticsearch(), notNullValue());
    }

    @Test
    public void testUploadAllDocuments() throws Exception {
        Path from = rootTmpDir.resolve("resources").resolve("documents");
        if (Files.notExists(from)) {
            logger.error("directory [{}] should exist before we start tests", from);
            throw new RuntimeException(from + " doesn't seem to exist. Check your JUnit tests.");
        }
        Files.walk(from)
                .filter(Files::isRegularFile)
                .forEach(path -> {
                    UploadResponse response = uploadFile(target, path);
                    assertThat(response.getFilename(), is(path.getFileName().toString()));
                });

        // We wait until we have all docs
        ESSearchResponse response = countTestHelper(new ESSearchRequest().withIndex(getCrawlerName()), Files.list(from).count(), null, TimeValue
                .timeValueMinutes(2));
        for (ESSearchHit hit : response.getHits()) {
            assertThat(JsonPath.read(hit.getSource(), "$.file.extension"), notNullValue());
        }
    }

    @Deprecated
    @Test
    public void testUploadTxtDocumentsWithDeprecatedApi() throws Exception {
        Path from = rootTmpDir.resolve("resources").resolve("documents");
        if (Files.notExists(from)) {
            logger.error("directory [{}] should exist before we start tests", from);
            throw new RuntimeException(from + " doesn't seem to exist. Check your JUnit tests.");
        }
        AtomicInteger number = new AtomicInteger();
        Files.walk(from)
                .filter(Files::isRegularFile)
                .filter(new Predicate<Path>() {
                    @Override
                    public boolean test(Path path) {
                        return path.toString().endsWith("txt");
                    }
                })
                .forEach(path -> {
                    number.getAndIncrement();
                    UploadResponse response = uploadFileUsingApi(target, path, null, null, "/_upload", null);
                    assertThat(response.getFilename(), is(path.getFileName().toString()));
                });

        // We wait until we have all txt docs
        ESSearchResponse response = countTestHelper(new ESSearchRequest().withIndex(getCrawlerName()), number.longValue(), null, TimeValue
                .timeValueMinutes(2));
        for (ESSearchHit hit : response.getHits()) {
            assertThat(JsonPath.read(hit.getSource(), "$.file.extension"), notNullValue());
        }
    }

    @Test
    public void testUploadDocumentWithId() throws Exception {
        Path from = rootTmpDir.resolve("resources").resolve("documents").resolve("test.txt");
        if (Files.notExists(from)) {
            logger.error("file [{}] should exist before we start tests", from);
            throw new RuntimeException(from + " doesn't seem to exist. Check your JUnit tests.");
        }
        UploadResponse uploadResponse = uploadFileWithId(target, from, "1234");
        assertThat(uploadResponse.isOk(), is(true));

        // We wait until we have our document
        ESSearchResponse response = countTestHelper(new ESSearchRequest().withIndex(getCrawlerName()), 1L, null);
        assertThat(response.getHits().get(0).getId(), is("1234"));
        assertThat(JsonPath.read(response.getHits().get(0).getSource(), "$.file.filesize"), notNullValue());
    }

    @Test
    public void testUploadDocumentWithIdUsingPut() throws Exception {
        Path from = rootTmpDir.resolve("resources").resolve("documents").resolve("test.txt");
        if (Files.notExists(from)) {
            logger.error("file [{}] should exist before we start tests", from);
            throw new RuntimeException(from + " doesn't seem to exist. Check your JUnit tests.");
        }
        UploadResponse uploadResponse = putDocument(target, from, null, null, "1234");
        assertThat(uploadResponse.isOk(), is(true));

        // We wait until we have our document
        ESSearchResponse response = countTestHelper(new ESSearchRequest().withIndex(getCrawlerName()), 1L, null);
        assertThat(response.getHits().get(0).getId(), is("1234"));
        assertThat(JsonPath.read(response.getHits().get(0).getSource(), "$.file.filesize"), greaterThan(0));
    }

    @Test
    public void testDeleteDocumentApi() throws Exception {
        // We need to create first the index
        DeleteResponse deleteResponse = deleteDocument(target, null, "foo", null, "/_document");
        assertThat(deleteResponse.isOk(), is(false));
        assertThat(deleteResponse.getMessage(), startsWith("Can not remove document ["));

        Path from = rootTmpDir.resolve("resources").resolve("documents");
        if (Files.notExists(from)) {
            logger.error("directory [{}] should exist before we start tests", from);
            throw new RuntimeException(from + " doesn't seem to exist. Check your JUnit tests.");
        }
        AtomicInteger number = new AtomicInteger();
        List<String> toBeRemoved = new ArrayList<>();

        Files.walk(from)
                .filter(Files::isRegularFile)
                .filter(new Predicate<Path>() {
                    @Override
                    public boolean test(Path path) {
                        return path.toString().endsWith("txt");
                    }
                })
                .forEach(path -> {
                    number.getAndIncrement();
                    UploadResponse response = uploadFileUsingApi(target, path, null, null, "/_document", null);
                    assertThat(response.getFilename(), is(path.getFileName().toString()));

                    toBeRemoved.add(response.getFilename());
                });

        // We wait until we have all txt docs
        ESSearchResponse response = countTestHelper(new ESSearchRequest().withIndex(getCrawlerName()), number.longValue(), null, TimeValue
                .timeValueMinutes(2));
        for (ESSearchHit hit : response.getHits()) {
            assertThat(JsonPath.read(hit.getSource(), "$.file.extension"), notNullValue());
        }

        // We can now remove all docs
        for (String filename : toBeRemoved) {
            deleteResponse = deleteDocument(target, null, null, filename, "/_document");
            if (!deleteResponse.isOk()) {
                logger.error("{}", deleteResponse.getMessage());
            }
            assertThat(deleteResponse.isOk(), is(true));
        }

        // We wait until we have removed all documents
        response = countTestHelper(new ESSearchRequest().withIndex(getCrawlerName()), 0L, null, TimeValue
                .timeValueMinutes(2));
    }

    @Test
    public void testAllDocumentsWithRestExternalIndex() throws Exception {
        Path from = rootTmpDir.resolve("resources").resolve("documents");
        if (Files.notExists(from)) {
            logger.error("directory [{}] should exist before we start tests", from);
            throw new RuntimeException(from + " doesn't seem to exist. Check your JUnit tests.");
        }
        String index = "fscrawler_fs_custom";
        Files.walk(from)
                .filter(Files::isRegularFile)
                .forEach(path -> {
                    UploadResponse response = uploadFileOnIndex(target, path, index);
                    assertThat(response.getFilename(), is(path.getFileName().toString()));
                });

        // We wait until we have all docs
        ESSearchResponse response = countTestHelper(new ESSearchRequest().withIndex(index), Files.list(from).count(), null, TimeValue
                .timeValueMinutes(2));
        for (ESSearchHit hit : response.getHits()) {
            assertThat(JsonPath.read(hit.getSource(), "$.file.extension"), notNullValue());
            int filesize = JsonPath.read(hit.getSource(), "$.file.filesize");
            if (filesize <= 0) {
                // On some machines (ie Github Actions), the size is not provided
                logger.warn("File [{}] has a size of [{}]",
                        JsonPath.read(hit.getSource(), "$.file.filename"), filesize);
            } else {
                assertThat(JsonPath.read(hit.getSource(), "$.file.filesize"), greaterThan(0));
            }
        }
    }

    @Test
    public void testDocumentWithExternalTags() throws Exception {
        // We iterate over all sample files and we try to locate any existing tag file
        // which can overwrite the data we extracted
        AtomicInteger numFiles = new AtomicInteger();
        Files.walk(currentTestResourceDir)
                .filter(Files::isRegularFile)
                .forEach(path -> {
                    Path tagsFilePath = currentTestTagDir.resolve(path.getFileName().toString() + ".json");
                    logger.debug("Upload file #[{}]: [{}] with tags [{}]", numFiles.incrementAndGet(), path.getFileName(), tagsFilePath.getFileName());
                    UploadResponse response = uploadFileUsingApi(target, path, tagsFilePath, null, "/_document", null);
                    assertThat(response.getFilename(), is(path.getFileName().toString()));
                });

        // We wait until we have all our documents docs
        countTestHelper(new ESSearchRequest().withIndex(getCrawlerName()), numFiles.longValue(), null, TimeValue.timeValueMinutes(2));

        // Let's test every single document that has been enriched
        checkDocument("add_external.txt", hit -> {
            assertThat(JsonPath.read(hit.getSource(), "$.content"), containsString("This file content will be extracted"));
            assertThat(JsonPath.read(hit.getSource(), "$.file.extension"), notNullValue());
            assertThat(JsonPath.read(hit.getSource(), "$.file.filesize"), greaterThan(0));
            expectThrows(PathNotFoundException.class, () -> JsonPath.read(hit.getSource(), "$.meta"));
            assertThat(JsonPath.read(hit.getSource(), "$.external.tenantId"), is(23));
            assertThat(JsonPath.read(hit.getSource(), "$.external.company"), is("shoe company"));
            assertThat(JsonPath.read(hit.getSource(), "$.external.daysOpen[0]"), is("Mon"));
            assertThat(JsonPath.read(hit.getSource(), "$.external.daysOpen[4]"), is("Fri"));
            assertThat(JsonPath.read(hit.getSource(), "$.external.products[0].brand"), is("nike"));
            assertThat(JsonPath.read(hit.getSource(), "$.external.products[0].size"), is(41));
            assertThat(JsonPath.read(hit.getSource(), "$.external.products[0].sub"), is("Air MAX"));
            assertThat(JsonPath.read(hit.getSource(), "$.external.products[1].brand"), is("reebok"));
            assertThat(JsonPath.read(hit.getSource(), "$.external.products[1].size"), is(43));
            assertThat(JsonPath.read(hit.getSource(), "$.external.products[1].sub"), is("Pump"));
        });
        checkDocument("replace_content_and_external.txt", hit -> {
            assertThat(JsonPath.read(hit.getSource(), "$.content"), is("OVERWRITTEN CONTENT"));
            assertThat(JsonPath.read(hit.getSource(), "$.file.extension"), notNullValue());
            assertThat(JsonPath.read(hit.getSource(), "$.file.filesize"), greaterThan(0));
            expectThrows(PathNotFoundException.class, () -> JsonPath.read(hit.getSource(), "$.meta"));
            assertThat(JsonPath.read(hit.getSource(), "$.external.tenantId"), is(23));
            assertThat(JsonPath.read(hit.getSource(), "$.external.company"), is("shoe company"));
            assertThat(JsonPath.read(hit.getSource(), "$.external.daysOpen[0]"), is("Mon"));
            assertThat(JsonPath.read(hit.getSource(), "$.external.daysOpen[4]"), is("Fri"));
            assertThat(JsonPath.read(hit.getSource(), "$.external.products[0].brand"), is("nike"));
            assertThat(JsonPath.read(hit.getSource(), "$.external.products[0].size"), is(41));
            assertThat(JsonPath.read(hit.getSource(), "$.external.products[0].sub"), is("Air MAX"));
            assertThat(JsonPath.read(hit.getSource(), "$.external.products[1].brand"), is("reebok"));
            assertThat(JsonPath.read(hit.getSource(), "$.external.products[1].size"), is(43));
            assertThat(JsonPath.read(hit.getSource(), "$.external.products[1].sub"), is("Pump"));
        });
        checkDocument("replace_content_only.txt", hit -> {
            assertThat(JsonPath.read(hit.getSource(), "$.content"), is("OVERWRITTEN CONTENT"));
            assertThat(JsonPath.read(hit.getSource(), "$.file.extension"), notNullValue());
            assertThat(JsonPath.read(hit.getSource(), "$.file.filesize"), greaterThan(0));
            expectThrows(PathNotFoundException.class, () -> JsonPath.read(hit.getSource(), "$.meta"));
            expectThrows(PathNotFoundException.class, () -> JsonPath.read(hit.getSource(), "$.external"));
        });
        checkDocument("replace_meta_only.txt", hit -> {
            assertThat(JsonPath.read(hit.getSource(), "$.content"), containsString("This file content will be extracted"));
            assertThat(JsonPath.read(hit.getSource(), "$.meta.raw.resourceName"), is("another-file-name.txt"));
            expectThrows(PathNotFoundException.class, () -> JsonPath.read(hit.getSource(), "$.external"));
        });
    }

    @Test
    public void testUploadUsingWrongFieldName() {
        Path from = rootTmpDir.resolve("resources").resolve("documents").resolve("test.txt");
        if (Files.notExists(from)) {
            logger.error("file [{}] should exist before we start tests", from);
            throw new RuntimeException(from + " doesn't seem to exist. Check your JUnit tests.");
        }

        Map<String, Object> params = new HashMap<>();
        FileDataBodyPart filePart = new FileDataBodyPart("anotherfieldname", from.toFile(), MediaType.APPLICATION_OCTET_STREAM_TYPE);
        FormDataMultiPart mp = new FormDataMultiPart();
        mp.bodyPart(filePart);
        if (logger.isDebugEnabled()) {
            logger.debug("Rest response: {}", post(target, "/_document", mp, String.class, debugOption));
        }

        UploadResponse response = post(target, "/_document", mp, UploadResponse.class, params);

        assertThat(response.isOk(), is(false));
        assertThat(response.getMessage(), containsString("No file has been sent or you are not using [file] as the field name."));
    }

    @Test
    public void testUploadDocumentWithFsPlugin() throws Exception {
        Path fromDoesNotExist = rootTmpDir.resolve("resources").resolve("documents").resolve("foobar").resolve("foobar.txt");
        Path fromExists = rootTmpDir.resolve("resources").resolve("documents").resolve("test.txt");
        if (Files.notExists(fromExists)) {
            logger.error("file [{}] should exist before we start tests", fromExists);
            throw new RuntimeException(fromExists + " doesn't seem to exist. Check your JUnit tests.");
        }
        if (Files.exists(fromDoesNotExist)) {
            logger.error("file [{}] should not exist before we start tests", fromDoesNotExist);
            throw new RuntimeException(fromDoesNotExist + " exists. Check your JUnit tests.");
        }

        // We try with a document that does not exist
        String json = "{\n" +
                "  \"type\": \"local\",\n" +
                "  \"local\": {\n" +
                "    \"url\": \"" + fromDoesNotExist + "\"\n" +
                "  }\n" +
                "}";
        UploadResponse uploadResponse = post(target, "/_document", json, UploadResponse.class);
        assertThat(uploadResponse.isOk(), is(false));
        assertThat(uploadResponse.getMessage(), containsString("NoSuchFileException"));
        assertThat(uploadResponse.getMessage(), containsString(fromDoesNotExist.toString()));

        // We try with an existing document
        json = "{\n" +
                "  \"type\": \"local\",\n" +
                "  \"local\": {\n" +
                "    \"url\": \"" + fromExists + "\"\n" +
                "  }\n" +
                "}";
        uploadResponse = post(target, "/_document", json, UploadResponse.class);
        assertThat(uploadResponse.isOk(), is(true));

        // We wait until we have our document
        ESSearchResponse response = countTestHelper(new ESSearchRequest().withIndex(getCrawlerName()), 1L, null);
        assertThat(JsonPath.read(response.getHits().get(0).getSource(), "$.file.filename"), is("test.txt"));
        assertThat(JsonPath.read(response.getHits().get(0).getSource(), "$.file.filesize"), is (30));
        assertThat(JsonPath.read(response.getHits().get(0).getSource(), "$.content"), containsString ("This file contains some words."));
    }

    @Test
    public void testUploadDocumentWithS3Plugin() throws Exception {
        logger.info("Starting Minio");

        try (MinIOContainer container = new MinIOContainer("minio/minio")) {
            container.start();
            String s3Url = container.getS3URL();
            String s3Username = container.getUserName();
            String s3Password = container.getPassword();
            logger.info("Minio started on {} with username {} and password {}. Console running at {}",
                    s3Url, s3Username, s3Password,
                    String.format("http://%s:%s", container.getHost(), container.getMappedPort(9001)));

            // Upload all files to Minio
            String bucket = "documents";
            logger.debug("  --> Copying test resources from [{}] to Minio [{}] bucket", DEFAULT_RESOURCES, bucket);

            try (MinioClient minioClient = MinioClient.builder()
                    .endpoint(s3Url)
                    .credentials(s3Username, s3Password)
                    .build()) {
                minioClient.makeBucket(MakeBucketArgs.builder().bucket(bucket).build());
                Files.walkFileTree(DEFAULT_RESOURCES, EnumSet.of(FileVisitOption.FOLLOW_LINKS), Integer.MAX_VALUE,
                        new InternalFileVisitor(bucket, minioClient, logger));
                logger.debug("  --> Test resources ready in [{}]", s3Url);
            }

            // We try with a document that does not exist
            String json = "{\n" +
                    "  \"type\": \"s3\",\n" +
                    "  \"s3\": {\n" +
                    "    \"url\": \"" + s3Url +"\",\n" +
                    "    \"bucket\": \"documents\",\n" +
                    "    \"object\": \"foobar/foobar.txt\",\n" +
                    "    \"access_key\": \"" + s3Username + "\",\n" +
                    "    \"secret_key\": \"" + s3Password + "\" \n" +
                    "  }\n" +
                    "}";

            UploadResponse uploadResponse = post(target, "/_document", json, UploadResponse.class);
            assertThat(uploadResponse.isOk(), is(false));
            assertThat(uploadResponse.getMessage(), containsString("FsCrawlerIllegalConfigurationException"));
            assertThat(uploadResponse.getMessage(), containsString("The specified key does not exist"));

            // We try with an existing document
            json = "{\n" +
                    "  \"type\": \"s3\",\n" +
                    "  \"s3\": {\n" +
                    "    \"url\": \"" + s3Url +"\",\n" +
                    "    \"bucket\": \"documents\",\n" +
                    "    \"object\": \"roottxtfile.txt\",\n" +
                    "    \"access_key\": \"" + s3Username + "\",\n" +
                    "    \"secret_key\": \"" + s3Password + "\" \n" +
                    "  }\n" +
                    "}";
            uploadResponse = post(target, "/_document", json, UploadResponse.class);
            assertThat(uploadResponse.isOk(), is(true));

            // We wait until we have our document
            ESSearchResponse response = countTestHelper(new ESSearchRequest().withIndex(getCrawlerName()), 1L, null);
            assertThat(JsonPath.read(response.getHits().get(0).getSource(), "$.file.filename"), is("roottxtfile.txt"));
            assertThat(JsonPath.read(response.getHits().get(0).getSource(), "$.file.filesize"), is (12230));
            assertThat(JsonPath.read(response.getHits().get(0).getSource(), "$.content"), containsString ("Nihil est enim virtute amabilius"));
        }
    }

    private static class InternalFileVisitor extends SimpleFileVisitor<Path> {
        private final String bucket;
        private final MinioClient minioClient;
        private final Logger logger;

        public InternalFileVisitor(String bucket, MinioClient minioClient, Logger logger) {
            this.bucket = bucket;
            this.minioClient = minioClient;
            this.logger = logger;
        }

        @Override
        public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) throws IOException {
            logger.trace("  --> Creating dir [{}] in [{}]", dir, bucket);
            return FileVisitResult.CONTINUE;
        }

        @Override
        public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
            logger.trace("  --> Copying [{}] to [{}]", file, bucket);
            try {
                minioClient.putObject(PutObjectArgs.builder()
                        .bucket(bucket)
                        .object(file.getFileName().toString())
                        .stream(Files.newInputStream(file), file.toFile().length(), -1)
                        .build());
            } catch (MinioException | InvalidKeyException | NoSuchAlgorithmException e) {
                throw new IOException(e);
            }
            return FileVisitResult.CONTINUE;
        }
    }

    private void createFile(Path root, String objectName, String object) throws IOException {
        logger.debug("Create fake content [{}]; [{}]", objectName, object);
        Path file = root.resolve(objectName);
        Files.writeString(file, object);
    }

    @Test
    public void testUploadDocumentWithHttpPlugin() throws Exception {
        logger.info("Starting Nginx from {}", rootTmpDir);
        Path nginxRoot = rootTmpDir.resolve("nginx-root");
        Files.createDirectory(nginxRoot);
        Files.writeString(nginxRoot.resolve("index.html"), "<html><body>Hello World!</body></html>");
        String text = "Hello Foo world!";
        createFile(nginxRoot, "foo.txt", text);
        createFile(nginxRoot, "bar.txt", "This one should be ignored.");

        try (NginxContainer<?> container = new NginxContainer<>("nginx")) {
            container.waitingFor(new HttpWaitStrategy());
            container.start();
            container.copyFileToContainer(MountableFile.forHostPath(nginxRoot), "/usr/share/nginx/html");
            URL url = container.getBaseUrl("http", 80);
            logger.info("Nginx started on {}.", url);

            // We try with a document that does not exist
            String json = "{\n" +
                    "  \"type\": \"http\",\n" +
                    "  \"http\": {\n" +
                    "    \"url\": \"" + url + "/doesnotexist.txt\"\n" +
                    "  }\n" +
                    "}";
            UploadResponse uploadResponse = post(target, "/_document", json, UploadResponse.class);
            assertThat(uploadResponse.isOk(), is(false));
            assertThat(uploadResponse.getMessage(), containsString("FileNotFoundException"));
            assertThat(uploadResponse.getMessage(), containsString("doesnotexist.txt"));

            // We try with an existing document
            json = "{\n" +
                    "  \"type\": \"http\",\n" +
                    "  \"http\": {\n" +
                    "    \"url\": \"" + url + "/foo.txt\"\n" +
                    "  }\n" +
                    "}";
            uploadResponse = post(target, "/_document", json, UploadResponse.class);
            assertThat(uploadResponse.isOk(), is(true));

            // We wait until we have our document
            ESSearchResponse response = countTestHelper(new ESSearchRequest().withIndex(getCrawlerName()), 1L, null);
            assertThat(JsonPath.read(response.getHits().get(0).getSource(), "$.file.filename"), is("foo.txt"));
            assertThat(JsonPath.read(response.getHits().get(0).getSource(), "$.file.filesize"), is (16));
            assertThat(JsonPath.read(response.getHits().get(0).getSource(), "$.content"), containsString(text));

            // We try with an existing document running on https
            json = "{\n" +
                    "  \"type\": \"http\",\n" +
                    "  \"http\": {\n" +
                    "    \"url\": \"https://www.elastic.co/robots.txt\"\n" +
                    "  }\n" +
                    "}";
            uploadResponse = post(target, "/_document", json, UploadResponse.class);
            assertThat(uploadResponse.getMessage(), nullValue());

            // We wait until we have our document
            response = countTestHelper(new ESSearchRequest().withIndex(getCrawlerName()).withSort("file.indexing_date"), 2L, null);
            assertThat(JsonPath.read(response.getHits().get(1).getSource(), "$.file.filename"), is("robots.txt"));
            assertThat(JsonPath.read(response.getHits().get(1).getSource(), "$.file.filesize"), greaterThan(100));
            assertThat(JsonPath.read(response.getHits().get(1).getSource(), "$.content"), containsString("Sitemap"));
        }
    }
}
