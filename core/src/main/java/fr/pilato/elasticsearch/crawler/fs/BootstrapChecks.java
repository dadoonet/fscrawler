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

package fr.pilato.elasticsearch.crawler.fs;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * This class checks at startup if everything is correctly set.
 * We are using a class for it so it's easier to track checks within a single place
 */
public class BootstrapChecks {

    private static final Logger logger = LogManager.getLogger(BootstrapChecks.class);

    public static void check() {
        checkUTF8();
    }

    private static void checkUTF8() {
        String encoding = System.getProperty("file.encoding");
        if (!encoding.equals("UTF-8")) {
            logger.warn("[file.encoding] should be [{}] but is [{}]", "UTF-8", encoding);
        }
    }

}
