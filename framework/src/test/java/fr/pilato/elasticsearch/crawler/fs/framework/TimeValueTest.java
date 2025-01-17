/*
 * Licensed to David Pilato under one or more contributor
 * license agreements. See the NOTICE file distributed with
 * this work for additional information regarding copyright
 * ownership. Elasticsearch licenses this file to you under
 * the Apache License, Version 2.0 (the "License"); you may
 * not use this file except in compliance with the License.
 * You may obtain a copy of the License at
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

package fr.pilato.elasticsearch.crawler.fs.framework;

import org.junit.Test;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;

public class TimeValueTest {

    @Test
    public void testFromString() {
        assertThat(TimeValue.parseTimeValue("1s").millis(), is(1000L));
        assertThat(TimeValue.parseTimeValue("1s").seconds(), is(1L));
        assertThat(TimeValue.parseTimeValue("10s").seconds(), is(10L));
        assertThat(TimeValue.parseTimeValue("1m").seconds(), is(60L));
        assertThat(TimeValue.parseTimeValue("1m").minutes(), is(1L));
        assertThat(TimeValue.parseTimeValue("1h").minutes(), is(60L));
        assertThat(TimeValue.parseTimeValue("1h").hours(), is(1L));
        assertThat(TimeValue.parseTimeValue("1d").hours(), is(24L));
        assertThat(TimeValue.parseTimeValue("1d").days(), is(1L));
        assertThat(TimeValue.parseTimeValue("2d").days(), is(2L));
        assertThat(TimeValue.parseTimeValue("30d").days(), is(30L));
    }

    @Test
    public void testToString() {
        assertThat(TimeValue.parseTimeValue("1s").toString(), is("1s"));
        assertThat(TimeValue.parseTimeValue("10s").toString(), is("10s"));
        assertThat(TimeValue.parseTimeValue("1m").toString(), is("1m"));
        assertThat(TimeValue.parseTimeValue("70s").toString(), is("1.1m"));
        assertThat(TimeValue.parseTimeValue("1h").toString(), is("1h"));
        assertThat(TimeValue.parseTimeValue("75m").toString(), is("1.2h"));
        assertThat(TimeValue.parseTimeValue("3830s").toString(), is("1h"));
        assertThat(TimeValue.parseTimeValue("1d").toString(), is("1d"));
        assertThat(TimeValue.parseTimeValue("25h").toString(), is("1d"));
        assertThat(TimeValue.parseTimeValue("2d").toString(), is("2d"));
        assertThat(TimeValue.parseTimeValue("30d").toString(), is("30d"));
    }
}
