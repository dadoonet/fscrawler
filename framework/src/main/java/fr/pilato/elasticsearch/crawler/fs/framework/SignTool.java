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
 * Utility class to sign *things*
 *
 * @author David Pilato (aka dadoonet)
 */
public class SignTool {

    /**
     * Signs the given input by computing an MD5 digest of its bytes.
     *
     * <p>The resulting hash is used as the Elasticsearch document {@code _id}. MD5 is kept here only as a
     * non-cryptographic checksum: it is <strong>not</strong> used in a security-sensitive context. Changing the
     * algorithm would change every generated {@code _id} and force a full reindex, so MD5 remains the default for
     * backward compatibility.
     *
     * @param toSign the value to hash (e.g. a file path)
     * @return the hexadecimal MD5 digest of {@code toSign}
     * @throws NoSuchAlgorithmException if the MD5 algorithm is not available
     * @deprecated the hash algorithm should become configurable; this hard-coded MD5 variant is kept for backward
     *     compatibility. See <a href="https://github.com/dadoonet/fscrawler/issues/2425">#2425</a>.
     */
    @Deprecated(since = "2.10")
    @SuppressWarnings("java:S4790") // MD5 is a non-cryptographic checksum used to derive document ids, not for security
    public static String sign(String toSign) throws NoSuchAlgorithmException {

        MessageDigest md = MessageDigest.getInstance("MD5");
        md.update(toSign.getBytes());

        StringBuilder key = new StringBuilder();
        byte[] b = md.digest();
        for (byte aB : b) {
            long t = aB < 0 ? 256 + aB : aB;
            key.append(Long.toHexString(t));
        }

        return key.toString();
    }
}
