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

package fr.pilato.elasticsearch.crawler.fs.settings;

import fr.pilato.elasticsearch.crawler.fs.framework.FsCrawlerUtil;
import fr.pilato.elasticsearch.crawler.fs.framework.OsValidator;
import org.apache.logging.log4j.Logger;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

public class FsCrawlerValidator {

    /**
     * Check if settings are valid. Note that settings can be updated by this method (fallback to defaults if not set)
     * @param logger    Needed to print warn/errors or info
     * @param settings  Settings we want to check
     * @return true if we found fatal errors and should prevent from starting
     */
    public static boolean validateSettings(Logger logger, FsSettings settings) {
        // V2 format doesn't have elasticsearch settings in global config (they're in plugin config)
        if (settings.getElasticsearch() != null) {
            if (settings.getElasticsearch().getUsername() != null || settings.getElasticsearch().getPassword() != null) {
                logger.warn("username/password is deprecated. Use apiKey instead.");
            }
        }

        // Checking protocol (V1 format only)
        if (settings.getServer() != null) {
            if (!Server.PROTOCOL.LOCAL.equals(settings.getServer().getProtocol()) &&
                    !Server.PROTOCOL.SSH.equals(settings.getServer().getProtocol()) && !Server.PROTOCOL.FTP.equals(settings.getServer().getProtocol())) {
                // Non supported protocol
                logger.error(settings.getServer().getProtocol() + " is not supported yet. Please use " +
                        Server.PROTOCOL.LOCAL + " or " + Server.PROTOCOL.SSH + " or " + Server.PROTOCOL.FTP + ". Disabling crawler");
                return true;
            }

            // Checking username/password
            if (Server.PROTOCOL.SSH.equals(settings.getServer().getProtocol()) &&
                    FsCrawlerUtil.isNullOrEmpty(settings.getServer().getUsername())) {
                logger.error("When using SSH, you need to set a username and probably a password or a pem file. Disabling crawler");
                return true;
            } else if (Server.PROTOCOL.FTP.equals(settings.getServer().getProtocol()) &&
                FsCrawlerUtil.isNullOrEmpty(settings.getServer().getUsername())) {
                logger.error("When using FTP, you need to set a username and probably a password. Disabling crawler");
                return true;
            }
        }

        // V2 format doesn't have fs settings in global config (they're in plugin config)
        if (settings.getFs() != null) {
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

            // Checking That we don't try to have xml or json, but we don't want to index their content
            if ((settings.getFs().isJsonSupport() || settings.getFs().isXmlSupport()) && !settings.getFs().isIndexContent()) {
                logger.error("Json or Xml indexation is activated but you disabled indexing content which does not make sense. Disabling crawler");
                return true;
            }

            // We just warn the user if he is running on Windows but want to get attributes
            if (OsValidator.WINDOWS && settings.getFs().isAttributesSupport()) {
                logger.info("attributes_support is set to true but getting group is not available on [{}].", OsValidator.OS);
            }

            if (settings.getFs().isAclSupport() && !settings.getFs().isAttributesSupport()) {
                logger.warn("acl_support is set to true but attributes_support is false. ACL collection is disabled.");
            }
        }

        return false;
    }
}
