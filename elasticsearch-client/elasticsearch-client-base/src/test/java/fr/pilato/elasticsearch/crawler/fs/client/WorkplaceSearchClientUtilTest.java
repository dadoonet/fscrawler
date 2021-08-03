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

package fr.pilato.elasticsearch.crawler.fs.client;

import fr.pilato.elasticsearch.crawler.fs.framework.FsCrawlerUtil;
import fr.pilato.elasticsearch.crawler.fs.test.framework.AbstractFSCrawlerTestCase;
import org.junit.Test;

import java.text.DateFormat;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.time.ZoneId;
import java.util.Date;
import java.util.Locale;
import java.util.TimeZone;

import static com.carrotsearch.randomizedtesting.RandomizedTest.*;
import static fr.pilato.elasticsearch.crawler.fs.client.WorkplaceSearchClientUtil.generateDefaultCustomSourceName;
import static fr.pilato.elasticsearch.crawler.fs.client.WorkplaceSearchClientUtil.toRFC3339;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.*;

public class WorkplaceSearchClientUtilTest extends AbstractFSCrawlerTestCase {

    @Test
    public void testNameGeneration() {
        String suffix = randomAsciiAlphanumOfLength(randomIntBetween(0, 48));
        String name = generateDefaultCustomSourceName(suffix);
        assertThat(name, is("Local files for " + suffix));
    }

    @Test
    public void testNameGenerationAboveLimit() {
        String max = randomAsciiAlphanumOfLength(48);
        String suffix = max + randomAsciiAlphanumOfLength(randomIntBetween(1, 10));
        String name = generateDefaultCustomSourceName(suffix);
        assertThat(name, is("Local files for " + max));
    }

    @Test
    public void testRFC3339() throws ParseException {
        assertThat(toRFC3339(new Date()), notNullValue());
        assertThat(toRFC3339(new SimpleDateFormat("dd/MM/yyyy").parse("26/12/1971")), startsWith("1971-12-26T00:00:00"));
        assertThat(toRFC3339(null), nullValue());
    }
}
