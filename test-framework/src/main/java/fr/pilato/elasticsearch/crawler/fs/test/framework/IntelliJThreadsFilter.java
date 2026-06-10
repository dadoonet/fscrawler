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
package fr.pilato.elasticsearch.crawler.fs.test.framework;

import java.util.function.Predicate;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/** This filter is only needed when running the tests from IntelliJ */
public class IntelliJThreadsFilter implements Predicate<Thread> {
    private static final Logger LOGGER = LogManager.getLogger();

    public boolean test(Thread t) {
        boolean intellijThreads =
                t.getName().startsWith("JMX server") || t.getName().startsWith("RMI");
        if (intellijThreads) {
            LOGGER.debug(
                    "Detected IntelliJ threads [{}], if you are running the tests from IntelliJ, "
                            + "you can ignore this warning or add [{}] to the thread leak filters",
                    t.getName(),
                    IntelliJThreadsFilter.class.getSimpleName());
        }
        return intellijThreads;
    }
}
