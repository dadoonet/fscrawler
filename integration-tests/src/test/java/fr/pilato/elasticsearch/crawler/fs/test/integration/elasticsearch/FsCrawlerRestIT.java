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

import com.carrotsearch.randomizedtesting.annotations.Timeout;
import com.jayway.jsonpath.JsonPath;
import com.jayway.jsonpath.PathNotFoundException;
import fr.pilato.elasticsearch.crawler.fs.client.ESSearchHit;
import fr.pilato.elasticsearch.crawler.fs.client.ESSearchRequest;
import fr.pilato.elasticsearch.crawler.fs.client.ESSearchResponse;
import fr.pilato.elasticsearch.crawler.fs.client.ElasticsearchClientException;
import fr.pilato.elasticsearch.crawler.fs.framework.OsValidator;
import fr.pilato.elasticsearch.crawler.fs.framework.Version;
import fr.pilato.elasticsearch.crawler.fs.rest.DeleteResponse;
import fr.pilato.elasticsearch.crawler.fs.rest.ServerStatusResponse;
import fr.pilato.elasticsearch.crawler.fs.rest.UploadResponse;
import fr.pilato.elasticsearch.crawler.fs.settings.FsSettings;
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
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.testcontainers.DockerClientFactory;
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

import static fr.pilato.elasticsearch.crawler.fs.framework.FsCrawlerUtil.INDEX_SUFFIX_FOLDER;
import static fr.pilato.elasticsearch.crawler.fs.framework.TimeValue.MAX_WAIT_FOR_SEARCH;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.Assume.assumeTrue;

public class FsCrawlerRestIT extends AbstractRestITCase {
    private static final Logger logger = LogManager.getLogger();
    private static final String CUSTOM_INDEX_NAME = getCrawlerName(FsCrawlerRestIT.class, "custom");

    public FsSettings getFsSettings() {
        return createTestSettings();
    }

    @Before
    @Override
    public void cleanExistingIndex() throws IOException, ElasticsearchClientException {
        // Also clean the specific indices for this test suite
        client.deleteIndex(CUSTOM_INDEX_NAME);
        client.deleteIndex(CUSTOM_INDEX_NAME + INDEX_SUFFIX_FOLDER);
        super.cleanExistingIndex();
    }

    @After
    @Override
    public void cleanUp() throws ElasticsearchClientException {
        if (!TEST_KEEP_DATA) {
            // Also clean the specific indices for this test suite
            client.deleteIndex(CUSTOM_INDEX_NAME);
            client.deleteIndex(CUSTOM_INDEX_NAME + INDEX_SUFFIX_FOLDER);
        }
        super.cleanUp();
    }

    @Test
    public void callRoot() {
        ServerStatusResponse status = get("/", ServerStatusResponse.class);
        assertThat(status.getVersion()).isEqualTo(Version.getVersion());
        assertThat(status.getElasticsearch()).isNotNull();
    }

    @Test
    @Timeout(millis = 10 * TIMEOUT_MINUTE_AS_MS)
    public void uploadAllDocuments() throws Exception {
        Path from = rootTmpDir.resolve("resources").resolve("documents");
        if (Files.notExists(from)) {
            logger.error("directory [{}] should exist before we start tests", from);
            throw new RuntimeException(from + " doesn't seem to exist. Check your JUnit tests.");
        }
        Files.walk(from)
                .filter(Files::isRegularFile)
                .forEach(path -> {
                    UploadResponse response = uploadFile(target, path);
            assertThat(response.getFilename()).isEqualTo(path.getFileName().toString());
                });

        // We wait until we have all docs
        ESSearchResponse response = countTestHelper(new ESSearchRequest().withIndex(getCrawlerName()),
                Files.list(from).count(), null, MAX_WAIT_FOR_SEARCH);
        for (ESSearchHit hit : response.getHits()) {
            assertThat((String) JsonPath.read(hit.getSource(), "$.file.extension")).isNotEmpty();
        }
    }

    @Test
    public void uploadDocumentWithId() throws Exception {
        Path from = rootTmpDir.resolve("resources").resolve("documents").resolve("test.txt");
        if (Files.notExists(from)) {
            logger.error("file [{}] should exist before we start tests", from);
            throw new RuntimeException(from + " doesn't seem to exist. Check your JUnit tests.");
        }
        UploadResponse uploadResponse = uploadFileWithId(target, from, "1234");
        assertThat(uploadResponse.isOk()).isTrue();

        // We wait until we have our document
        ESSearchResponse response = countTestHelper(new ESSearchRequest().withIndex(getCrawlerName()), 1L, null);
        assertThat(response.getHits().get(0).getId()).isEqualTo("1234");
        assertThat((Integer) JsonPath.read(response.getHits().get(0).getSource(), "$.file.filesize")).isGreaterThan(0);
    }

    @Test
    public void uploadDocumentWithIdUsingPut() throws Exception {
        Path from = rootTmpDir.resolve("resources").resolve("documents").resolve("test.txt");
        if (Files.notExists(from)) {
            logger.error("file [{}] should exist before we start tests", from);
            throw new RuntimeException(from + " doesn't seem to exist. Check your JUnit tests.");
        }
        UploadResponse uploadResponse = putDocument(target, from, null, null, "1234");
        assertThat(uploadResponse.isOk()).isTrue();

        // We wait until we have our document
        ESSearchResponse response = countTestHelper(new ESSearchRequest().withIndex(getCrawlerName()), 1L, null);
        assertThat(response.getHits().get(0).getId()).isEqualTo("1234");
        assertThat((Integer) JsonPath.read(response.getHits().get(0).getSource(), "$.file.filesize")).isGreaterThan(0);
    }

    @Test
    public void deleteDocumentApi() throws Exception {
        // We need to create first the index
        DeleteResponse deleteResponse = deleteDocument(target, null, "foo", null, "/_document");
        assertThat(deleteResponse.isOk()).isFalse();
        assertThat(deleteResponse.getMessage()).startsWith("Can not remove document [");

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
            assertThat(response.getFilename()).isEqualTo(path.getFileName().toString());

                    toBeRemoved.add(response.getFilename());
                });

        // We wait until we have all txt docs
        ESSearchResponse response = countTestHelper(new ESSearchRequest().withIndex(getCrawlerName()),
                number.longValue(), null, MAX_WAIT_FOR_SEARCH);
        for (ESSearchHit hit : response.getHits()) {
            assertThat((String) JsonPath.read(hit.getSource(), "$.file.extension")).isNotEmpty();
        }

        // We can now remove all docs
        for (String filename : toBeRemoved) {
            deleteResponse = deleteDocument(target, null, null, filename, "/_document");
            if (!deleteResponse.isOk()) {
                logger.error("{}", deleteResponse.getMessage());
            }
            assertThat(deleteResponse.isOk()).isTrue();
        }

        // We wait until we have removed all documents
        countTestHelper(new ESSearchRequest().withIndex(getCrawlerName()), 0L, null);
    }

    @Test
    public void allDocumentsWithRestExternalIndex() throws Exception {
        Path from = rootTmpDir.resolve("resources").resolve("documents");
        if (Files.notExists(from)) {
            logger.error("directory [{}] should exist before we start tests", from);
            throw new RuntimeException(from + " doesn't seem to exist. Check your JUnit tests.");
        }
        Files.walk(from)
                .filter(Files::isRegularFile)
                .forEach(path -> {
                    UploadResponse response = uploadFileOnIndex(target, path, CUSTOM_INDEX_NAME);
            assertThat(response.getFilename()).isEqualTo(path.getFileName().toString());
                });

        // We wait until we have all docs
        ESSearchResponse response = countTestHelper(new ESSearchRequest().withIndex(CUSTOM_INDEX_NAME),
                Files.list(from).count(), null, MAX_WAIT_FOR_SEARCH);
        for (ESSearchHit hit : response.getHits()) {
            assertThat((String) JsonPath.read(hit.getSource(), "$.file.extension")).isNotEmpty();
            int filesize = JsonPath.read(hit.getSource(), "$.file.filesize");
            if (filesize <= 0) {
                // On some machines (ie Github Actions), the size is not provided
                logger.warn("File [{}] has a size of [{}]",
                        JsonPath.read(hit.getSource(), "$.file.filename"), filesize);
            } else {
                assertThat((Integer) JsonPath.read(hit.getSource(), "$.file.filesize")).isGreaterThan(0);
            }
        }
    }

    @Test
    public void documentWithExternalTags() throws Exception {
        // We iterate over all sample files, and we try to locate any existing tag file
        // which can overwrite the data we extracted
        AtomicInteger numFiles = new AtomicInteger();
        Files.walk(currentTestResourceDir)
                .filter(Files::isRegularFile)
                .forEach(path -> {
                    Path tagsFilePath = currentTestTagDir.resolve(path.getFileName().toString() + ".json");
                    logger.info("Upload file #[{}]: [{}] with tags [{}]", numFiles.incrementAndGet(), path.getFileName(), tagsFilePath.getFileName());
                    UploadResponse response = uploadFileUsingApi(target, path, tagsFilePath, null, "/_document", null);
                    assertThat(response.getFilename()).isEqualTo(path.getFileName().toString());
                });

        // We wait until we have all our documents docs
        countTestHelper(new ESSearchRequest().withIndex(getCrawlerName()), numFiles.longValue(), null);

        // Let's test every single document that has been enriched
        checkDocument("add_external.txt", hit -> {
            assertThat((String) JsonPath.read(hit.getSource(), "$.content")).contains("This file content will be extracted");
            assertThat((String) JsonPath.read(hit.getSource(), "$.file.extension")).isNotEmpty();
            assertThat((Integer) JsonPath.read(hit.getSource(), "$.file.filesize")).isGreaterThan(0);
            assertThatThrownBy(() -> JsonPath.read(hit.getSource(), "$.meta")).isInstanceOf(PathNotFoundException.class);
            assertThat((Integer) JsonPath.read(hit.getSource(), "$.external.tenantId")).isEqualTo(23);
            assertThat((String) JsonPath.read(hit.getSource(), "$.external.company")).isEqualTo("shoe company");
            assertThat((String) JsonPath.read(hit.getSource(), "$.external.daysOpen[0]")).isEqualTo("Mon");
            assertThat((String) JsonPath.read(hit.getSource(), "$.external.daysOpen[4]")).isEqualTo("Fri");
            assertThat((String) JsonPath.read(hit.getSource(), "$.external.products[0].brand")).isEqualTo("nike");
            assertThat((Integer) JsonPath.read(hit.getSource(), "$.external.products[0].size")).isEqualTo(41);
            assertThat((String) JsonPath.read(hit.getSource(), "$.external.products[0].sub")).isEqualTo("Air MAX");
            assertThat((String) JsonPath.read(hit.getSource(), "$.external.products[1].brand")).isEqualTo("reebok");
            assertThat((Integer) JsonPath.read(hit.getSource(), "$.external.products[1].size")).isEqualTo(43);
            assertThat((String) JsonPath.read(hit.getSource(), "$.external.products[1].sub")).isEqualTo("Pump");
        });
        checkDocument("replace_content_and_external.txt", hit -> {
            assertThat((String) JsonPath.read(hit.getSource(), "$.content")).isEqualTo("OVERWRITTEN CONTENT");
            assertThat((String) JsonPath.read(hit.getSource(), "$.file.extension")).isNotEmpty();
            assertThat((Integer) JsonPath.read(hit.getSource(), "$.file.filesize")).isGreaterThan(0);
            assertThatThrownBy(() -> JsonPath.read(hit.getSource(), "$.meta")).isInstanceOf(PathNotFoundException.class);
            assertThat((Integer) JsonPath.read(hit.getSource(), "$.external.tenantId")).isEqualTo(23);
            assertThat((String) JsonPath.read(hit.getSource(), "$.external.company")).isEqualTo("shoe company");
            assertThat((String) JsonPath.read(hit.getSource(), "$.external.daysOpen[0]")).isEqualTo("Mon");
            assertThat((String) JsonPath.read(hit.getSource(), "$.external.daysOpen[4]")).isEqualTo("Fri");
            assertThat((String) JsonPath.read(hit.getSource(), "$.external.products[0].brand")).isEqualTo("nike");
            assertThat((Integer) JsonPath.read(hit.getSource(), "$.external.products[0].size")).isEqualTo(41);
            assertThat((String) JsonPath.read(hit.getSource(), "$.external.products[0].sub")).isEqualTo("Air MAX");
            assertThat((String) JsonPath.read(hit.getSource(), "$.external.products[1].brand")).isEqualTo("reebok");
            assertThat((Integer) JsonPath.read(hit.getSource(), "$.external.products[1].size")).isEqualTo(43);
            assertThat((String) JsonPath.read(hit.getSource(), "$.external.products[1].sub")).isEqualTo("Pump");
        });
        checkDocument("replace_content_only.txt", hit -> {
            assertThat((String) JsonPath.read(hit.getSource(), "$.content")).isEqualTo("OVERWRITTEN CONTENT");
            assertThat((String) JsonPath.read(hit.getSource(), "$.file.extension")).isNotEmpty();
            assertThat((Integer) JsonPath.read(hit.getSource(), "$.file.filesize")).isGreaterThan(0);
            assertThatThrownBy(() -> JsonPath.read(hit.getSource(), "$.meta")).isInstanceOf(PathNotFoundException.class);
            assertThatThrownBy(() -> JsonPath.read(hit.getSource(), "$.external")).isInstanceOf(PathNotFoundException.class);
        });
        checkDocument("replace_meta_only.txt", hit -> {
            assertThat((String) JsonPath.read(hit.getSource(), "$.content")).contains("This file content will be extracted");
            assertThat((String) JsonPath.read(hit.getSource(), "$.meta.raw.resourceName")).isEqualTo("another-file-name.txt");
            assertThatThrownBy(() -> JsonPath.read(hit.getSource(), "$.external")).isInstanceOf(PathNotFoundException.class);
        });
    }

    @Test
    public void uploadUsingWrongFieldName() {
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

        assertThat(response.isOk()).isFalse();
        assertThat(response.getMessage()).contains("No file has been sent or you are not using [file] as the field name.");
    }

    @Test
    public void uploadDocumentWithFsPlugin() throws Exception {
        Path fileDoesNotExist = rootTmpDir.resolve("resources").resolve("documents").resolve("foobar").resolve("foobar.txt");
        Path fileOutsideWatchedDir = rootTmpDir.resolve("resources").resolve("documents").resolve("test.txt");
        Path correctFile = currentTestResourceDir.resolve("roottxtfile.txt");
        if (Files.notExists(fileOutsideWatchedDir)) {
            logger.error("file [{}] should exist before we start tests", fileOutsideWatchedDir);
            throw new RuntimeException(fileOutsideWatchedDir + " doesn't seem to exist. Check your JUnit tests.");
        }
        if (Files.exists(fileDoesNotExist)) {
            logger.error("file [{}] should not exist before we start tests", fileDoesNotExist);
            throw new RuntimeException(fileDoesNotExist + " exists. Check your JUnit tests.");
        }
        if (Files.notExists(correctFile)) {
            logger.error("file [{}] should exist before we start tests", correctFile);
            throw new RuntimeException(correctFile + " doesn't seem to exist. Check your JUnit tests.");
        }

        // We try with a document that does not exist
        String json = "{\n" +
                "  \"type\": \"local\",\n" +
                "  \"local\": {\n" +
                "    \"url\": \"" + fileDoesNotExist.toString().replace("\\", "\\\\") + "\"\n" +
                "  }\n" +
                "}";
        UploadResponse uploadResponse = post(target, "/_document", json, UploadResponse.class);
        assertThat(uploadResponse.isOk()).isFalse();
        assertThat(uploadResponse.getMessage()).contains("FsCrawlerIllegalConfigurationException");
        assertThat(uploadResponse.getMessage()).contains(fileDoesNotExist.toString());

        // We try with an existing document which is not part of the crawler fs.url
        json = "{\n" +
                "  \"type\": \"local\",\n" +
                "  \"local\": {\n" +
                "    \"url\": \"" + fileOutsideWatchedDir.toString().replace("\\", "\\\\") + "\"\n" +
                "  }\n" +
                "}";
        uploadResponse = post(target, "/_document", json, UploadResponse.class);
        assertThat(uploadResponse.isOk()).isFalse();
        assertThat(uploadResponse.getMessage())
                .contains("FsCrawlerIllegalConfigurationException")
                .contains("is not within")
                .contains(fileOutsideWatchedDir.toString());

        // We try with an existing document which is part of the crawler fs.url
        json = "{\n" +
                "  \"type\": \"local\",\n" +
                "  \"local\": {\n" +
                "    \"url\": \"" + correctFile.toString().replace("\\", "\\\\") + "\"\n" +
                "  }\n" +
                "}";
        uploadResponse = post(target, "/_document", json, UploadResponse.class);
        assertThat(uploadResponse.isOk()).isTrue();

        // We wait until we have our document
        ESSearchResponse response = countTestHelper(new ESSearchRequest().withIndex(getCrawlerName()), 1L, null);
        assertThat((String) JsonPath.read(response.getHits().get(0).getSource(), "$.file.filename")).isEqualTo("roottxtfile.txt");
        assertThat((Integer) JsonPath.read(response.getHits().get(0).getSource(), "$.file.filesize")).isEqualTo(30);
        assertThat((String) JsonPath.read(response.getHits().get(0).getSource(), "$.content")).contains("This file contains some words.");
        if (OsValidator.WINDOWS) {
            assertThat((String) JsonPath.read(response.getHits().get(0).getSource(), "$.path.virtual")).isEqualTo("\\");
        } else {
            assertThat((String) JsonPath.read(response.getHits().get(0).getSource(), "$.path.virtual")).isEqualTo("/");
        }
        assertThat((String) JsonPath.read(response.getHits().get(0).getSource(), "$.path.real")).isEqualTo(correctFile.toAbsolutePath().toString());

    }

    @Test
    public void uploadDocumentWithS3Plugin() throws Exception {
        // We can only run this test if Docker is available on this machine
        assumeTrue("We can only run this test if Docker is available on this machine", DockerClientFactory.instance().isDockerAvailable());

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
            assertThat(uploadResponse.isOk()).isFalse();
            assertThat(uploadResponse.getMessage()).contains("FsCrawlerIllegalConfigurationException");
            assertThat(uploadResponse.getMessage()).contains("The specified key does not exist");

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
            assertThat(uploadResponse.isOk()).isTrue();

            // We wait until we have our document
            ESSearchResponse response = countTestHelper(new ESSearchRequest().withIndex(getCrawlerName()), 1L, null);
            assertThat((String) JsonPath.read(response.getHits().get(0).getSource(), "$.file.filename")).isEqualTo("roottxtfile.txt");
            // When on Windows the expected file size differs
            int expectedFilesize = OsValidator.WINDOWS ? 12364 : 12230;
            assertThat((Integer) JsonPath.read(response.getHits().get(0).getSource(), "$.file.filesize")).isEqualTo(expectedFilesize);
            assertThat((String) JsonPath.read(response.getHits().get(0).getSource(), "$.content")).contains("Nihil est enim virtute amabilius");
        }
    }

    @Test
    public void uploadDocumentWithUnknownPlugin() {
        Path fromExists = rootTmpDir.resolve("resources").resolve("documents").resolve("test.txt");
        if (Files.notExists(fromExists)) {
            logger.error("file [{}] should exist before we start tests", fromExists);
            throw new RuntimeException(fromExists + " doesn't seem to exist. Check your JUnit tests.");
        }

        // We try with an existing document
        String json = "{\n" +
                "  \"type\": \"not_available\",\n" +
                "  \"not_available\": {\n" +
                "    \"url\": \"" + fromExists.toString().replace("\\", "\\\\") + "\"\n" +
                "  }\n" +
                "}";
        UploadResponse uploadResponse = post(target, "/_document", json, UploadResponse.class);
        assertThat(uploadResponse).satisfies(response -> {
            assertThat(response.isOk()).isFalse();
            assertThat(response.getMessage()).contains("No FsProvider found for type [not_available]");
        });
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
        public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) {
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
    public void uploadDocumentWithHttpPlugin() throws Exception {
        // We can only run this test if Docker is available on this machine
        assumeTrue("We can only run this test if Docker is available on this machine", DockerClientFactory.instance().isDockerAvailable());

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
            assertThat(uploadResponse.isOk()).isFalse().isFalse();
            assertThat(uploadResponse.getMessage()).contains("FileNotFoundException");
            assertThat(uploadResponse.getMessage()).contains("doesnotexist.txt");

            // We try with an existing document
            json = "{\n" +
                    "  \"type\": \"http\",\n" +
                    "  \"http\": {\n" +
                    "    \"url\": \"" + url + "/foo.txt\"\n" +
                    "  }\n" +
                    "}";
            uploadResponse = post(target, "/_document", json, UploadResponse.class);
            assertThat(uploadResponse.isOk()).isTrue();

            // We wait until we have our document
            ESSearchResponse response = countTestHelper(new ESSearchRequest().withIndex(getCrawlerName()), 1L, null);
            assertThat((String) JsonPath.read(response.getHits().get(0).getSource(), "$.file.filename")).isEqualTo("foo.txt");
            assertThat((Integer) JsonPath.read(response.getHits().get(0).getSource(), "$.file.filesize")).isEqualTo(16);
            assertThat((String) JsonPath.read(response.getHits().get(0).getSource(), "$.content")).contains(text);

            // We try with an existing document running on https
            json = "{\n" +
                    "  \"type\": \"http\",\n" +
                    "  \"http\": {\n" +
                    "    \"url\": \"https://www.google.fr/robots.txt\"\n" +
                    "  }\n" +
                    "}";
            uploadResponse = post(target, "/_document", json, UploadResponse.class);
            assertThat(uploadResponse.getMessage()).isNull();

            // We wait until we have our document
            response = countTestHelper(new ESSearchRequest().withIndex(getCrawlerName()).withSort("file.indexing_date"), 2L, null);
            assertThat((String) JsonPath.read(response.getHits().get(1).getSource(), "$.file.filename")).isEqualTo("robots.txt");
            assertThat((Integer) JsonPath.read(response.getHits().get(1).getSource(), "$.file.filesize")).isGreaterThan(100);
            assertThat((String) JsonPath.read(response.getHits().get(1).getSource(), "$.content")).contains("Sitemap");
        }
    }
}
