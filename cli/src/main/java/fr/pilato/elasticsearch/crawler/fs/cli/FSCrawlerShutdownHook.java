package fr.pilato.elasticsearch.crawler.fs.cli;/*
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

import fr.pilato.elasticsearch.crawler.fs.FsCrawlerImpl;
import fr.pilato.elasticsearch.crawler.fs.rest.RestServer;

import java.io.IOException;

/**
 * Shutdown hook so we make sure we close everything
 */
class FSCrawlerShutdownHook extends Thread implements Runnable {

    private final FsCrawlerImpl fsCrawler;

    FSCrawlerShutdownHook(FsCrawlerImpl fsCrawler) {
        this.fsCrawler = fsCrawler;
    }

    @Override
    public void run() {
        try {
            fsCrawler.close();
            // Stop the REST Server if needed
            RestServer.close();
        } catch (InterruptedException | IOException e) {
            Thread.currentThread().interrupt();
        }
    }
}
