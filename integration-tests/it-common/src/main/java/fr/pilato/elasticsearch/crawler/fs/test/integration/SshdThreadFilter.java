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

package fr.pilato.elasticsearch.crawler.fs.test.integration;

import com.carrotsearch.randomizedtesting.ThreadFilter;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class SshdThreadFilter implements ThreadFilter {
    protected final Logger logger = LogManager.getLogger(SshdThreadFilter.class);

    @Override
    public boolean reject(Thread t) {
        boolean isSshdThread = t.getName().startsWith("sshd-SshClient");
        if (isSshdThread) {
            logger.warn("We have a zombie thread for sshd-SshClient: {} with this loader {}. " +
                    "See https://github.com/dadoonet/fscrawler/pull/1225 for more information.", t, t.getContextClassLoader());
        } else if (t.getThreadGroup() != null) {
            isSshdThread = t.getName().startsWith("Thread-") && (
                    t.getThreadGroup().getName().startsWith("TGRP-FsCrawler") ||
                            t.getThreadGroup().getName().startsWith("TGRP-WPSearch"));
            if (isSshdThread) {
                logger.warn("We have a zombie thread for {}: {}  with this loader {}. " +
                                "See https://github.com/dadoonet/fscrawler/pull/1225 for more information.", t.getThreadGroup().getName(), t,
                        t.getContextClassLoader());
            }
        }
        return isSshdThread;
    }
}
