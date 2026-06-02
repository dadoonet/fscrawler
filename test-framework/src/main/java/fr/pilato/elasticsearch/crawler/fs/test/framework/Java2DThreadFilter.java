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

/**
 * Filters AWT/Java2D JDK-internal threads created when PDFBox (via Apache Tika) initialises the Java2D/AWT subsystem to
 * render PDF fonts or images.
 *
 * <ul>
 *   <li>{@code Java2D Disposer} — garbage-collects native graphics resources (sun.java2d)
 *   <li>{@code AppKit Thread} — macOS native UI event loop (sun.lwawt.macosx), macOS only
 *   <li>{@code AWT-*} — AWT event dispatch and toolkit threads
 * </ul>
 *
 * All are JVM-lifetime system threads — false positives for leak detection.
 */
public class Java2DThreadFilter implements Predicate<Thread> {
    public boolean test(Thread t) {
        return "Java2D Disposer".equals(t.getName())
                || "AppKit Thread".equals(t.getName())
                || t.getName().startsWith("AWT-");
    }
}
