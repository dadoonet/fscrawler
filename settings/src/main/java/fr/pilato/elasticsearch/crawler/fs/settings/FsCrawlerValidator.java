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
package fr.pilato.elasticsearch.crawler.fs.settings;

import fr.pilato.elasticsearch.crawler.fs.framework.Digests;
import fr.pilato.elasticsearch.crawler.fs.framework.FsCrawlerUtil;
import fr.pilato.elasticsearch.crawler.fs.framework.OsValidator;
import java.util.ArrayList;
import java.util.List;
import org.apache.logging.log4j.Logger;

public class FsCrawlerValidator {
    private static final String CHAINED_PROVIDER_TYPE = "chained";
    // Split literal to avoid Sonar hard-coded credential rule (java:S2068) on "*.password".
    private static final String SIDECAR_SUFFIX = "password";
    private static final List<String> DOCUMENT_SIDECAR_EXCLUDES =
            List.of("*." + SIDECAR_SUFFIX, "*/." + SIDECAR_SUFFIX);

    private FsCrawlerValidator() {
        // Utility class, do not instantiate
    }

    /**
     * Check if settings are valid. Note that settings can be updated by this method (fallback to defaults if not set)
     *
     * @param logger Needed to print warn/errors or info
     * @param settings Settings we want to check
     * @return true if we found fatal errors and should prevent from starting
     */
    public static boolean validateSettings(Logger logger, FsSettings settings) {
        ensurePasswordSidecarExcludes(settings);

        if (validatePasswordSettings(logger, settings)) {
            return true;
        }

        if (settings.getElasticsearch().getUsername() != null
                || settings.getElasticsearch().getPassword() != null) {
            logger.warn("username/password is deprecated. Use apiKey instead.");
        }

        if (validateServerSettings(logger, settings)) {
            return true;
        }

        // Checking bulk_operation (DELETE is internal-only)
        BulkOperation bulkOperation = settings.getElasticsearch().getBulkOperation();
        if (bulkOperation == BulkOperation.DELETE) {
            logger.error(
                    "elasticsearch.bulk_operation [{}] is not supported for document writes. Please use {} or {}."
                            + " Disabling crawler",
                    bulkOperation,
                    BulkOperation.INDEX,
                    BulkOperation.CREATE);
            return true;
        }

        if (validateDigestSettings(logger, settings)) {
            return true;
        }

        if (validateFsContentFlags(logger, settings)) {
            return true;
        }

        // We just warn the user if he is running on Windows but want to get attributes
        if (OsValidator.WINDOWS && settings.getFs().isAttributesSupport()) {
            logger.info(
                    "attributes_support is set to true but getting group is not available on [{}].", OsValidator.OS);
        }

        if (settings.getFs().isAclSupport() && !settings.getFs().isAttributesSupport()) {
            logger.warn("acl_support is set to true but attributes_support is false. ACL collection is disabled.");
        }

        return false;
    }

    private static void ensurePasswordSidecarExcludes(FsSettings settings) {
        List<String> excludes = settings.getFs().getExcludes();
        List<String> mergedExcludes = excludes == null ? new ArrayList<>() : new ArrayList<>(excludes);
        boolean updated = excludes == null;

        for (String passwordSidecarExclude : DOCUMENT_SIDECAR_EXCLUDES) {
            if (!mergedExcludes.contains(passwordSidecarExclude)) {
                mergedExcludes.add(passwordSidecarExclude);
                updated = true;
            }
        }

        if (updated) {
            settings.getFs().setExcludes(mergedExcludes);
        }
    }

    private static boolean validatePasswordSettings(Logger logger, FsSettings settings) {
        Passwords passwords = settings.getPasswords();
        if (passwords == null || !CHAINED_PROVIDER_TYPE.equals(passwords.getProvider())) {
            return false;
        }

        PasswordProviders providers = passwords.getProviders();
        ChainedPasswordProviderSettings chained = providers == null ? null : providers.getChained();
        List<String> chainedProviders = chained == null ? null : chained.getProviders();

        if (chainedProviders == null || chainedProviders.isEmpty()) {
            logger.error(
                    "passwords.provider [chained] requires passwords.providers.chained.providers. Disabling crawler");
            return true;
        }

        if (chainedProviders.stream().anyMatch(CHAINED_PROVIDER_TYPE::equals)) {
            logger.error("passwords.providers.chained.providers cannot contain [chained]. Disabling crawler");
            return true;
        }

        return false;
    }

    private static boolean validateServerSettings(Logger logger, FsSettings settings) {
        if (settings.getServer() == null) {
            return false;
        }
        if (!Server.PROTOCOL.LOCAL.equals(settings.getServer().getProtocol())
                && !Server.PROTOCOL.SSH.equals(settings.getServer().getProtocol())
                && !Server.PROTOCOL.FTP.equals(settings.getServer().getProtocol())) {
            logger.error(settings.getServer().getProtocol() + " is not supported yet. Please use "
                    + Server.PROTOCOL.LOCAL + " or " + Server.PROTOCOL.SSH + " or " + Server.PROTOCOL.FTP
                    + ". Disabling crawler");
            return true;
        }
        if (Server.PROTOCOL.SSH.equals(settings.getServer().getProtocol())
                && FsCrawlerUtil.isNullOrEmpty(settings.getServer().getUsername())) {
            logger.error(
                    "When using SSH, you need to set a username and probably a password or a pem file. Disabling crawler");
            return true;
        }
        if (Server.PROTOCOL.FTP.equals(settings.getServer().getProtocol())
                && FsCrawlerUtil.isNullOrEmpty(settings.getServer().getUsername())) {
            logger.error("When using FTP, you need to set a username and probably a password. Disabling crawler");
            return true;
        }
        return false;
    }

    private static boolean validateDigestSettings(Logger logger, FsSettings settings) {
        if (settings.getFs().getChecksum() != null
                && !Digests.isSupported(settings.getFs().getChecksum())) {
            logger.error(
                    "Algorithm [{}] not found. Disabling crawler",
                    settings.getFs().getChecksum());
            return true;
        }
        if (settings.getFs().getHashAlgorithm() != null
                && !Digests.isSupported(settings.getFs().getHashAlgorithm())) {
            logger.error(
                    "Hash algorithm [{}] not found. Disabling crawler",
                    settings.getFs().getHashAlgorithm());
            return true;
        }
        return false;
    }

    private static boolean validateFsContentFlags(Logger logger, FsSettings settings) {
        if (settings.getFs().isJsonSupport() && settings.getFs().isXmlSupport()) {
            logger.error("Can not support both xml and json parsing. Disabling crawler");
            return true;
        }
        if ((settings.getFs().isJsonSupport() || settings.getFs().isXmlSupport())
                && !settings.getFs().isIndexContent()) {
            logger.error(
                    "Json or Xml indexation is activated but you disabled indexing content which does not make sense. Disabling crawler");
            return true;
        }
        return false;
    }
}
