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
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;

class DigestsTest extends AbstractFSCrawlerTestCase {

    @Test
    void isSupportedKnownAlgorithms() {
        Assertions.assertThat(Digests.isSupported("MD5")).isTrue();
        Assertions.assertThat(Digests.isSupported("SHA-256")).isTrue();
    }

    @Test
    void isSupportedUnknownAlgorithm() {
        Assertions.assertThat(Digests.isSupported("NOT-A-REAL-DIGEST")).isFalse();
    }

    @Test
    void isSupportedNull() {
        Assertions.assertThat(Digests.isSupported(null)).isFalse();
    }

    @Test
    void getReturnsInstance() throws NoSuchAlgorithmException {
        MessageDigest md = Digests.get("SHA-256");
        Assertions.assertThat(md.getAlgorithm()).isEqualTo("SHA-256");
    }

    @Test
    void getUnknownThrows() {
        Assertions.assertThatThrownBy(() -> Digests.get("NOT-A-REAL-DIGEST"))
                .isInstanceOf(NoSuchAlgorithmException.class);
    }

    @Test
    void getOrNullWithNullReturnsNull() {
        Assertions.assertThat(Digests.getOrNull(null)).isNull();
    }

    @Test
    void getOrNullWithAlgorithmReturnsInstance() {
        MessageDigest md = Digests.getOrNull("MD5");
        Assertions.assertThat(md).isNotNull();
        Assertions.assertThat(md.getAlgorithm()).isEqualTo("MD5");
    }

    @Test
    void getOrNullUnknownThrowsUnchecked() {
        Assertions.assertThatThrownBy(() -> Digests.getOrNull("NOT-A-REAL-DIGEST"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasCauseInstanceOf(NoSuchAlgorithmException.class);
    }

    @Test
    void toHexStandardPadding() {
        byte[] bytes = new byte[] {0x0A, (byte) 0xFF, 0x00};
        Assertions.assertThat(Digests.toHex(bytes)).isEqualTo("0aff00");
    }
}
