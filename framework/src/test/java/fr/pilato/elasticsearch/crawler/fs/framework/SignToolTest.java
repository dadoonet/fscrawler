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

import fr.pilato.elasticsearch.crawler.fs.test.framework.AbstractFSCrawlerTestCase;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;

class SignToolTest extends AbstractFSCrawlerTestCase {

    @Test
    void signMd5LegacyCompat() throws NoSuchAlgorithmException {
        String signature = SignTool.sign("ABCD");
        Assertions.assertThat(signature).isEqualTo("cb8ca4a7bb5f9683c19133a84872ca7");
        Assertions.assertThat(SignTool.sign("MD5", "ABCD")).isEqualTo(signature);
    }

    @Test
    void signSha256Utf8StandardHex() throws NoSuchAlgorithmException {
        MessageDigest expected = MessageDigest.getInstance("SHA-256");
        expected.update("ABCD".getBytes(StandardCharsets.UTF_8));
        String expectedHex = Digests.toHex(expected.digest());

        Assertions.assertThat(SignTool.sign("SHA-256", "ABCD")).isEqualTo(expectedHex);
        Assertions.assertThat(expectedHex).hasSize(64);
    }
}
