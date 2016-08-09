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

package fr.pilato.elasticsearch.crawler.fs.util;

public class OsValidator {
    public static final String OS;
    public static final boolean windows;
    public static final boolean mac;
    public static final boolean unix;
    public static final boolean solaris;

    static {
        OS = System.getProperty("os.name").toLowerCase();
        windows = (OS.indexOf("win") >= 0);
        mac = (OS.indexOf("mac") >= 0);
        unix = (OS.indexOf("nix") >= 0 || OS.indexOf("nux") >= 0 || OS.indexOf("aix") > 0 );
        solaris = (OS.indexOf("sunos") >= 0);
    }
}
