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
package fr.pilato.elasticsearch.crawler.plugins.fs.ssh;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.sshd.common.config.keys.writer.openssh.OpenSSHKeyPairResourceWriter;

import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.file.Path;
import java.security.GeneralSecurityException;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.NoSuchAlgorithmException;

/**
 * Utility class for SSH-related test operations.
 */
public final class SshTestHelper {
    private static final Logger logger = LogManager.getLogger();

    private SshTestHelper() {
        // Utility class
    }

    /**
     * Generate an RSA key pair for SSH tests.
     *
     * @return the generated key pair
     * @throws NoSuchAlgorithmException if RSA algorithm is not available
     */
    public static KeyPair generateKeyPair() throws NoSuchAlgorithmException {
        return KeyPairGenerator.getInstance("RSA").generateKeyPair();
    }

    /**
     * Save a key pair to files in the specified directory.
     * Creates two files:
     * <ul>
     *     <li>{@code public.key} - the public key in OpenSSH format</li>
     *     <li>{@code private.key} - the private key in OpenSSH format</li>
     * </ul>
     *
     * @param directory the directory where to save the key files
     * @param keyPair   the key pair to save
     */
    public static void saveKeyPair(Path directory, KeyPair keyPair) {
        OpenSSHKeyPairResourceWriter writer = new OpenSSHKeyPairResourceWriter();

        try (FileOutputStream fos = new FileOutputStream(directory.resolve("public.key").toFile())) {
            writer.writePublicKey(keyPair.getPublic(), "Public Key for tests", fos);
        } catch (GeneralSecurityException | IOException e) {
            logger.error("Failed to save public key", e);
        }

        try (FileOutputStream fos = new FileOutputStream(directory.resolve("private.key").toFile())) {
            writer.writePrivateKey(keyPair, "Private Key for tests", null, fos);
        } catch (GeneralSecurityException | IOException e) {
            logger.error("Failed to save private key", e);
        }
    }

    /**
     * Generate and save a key pair to files in the specified directory.
     * Convenience method that combines {@link #generateKeyPair()} and {@link #saveKeyPair(Path, KeyPair)}.
     *
     * @param directory the directory where to save the key files
     * @return the generated key pair
     * @throws NoSuchAlgorithmException if RSA algorithm is not available
     */
    public static KeyPair generateAndSaveKeyPair(Path directory) throws NoSuchAlgorithmException {
        KeyPair keyPair = generateKeyPair();
        saveKeyPair(directory, keyPair);
        return keyPair;
    }
}
