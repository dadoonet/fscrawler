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

import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

/**
 * Utility class to sign *things* (typically file paths used as Elasticsearch document {@code _id}s).
 *
 * @author David Pilato (aka dadoonet)
 */
public class SignTool {

    /** Default algorithm for document ids when {@code fs.hash_algorithm} is unset (backward compatible). */
    public static final String DEFAULT_ALGORITHM = "MD5";

    private SignTool() {
        // Utility class, do not instantiate
    }

    /**
     * Signs the given input by computing an MD5 digest of its bytes using the legacy encoding.
     *
     * <p>Prefer {@link #sign(String, String)} with {@code fs.hash_algorithm}.
     *
     * @param toSign the value to hash (e.g. a file path)
     * @return the hexadecimal MD5 digest of {@code toSign}
     * @throws NoSuchAlgorithmException if the MD5 algorithm is not available
     * @deprecated use {@link #sign(String, String)} with a configurable algorithm; this hard-coded MD5 variant is kept
     *     for backward compatibility. See <a href="https://github.com/dadoonet/fscrawler/issues/2425">#2425</a>.
     */
    @Deprecated(since = "3.0")
    public static String sign(String toSign) throws NoSuchAlgorithmException {
        return sign(DEFAULT_ALGORITHM, toSign);
    }

    /**
     * Signs {@code toSign} with the given {@link MessageDigest} algorithm.
     *
     * <p>For {@code MD5}, bytes are taken with the platform default charset and encoded with the historical non-padded
     * hex format so existing document {@code _id}s stay stable. For any other algorithm, bytes are UTF-8 and the hex
     * form is standard zero-padded lowercase.
     *
     * @param algorithm digest algorithm name (e.g. {@code MD5}, {@code SHA-256})
     * @param toSign the value to hash (e.g. a file path)
     * @return hexadecimal digest used as document {@code _id}
     * @throws NoSuchAlgorithmException if {@code algorithm} is not available
     */
    @SuppressWarnings("java:S4790") // MD5 is a non-cryptographic checksum used to derive document ids, not for security
    public static String sign(String algorithm, String toSign) throws NoSuchAlgorithmException {
        boolean legacyMd5 = DEFAULT_ALGORITHM.equalsIgnoreCase(algorithm);
        Charset charset = legacyMd5 ? Charset.defaultCharset() : StandardCharsets.UTF_8;

        MessageDigest md = Digests.get(algorithm);
        md.update(toSign.getBytes(charset));
        byte[] digest = md.digest();

        if (legacyMd5) {
            return toLegacyHex(digest);
        }
        return Digests.toHex(digest);
    }

    /**
     * Historical FSCrawler hex encoding: no zero-padding ({@code 0x0A} → {@code "a"}), unsigned via {@code 256 + b} for
     * negative bytes.
     */
    private static String toLegacyHex(byte[] digest) {
        StringBuilder key = new StringBuilder();
        for (byte aB : digest) {
            long t = aB < 0 ? 256 + aB : aB;
            key.append(Long.toHexString(t));
        }
        return key.toString();
    }
}
