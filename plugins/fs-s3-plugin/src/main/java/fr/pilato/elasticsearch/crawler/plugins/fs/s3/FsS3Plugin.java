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

import com.jayway.jsonpath.PathNotFoundException;
import fr.pilato.elasticsearch.crawler.fs.framework.FsCrawlerIllegalConfigurationException;
import fr.pilato.elasticsearch.crawler.plugins.FsCrawlerExtensionFsProviderAbstract;
import fr.pilato.elasticsearch.crawler.plugins.FsCrawlerPlugin;
import io.minio.GetObjectArgs;
import io.minio.GetObjectResponse;
import io.minio.MinioClient;
import io.minio.errors.*;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.pf4j.Extension;

import java.io.IOException;
import java.io.InputStream;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;

public class FsS3Plugin extends FsCrawlerPlugin {
    private static final Logger logger = LogManager.getLogger(FsS3Plugin.class);

    @Override
    protected String getName() {
        return "fs-s3";
    }

    @Extension
    public static class FsCrawlerExtensionFsProviderS3 extends FsCrawlerExtensionFsProviderAbstract {

        private MinioClient minioClient;
        private String bucket;
        private String url;
        private String accesKey;
        private String secretKey;
        private String object;

        @Override
        public String getType() {
            return "s3";
        }

        @Override
        protected void parseSettings() throws PathNotFoundException {
            bucket = document.read("$.s3.bucket");
            object = document.read("$.s3.object");
            url = document.read("$.s3.url");
            accesKey = document.read("$.s3.access_key");
            secretKey = document.read("$.s3.secret_key");
        }

        @Override
        public void start() {
            minioClient = MinioClient.builder()
                    .endpoint(url)
                    .credentials(accesKey, secretKey)
                    .build();
        }

        @Override
        public void stop() throws Exception {
            logger.debug("Closing FsCrawlerExtensionFsProviderS3");
            if (minioClient != null) {
                logger.debug("Closing minioClient");
                minioClient.close();
                minioClient = null;
            }
        }

        @Override
        public InputStream readFile() throws IOException {
            logger.debug("Reading S3 file [{}] from bucket [{}/{}]", object, url, bucket);
            GetObjectArgs getObjectArgs = GetObjectArgs.builder()
                    .bucket(bucket)
                    .object(object)
                    .build();
            try {
                return minioClient.getObject(getObjectArgs);
            } catch (MinioException | InvalidKeyException | NoSuchAlgorithmException e) {
                logger.debug("Failed to read file", e);
                throw new FsCrawlerIllegalConfigurationException(e.getMessage());
            }
        }

        @Override
        public String getFilename() {
            return object;
        }

        @Override
        public long getFilesize() throws IOException {
            logger.debug("Reading S3 filesize for file [{}] from bucket [{}/{}]", object, url, bucket);
            GetObjectArgs getObjectArgs = GetObjectArgs.builder()
                    .bucket(bucket)
                    .object(object)
                    .build();
            try (GetObjectResponse response = minioClient.getObject(getObjectArgs)) {
                return response.headers().byteCount();
            } catch (MinioException | InvalidKeyException | NoSuchAlgorithmException e) {
                logger.debug("Failed to read file", e);
                throw new FsCrawlerIllegalConfigurationException(e.getMessage());
            }
        }
    }
}
