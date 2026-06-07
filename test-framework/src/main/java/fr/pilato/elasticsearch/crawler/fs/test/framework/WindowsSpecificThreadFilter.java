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

public class WindowsSpecificThreadFilter implements Predicate<Thread> {
    @Override
    public boolean test(Thread t) {
        if (!System.getProperty("os.name").toLowerCase().contains("win")) {
            return false;
        }
        // Exclude TGRP- threads
        if (t.getThreadGroup() != null && t.getThreadGroup().getName().startsWith("TGRP-")) {
            return true;
        }
        // Exclude Apache HttpClient5 connector threads (Thread-N) that may leak on Windows
        // These are Iocp threads that can take several seconds to shut down
        return t.getName().matches("Thread-\\d+")
                && t.getThreadGroup() != null
                && "main".equals(t.getThreadGroup().getName());
    }
}
