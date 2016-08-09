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

package fr.pilato.elasticsearch.crawler.fs;

import fr.pilato.elasticsearch.crawler.fs.FsCrawlerImpl.PROTOCOL;
import fr.pilato.elasticsearch.crawler.fs.meta.settings.Elasticsearch;
import fr.pilato.elasticsearch.crawler.fs.meta.settings.Fs;
import fr.pilato.elasticsearch.crawler.fs.meta.settings.FsSettings;
import fr.pilato.elasticsearch.crawler.fs.util.OsValidator;
import org.apache.logging.log4j.Logger;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

import static fr.pilato.elasticsearch.crawler.fs.FsCrawlerImpl.hasText;

public class FsCrawlerValidator {

    /**
     * Check if settings are valid. Note that settings can be updated by this method (fallback to defaults if not set)
     * @param logger    Needed to print warn/errors or info
     * @param settings  Settings we want to check
     * @return true if we found fatal errors and should prevent from starting
     */
    public static boolean validateSettings(Logger logger, FsSettings settings) {
        if (settings.getFs() == null || settings.getFs().getUrl() == null) {
            logger.warn("`url` is not set. Please define it. Falling back to default: [{}].", Fs.DEFAULT_DIR);
            if (settings.getFs() == null) {
                settings.setFs(Fs.DEFAULT);
            } else {
                settings.getFs().setUrl(Fs.DEFAULT_DIR);
            }
        }

        if (settings.getElasticsearch() == null) {
            settings.setElasticsearch(Elasticsearch.DEFAULT);
        }
        if (settings.getElasticsearch().getIndex() == null) {
            // When index is not set, we fallback to the config name
            settings.getElasticsearch().setIndex(settings.getName());
        }

        // Checking protocol
        if (settings.getServer() != null) {
            if (!PROTOCOL.LOCAL.equals(settings.getServer().getProtocol()) &&
                    !PROTOCOL.SSH.equals(settings.getServer().getProtocol())) {
                // Non supported protocol
                logger.error(settings.getServer().getProtocol() + " is not supported yet. Please use " +
                        PROTOCOL.LOCAL + " or " + PROTOCOL.SSH + ". Disabling crawler");
                return true;
            }

            // Checking username/password
            if (PROTOCOL.SSH.equals(settings.getServer().getProtocol()) &&
                    !hasText(settings.getServer().getUsername())) {
                // Non supported protocol
                logger.error("When using SSH, you need to set a username and probably a password or a pem file. Disabling crawler");
                return true;
            }
        }

        // Checking Checksum Algorithm
        if (settings.getFs().getChecksum() != null) {
            try {
                MessageDigest.getInstance(settings.getFs().getChecksum());
            } catch (NoSuchAlgorithmException e) {
                // Non supported protocol
                logger.error("Algorithm [{}] not found. Disabling crawler", settings.getFs().getChecksum());
                return true;
            }
        }

        // Checking That we don't try to do both xml and json
        if (settings.getFs().isJsonSupport() && settings.getFs().isXmlSupport()) {
            logger.error("Can not support both xml and json parsing. Disabling crawler");
            return true;
        }

        // We just warn the user if he is running on windows but want to get attributes
        if (OsValidator.windows && settings.getFs().isAttributesSupport()) {
            logger.info("attributes_support is set to true but getting group is not available on [{}].", OsValidator.OS);
        }

        return false;
    }

}
