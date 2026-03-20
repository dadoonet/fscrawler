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

import com.carrotsearch.randomizedtesting.RandomizedTest;
import fr.pilato.elasticsearch.crawler.fs.test.framework.AbstractFSCrawlerTestCase;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.assertj.core.api.Assertions;
import org.junit.Test;

public class ByteSizeValueTest extends AbstractFSCrawlerTestCase {
    private static final Logger logger = LogManager.getLogger();

    @Test
    public void byteSizeConversion() {
        testConversionBothWays("100mb", 104857600L);
        testConversionBothWays("1mb", 1048576L);
        testConversionBothWays("10kb", 10240L);
        testConversionBothWays("1b", 1L);

        // Test 500 random values
        for (int i = 0; i < 500; i++) {
            int unitNumber = RandomizedTest.randomIntBetween(0, 5);
            ByteSizeUnit unit =
                    switch (unitNumber) {
                        case 1 -> ByteSizeUnit.KB;
                        case 2 -> ByteSizeUnit.MB;
                        case 3 -> ByteSizeUnit.GB;
                        case 4 -> ByteSizeUnit.TB;
                        case 5 -> ByteSizeUnit.PB;
                        default -> ByteSizeUnit.BYTES;
                    };

            long value = RandomizedTest.randomLongBetween(1, 999);
            String randomByteSize = value + unit.getSuffix();
            testConversionBothWays(randomByteSize, value, unit);
        }
    }

    private void testConversionBothWays(String value, long bytes) {
        logger.debug("Testing [{}]", value);

        ByteSizeValue byteSizeValue = ByteSizeValue.parseBytesSizeValue(value);
        Assertions.assertThat(byteSizeValue).hasToString(value);
        Assertions.assertThat(byteSizeValue.getBytes()).isEqualTo(bytes);
    }

    private void testConversionBothWays(String asString, long size, ByteSizeUnit unit) {
        ByteSizeValue byteSizeValue = new ByteSizeValue(size, unit);
        testConversionBothWays(asString, byteSizeValue.getBytes());
    }
}
