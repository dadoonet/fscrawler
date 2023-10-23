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
package fr.pilato.elasticsearch.crawler.fs.test.framework;

import org.hamcrest.Matchers;
import org.junit.Test;

import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.is;

public class AbstractFSCrawlerTestCaseTest extends AbstractFSCrawlerTestCase {

    @SuppressWarnings("ConstantConditions")
    @Test
    public void testExpectThrows() {
        NullPointerException npe = expectThrows(NullPointerException.class, () -> {
            throw new NullPointerException("NPE");
        });
        assertThat(npe.getMessage(), is("NPE"));
        AssertionError assertionError = expectThrows(AssertionError.class, () -> expectThrows(NullPointerException.class, () -> {
            throw new RuntimeException("RTE");
        }));
        assertThat(assertionError.getMessage(), containsString("Expected: an instance of " + NullPointerException.class.getName()));
    }

    @Test
    public void testSimulateElasticsearchException() throws InterruptedException {
        AtomicLong l = new AtomicLong(-1);

        long hits = awaitBusy(() -> {
            // Let's search for entries
            try {
                throw new RuntimeException("foo bar");
            } catch (RuntimeException e) {
                staticLogger.warn("error caught", e);
                return l.getAndIncrement();
            }
        }, null, 1, TimeUnit.SECONDS);

        assertThat(hits, Matchers.greaterThan(-1L));
    }

}
