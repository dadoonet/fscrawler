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

package fr.pilato.elasticsearch.crawler.fs.settings;

import java.util.Collections;
import java.util.List;

public class Defaults {
    public static final String DEFAULT_DIR = "/tmp/es";
    public static final List<String> DEFAULT_EXCLUDED = Collections.singletonList("*/~*");
    public static final ServerUrl NODE_DEFAULT = new ServerUrl("https://127.0.0.1:9200");
    public static final String DEFAULT_META_FILENAME = ".meta.yml";
    public static final String URL_DEFAULT = "http://127.0.0.1:8080/fscrawler";

    // To make sur we don't create an instance of this class
    private Defaults() {}
}
