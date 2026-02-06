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

package fr.pilato.elasticsearch.crawler.plugins.pipeline.service;

import fr.pilato.elasticsearch.crawler.plugins.FsCrawlerPluginException;
import org.junit.Test;

import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests for {@link AbstractServicePlugin} lifecycle and enabled default.
 */
public class AbstractServicePluginTest {

    /** Concrete stub for testing. */
    private static final class TestServicePlugin extends AbstractServicePlugin {
        private int startCount;
        private int stopCount;

        @Override
        public String getType() {
            return "test-service";
        }

        @Override
        protected void configureTypeSpecific(Map<String, Object> typeConfig) {
            // No type-specific config for test
        }

        @Override
        protected void doStart() throws FsCrawlerPluginException {
            startCount++;
        }

        @Override
        protected void doStop() throws FsCrawlerPluginException {
            stopCount++;
        }

        int getStartCount() {
            return startCount;
        }

        int getStopCount() {
            return stopCount;
        }
    }

    @Test
    public void defaultEnabledIsFalse() {
        TestServicePlugin plugin = new TestServicePlugin();
        plugin.setId("test-1");
        assertThat(plugin.isEnabled()).isFalse();
    }

    @Test
    public void configureWithEnabledTrue() {
        TestServicePlugin plugin = new TestServicePlugin();
        plugin.setId("test-1");
        Map<String, Object> config = new HashMap<>();
        config.put("enabled", true);
        plugin.configure(config, null);
        assertThat(plugin.isEnabled()).isTrue();
    }

    @Test
    public void configureWithEnabledFalse() {
        TestServicePlugin plugin = new TestServicePlugin();
        plugin.setId("test-1");
        Map<String, Object> config = new HashMap<>();
        config.put("enabled", false);
        plugin.configure(config, null);
        assertThat(plugin.isEnabled()).isFalse();
    }

    @Test
    public void startThenStopUpdatesRunning() throws FsCrawlerPluginException {
        TestServicePlugin plugin = new TestServicePlugin();
        plugin.setId("test-1");
        assertThat(plugin.isRunning()).isFalse();

        plugin.start();
        assertThat(plugin.isRunning()).isTrue();
        assertThat(plugin.getStartCount()).isEqualTo(1);

        plugin.stop();
        assertThat(plugin.isRunning()).isFalse();
        assertThat(plugin.getStopCount()).isEqualTo(1);
    }

    @Test
    public void doubleStartIsIdempotent() throws FsCrawlerPluginException {
        TestServicePlugin plugin = new TestServicePlugin();
        plugin.setId("test-1");
        plugin.start();
        plugin.start();
        assertThat(plugin.isRunning()).isTrue();
        assertThat(plugin.getStartCount()).isEqualTo(1);
        plugin.stop();
    }

    @Test
    public void stopWithoutStartIsNoOp() throws FsCrawlerPluginException {
        TestServicePlugin plugin = new TestServicePlugin();
        plugin.setId("test-1");
        plugin.stop();
        assertThat(plugin.isRunning()).isFalse();
        assertThat(plugin.getStopCount()).isEqualTo(0);
    }
}
