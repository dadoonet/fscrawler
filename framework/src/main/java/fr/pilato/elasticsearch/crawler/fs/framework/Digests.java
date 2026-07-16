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
package fr.pilato.elasticsearch.crawler.fs.framework;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

/**
 * Helpers around {@link MessageDigest} algorithms used for content checksums ({@code fs.checksum}) and document
 * {@code _id} generation ({@code fs.hash_algorithm}).
 */
public final class Digests {

    private Digests() {
        // Utility class, do not instantiate
    }

    /** @return {@code true} if {@code algorithm} is a known {@link MessageDigest} name */
    public static boolean isSupported(String algorithm) {
        if (algorithm == null) {
            return false;
        }
        try {
            MessageDigest.getInstance(algorithm);
            return true;
        } catch (NoSuchAlgorithmException e) {
            return false;
        }
    }

    /**
     * @param algorithm a {@link MessageDigest} algorithm name (must not be {@code null})
     * @return a new digest instance
     * @throws NoSuchAlgorithmException if the algorithm is unknown
     */
    public static MessageDigest get(String algorithm) throws NoSuchAlgorithmException {
        return MessageDigest.getInstance(algorithm);
    }

    /**
     * Like {@link #get(String)} but returns {@code null} when {@code algorithm} is {@code null}. Unknown algorithms
     * raise {@link IllegalArgumentException} (expected after startup validation).
     *
     * @param algorithm digest name, or {@code null} when no digest is requested
     * @return a new digest instance, or {@code null}
     */
    public static MessageDigest getOrNull(String algorithm) {
        if (algorithm == null) {
            return null;
        }
        try {
            return get(algorithm);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalArgumentException(
                    "Algorithm [" + algorithm + "] not found. This should never happen as we checked that previously",
                    e);
        }
    }

    /** Standard zero-padded lowercase hexadecimal encoding of {@code digest} bytes. */
    public static String toHex(byte[] digest) {
        StringBuilder result = new StringBuilder(digest.length * 2);
        for (byte aDigest : digest) {
            result.append(Integer.toString((aDigest & 0xff) + 0x100, 16).substring(1));
        }
        return result.toString();
    }
}
