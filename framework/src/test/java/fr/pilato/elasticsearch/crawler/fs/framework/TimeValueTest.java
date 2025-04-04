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

import static org.assertj.core.api.Assertions.assertThat;

public class TimeValueTest {

    @Test
    public void timeValueFromString() {
        assertThat(TimeValue.parseTimeValue("1s").millis()).isEqualTo(1000L);
        assertThat(TimeValue.parseTimeValue("1s").seconds()).isEqualTo(1L);
        assertThat(TimeValue.parseTimeValue("10s").seconds()).isEqualTo(10L);
        assertThat(TimeValue.parseTimeValue("1m").seconds()).isEqualTo(60L);
        assertThat(TimeValue.parseTimeValue("1m").minutes()).isEqualTo(1L);
        assertThat(TimeValue.parseTimeValue("1h").minutes()).isEqualTo(60L);
        assertThat(TimeValue.parseTimeValue("1h").hours()).isEqualTo(1L);
        assertThat(TimeValue.parseTimeValue("1d").hours()).isEqualTo(24L);
        assertThat(TimeValue.parseTimeValue("1d").days()).isEqualTo(1L);
        assertThat(TimeValue.parseTimeValue("2d").days()).isEqualTo(2L);
        assertThat(TimeValue.parseTimeValue("30d").days()).isEqualTo(30L);
    }

    @Test
    public void timeValueToString() {
        assertThat(TimeValue.parseTimeValue("1s").toString()).isEqualTo("1s");
        assertThat(TimeValue.parseTimeValue("10s").toString()).isEqualTo("10s");
        assertThat(TimeValue.parseTimeValue("1m").toString()).isEqualTo("1m");
        assertThat(TimeValue.parseTimeValue("70s").toString()).isEqualTo("1.1m");
        assertThat(TimeValue.parseTimeValue("1h").toString()).isEqualTo("1h");
        assertThat(TimeValue.parseTimeValue("75m").toString()).isEqualTo("1.2h");
        assertThat(TimeValue.parseTimeValue("3830s").toString()).isEqualTo("1h");
        assertThat(TimeValue.parseTimeValue("1d").toString()).isEqualTo("1d");
        assertThat(TimeValue.parseTimeValue("25h").toString()).isEqualTo("1d");
        assertThat(TimeValue.parseTimeValue("2d").toString()).isEqualTo("2d");
        assertThat(TimeValue.parseTimeValue("30d").toString()).isEqualTo("30d");
    }
}
