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
package fr.pilato.elasticsearch.crawler.fs.settings;

import fr.pilato.elasticsearch.crawler.fs.framework.ByteSizeValue;
import fr.pilato.elasticsearch.crawler.fs.framework.FsCrawlerIllegalConfigurationException;
import fr.pilato.elasticsearch.crawler.fs.framework.TimeValue;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import org.github.gestalt.config.annotations.Config;

/** Independent recovery boundaries; ordinary Elasticsearch bulk thresholds do not change these values. */
public class Checkpoint {
    @Config(defaultVal = "100")
    private int maxDirectories = 100;

    @Config(defaultVal = "5000")
    private long maxOperations = 5000;

    @Config(defaultVal = "32mb")
    private ByteSizeValue maxBytes = new ByteSizeValue("32mb");

    @Config(defaultVal = "30s")
    private TimeValue maxInterval = new TimeValue("30s");

    public int getMaxDirectories() {
        return maxDirectories;
    }

    public void setMaxDirectories(int value) {
        maxDirectories = value;
    }

    public long getMaxOperations() {
        return maxOperations;
    }

    public void setMaxOperations(long value) {
        maxOperations = value;
    }

    public ByteSizeValue getMaxBytes() {
        return maxBytes;
    }

    public void setMaxBytes(ByteSizeValue value) {
        maxBytes = value;
    }

    public TimeValue getMaxInterval() {
        return maxInterval;
    }

    public void setMaxInterval(TimeValue value) {
        maxInterval = value;
    }

    public void validate() {
        if (maxDirectories <= 0) invalid("max_directories", "a positive integer");
        if (maxOperations <= 0) invalid("max_operations", "a positive integer");
        if (maxBytes == null || maxBytes.getBytes() <= 0) invalid("max_bytes", "a positive byte size with a unit");
        if (maxInterval == null || maxInterval.duration() <= 0 || maxInterval.timeUnit() == null)
            invalid("max_interval", "a positive duration with a unit");
        try {
            Math.multiplyExact(maxInterval.duration(), maxInterval.timeUnit().toNanos(1));
        } catch (ArithmeticException e) {
            invalid("max_interval", "a duration representable in signed 64-bit nanoseconds");
        }
    }

    private static void invalid(String field, String expected) {
        throw new FsCrawlerIllegalConfigurationException("Setting [fs.checkpoint." + field + "] must be " + expected);
    }

    /** Warnings describe a valid configuration's effect; they never rewrite explicit limits. */
    public List<String> validationWarnings(Elasticsearch bulk) {
        List<String> warnings = new ArrayList<>();
        if (bulk == null) return warnings;
        if (bulk.getBulkSize() > 0 && maxOperations < bulk.getBulkSize())
            warnings.add(
                    "fs.checkpoint.max_operations is below elasticsearch.bulk_size; checkpoints can force partial bulk requests");
        if (bulk.getByteSize() != null
                && bulk.getByteSize().getBytes() > 0
                && maxBytes.getBytes() < bulk.getByteSize().getBytes())
            warnings.add(
                    "fs.checkpoint.max_bytes is below elasticsearch.byte_size; checkpoints can force partial bulk requests");
        if (bulk.getFlushInterval() != null
                && bulk.getFlushInterval().nanos() > 0
                && maxInterval.nanos() < bulk.getFlushInterval().nanos())
            warnings.add(
                    "fs.checkpoint.max_interval is shorter than elasticsearch.flush_interval; checkpoints can flush pending work sooner");
        return warnings;
    }

    private static Long intervalNanos(TimeValue interval) {
        return interval == null ? null : interval.nanos();
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) return true;
        if (!(other instanceof Checkpoint cp)) return false;
        return maxDirectories == cp.maxDirectories
                && maxOperations == cp.maxOperations
                && Objects.equals(maxBytes, cp.maxBytes)
                && Objects.equals(intervalNanos(maxInterval), intervalNanos(cp.maxInterval));
    }

    @Override
    public int hashCode() {
        return Objects.hash(maxDirectories, maxOperations, maxBytes, intervalNanos(maxInterval));
    }

    @Override
    public String toString() {
        return "Checkpoint{maxDirectories=" + maxDirectories + ", maxOperations=" + maxOperations + ", maxBytes="
                + maxBytes + ", maxInterval=" + maxInterval + "}";
    }
}
