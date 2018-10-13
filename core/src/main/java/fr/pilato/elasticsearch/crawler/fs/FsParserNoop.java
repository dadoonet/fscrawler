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

package fr.pilato.elasticsearch.crawler.fs;

import fr.pilato.elasticsearch.crawler.fs.settings.FsSettings;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class FsParserNoop extends FsParser {
    private static final Logger logger = LogManager.getLogger(FsParserNoop.class);
    private final FsSettings fsSettings;

    public FsParserNoop(FsSettings fsSettings) {
        this.fsSettings = fsSettings;
    }

    @Override
    public void run() {
        closed = false;

        while (!closed) {
            // This is doing nothing
            try {
                logger.debug("Fs crawler is going to sleep for {}", fsSettings.getFs().getUpdateRate());

                // The problem here is that there is no wait to close the thread while we are sleeping.
                // Which leads to Zombie threads in our tests

                synchronized (semaphore) {
                    semaphore.wait(fsSettings.getFs().getUpdateRate().millis());
                    logger.debug("Fs crawler is now waking up again...");
                }
            } catch (InterruptedException e) {
                logger.debug("Fs crawler thread has been interrupted: [{}]", e.getMessage());
                Thread.currentThread().interrupt();
            }
        }
    }
}
