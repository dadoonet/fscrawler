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
package fr.pilato.elasticsearch.crawler.fs;

import static org.assertj.core.api.Assertions.assertThat;

import fr.pilato.elasticsearch.crawler.fs.framework.TimeValue;
import fr.pilato.elasticsearch.crawler.fs.settings.FsSettings;
import fr.pilato.elasticsearch.crawler.fs.settings.FsSettingsLoader;
import fr.pilato.elasticsearch.crawler.fs.test.framework.AbstractFSCrawlerTestCase;
import java.util.concurrent.TimeUnit;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.Test;

/**
 * Scheduling isolation tests for {@link FsParser}. These reproduce the parallel-integration-test failures where a
 * crawler performed many back-to-back runs within a single {@code updateRate} window (doc {@code _version} climbing
 * well above the expected value).
 */
class FsParserSchedulingTest extends AbstractFSCrawlerTestCase {

    /**
     * Large enough that the between-runs {@code nextCheck} stays in the future during the observation window. Since
     * {@code nextCheck = runStart - 2s + updateRate} (issue #82), an 8s rate keeps {@code nextCheck} ~6s ahead, so the
     * loop's "nextCheck is in the past" early-break cannot fire and confound the test.
     */
    private static final TimeValue UPDATE_RATE = TimeValue.timeValueSeconds(8);

    /**
     * Builds a REST-only crawler (no fs provider): each run is a no-op that completes immediately and enters the
     * between-runs wait, which is exactly the scheduling path under test — without needing Elasticsearch or a real file
     * system.
     */
    private FsParser newRestOnlyCrawler(String name) {
        FsSettings fsSettings = FsSettingsLoader.load();
        fsSettings.setName(name);
        fsSettings.getFs().setUpdateRate(UPDATE_RATE);
        // Skip Tika parser initialisation; the run is a no-op regardless.
        fsSettings.getFs().setJsonSupport(true);
        // crawlerPlugin == null => REST-only no-op run, then between-runs wait.
        return new FsParser(fsSettings, testTmpDir, null, null, FsCrawlerImpl.LOOP_INFINITE, false, null);
    }

    /**
     * A {@code resume()} of a <em>different</em> job must not wake this crawler's between-runs wait. Before the fix the
     * wait semaphore was JVM-wide static, so any job's resume woke every crawler, causing premature runs under parallel
     * test load (the notification storm).
     */
    @Test
    void resume_of_another_job_does_not_wake_this_crawler() {
        FsParser crawler = newRestOnlyCrawler(jobName);
        FsParser otherJob = newRestOnlyCrawler(jobName + "-other");
        Thread thread = new Thread(crawler, "test-crawler");
        try {
            // FsCrawlerImpl.start() clears the initially-true closed flag before starting the thread.
            crawler.closed.set(false);
            thread.start();

            // Wait until run #1 completed and the crawler is in its between-runs wait.
            Awaitility.await()
                    .atMost(5, TimeUnit.SECONDS)
                    .until(() -> crawler.getRunNumber() >= 1 && crawler.isPaused());
            int runsBeforeStorm = crawler.getRunNumber();

            // Simulate the parallel notification storm: another job resumes repeatedly.
            for (int i = 0; i < 5; i++) {
                otherJob.resume();
                sleepQuietly(150);
            }

            assertThat(crawler.getRunNumber())
                    .as("another job's resume() must not trigger a premature run of this crawler")
                    .isEqualTo(runsBeforeStorm);
        } finally {
            stopQuietly(crawler, thread);
        }
    }

    /**
     * An early wake-up of the between-runs wait (a spurious wake-up, or any stray {@code notifyAll}) must not shorten
     * the {@code updateRate} interval. Before the fix the wait loop credited the <em>requested</em> chunk duration to
     * {@code totalWaitTime} instead of the time actually elapsed, so a single early wake-up made the loop believe
     * {@code updateRate} had passed and it started a premature run.
     */
    @Test
    void spurious_wakeups_do_not_shorten_the_between_runs_interval() {
        FsParser crawler = newRestOnlyCrawler(jobName);
        Thread thread = new Thread(crawler, "test-crawler");
        try {
            crawler.closed.set(false);
            thread.start();

            Awaitility.await()
                    .atMost(5, TimeUnit.SECONDS)
                    .until(() -> crawler.getRunNumber() >= 1 && crawler.isPaused());
            int runsBeforeStorm = crawler.getRunNumber();

            // Simulate stray/spurious wake-ups directly on this crawler's own wait monitor.
            Object monitor = crawler.getSemaphore();
            for (int i = 0; i < 5; i++) {
                synchronized (monitor) {
                    monitor.notifyAll();
                }
                sleepQuietly(150);
            }

            assertThat(crawler.getRunNumber())
                    .as("an early wake-up must not trigger a premature run before updateRate elapses")
                    .isEqualTo(runsBeforeStorm);
        } finally {
            stopQuietly(crawler, thread);
        }
    }

    private static void sleepQuietly(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    /** Stops the crawler thread the same way {@code FsCrawlerImpl.close()} does: close then interrupt. */
    private static void stopQuietly(FsParser crawler, Thread thread) {
        crawler.close();
        thread.interrupt();
        try {
            thread.join(5000);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
