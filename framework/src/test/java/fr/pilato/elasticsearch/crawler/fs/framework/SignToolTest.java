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
import java.security.NoSuchAlgorithmException;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;

class SignToolTest extends AbstractFSCrawlerTestCase {

    @Test
    void signMd5LegacyCompat() throws NoSuchAlgorithmException {
        Assertions.assertThat(SignTool.sign(SignTool.LEGACY_MD5_ALGORITHM, "ABCD"))
                .isEqualTo("cb8ca4a7bb5f9683c19133a84872ca7");
    }

    @Test
    void signSha256Utf8StandardHex() throws NoSuchAlgorithmException {
        Assertions.assertThat(SignTool.sign("SHA-256", "ABCD"))
                .isEqualTo("e12e115acf4552b2568b55e93cbd39394c4ef81c82447fafc997882a02d23677")
                .hasSize(64);
    }
}
