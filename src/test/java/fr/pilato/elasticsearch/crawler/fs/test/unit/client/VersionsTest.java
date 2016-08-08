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

package fr.pilato.elasticsearch.crawler.fs.test.unit.client;

import fr.pilato.elasticsearch.crawler.fs.client.VersionComparator;
import fr.pilato.elasticsearch.crawler.fs.test.AbstractFSCrawlerTestCase;
import org.junit.Test;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;

public class VersionsTest extends AbstractFSCrawlerTestCase {

    @Test
    public void testVersions() {
        versionTester("5.0.0-alpha5", "5.0.0", -1);
        versionTester("1.7.3", "5.0.0", -1);
        versionTester("5.1.0", "5.0.0", 1);
        versionTester("5.0.0-alpha5", "5.0.0-SNAPSHOT", 14);
        versionTester("5.0.0-alpha5-SNAPSHOT", "5.0.0-SNAPSHOT", 14);
        versionTester("5.0.0-beta1-SNAPSHOT", "5.0.0", -1);
        versionTester("5.0.0-beta1", "5.0.0", -1);
        versionTester("5.0.0-beta1", "5", 1);
        versionTester("5.0.0", "5", 0);
        versionTester("5.0.0-alpha1-SNAPSHOT", "5", 1);
        versionTester("5.0.0-alpha5", "2.3.3", 1);
    }

    private void versionTester(String a, String b, int expected) {
        int i = new VersionComparator().compare(a, b);
        logger.info("{} {} {}", a, i < 0 ? "<" : i > 0 ? ">" : "=", b);
        assertThat(i, is(expected));
    }

}
