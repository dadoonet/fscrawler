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

package fr.pilato.elasticsearch.crawler;


import fr.pilato.elasticsearch.crawler.fs.util.FsCrawlerUtil;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.junit.BeforeClass;
import org.junit.Test;

import java.net.URISyntaxException;
import java.net.URL;
import java.nio.file.Path;
import java.nio.file.Paths;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.junit.Assert.fail;

public class FsMappingTest {

    private final Logger logger = LogManager.getLogger(FsMappingTest.class);

    protected static Path config;

    @BeforeClass
    public static void computeResourcesDir() throws URISyntaxException {
        URL resource = FsCrawlerUtil.class.getResource("/fr/pilato/elasticsearch/crawler/fs/README.txt");
        if (resource == null) {
            throw new IllegalArgumentException("Test resource not found: " + resource);
        }
        config = Paths.get(resource.toURI()).getParent();
    }

    @Test
    public void fs_mapping_for_files_version_2() throws Exception {
        String mapping = FsCrawlerUtil.readMapping(config, "2", FsCrawlerUtil.INDEX_TYPE_DOC);
        logger.info("Mapping used for files : " + mapping);
    }

    @Test
    public void fs_mapping_for_folders_version_2() throws Exception {
        String mapping = FsCrawlerUtil.readMapping(config, "2", FsCrawlerUtil.INDEX_TYPE_FOLDER);
        logger.info("Mapping used for folders : " + mapping);
    }
    @Test
    public void fs_mapping_for_files_version_not_supported() throws Exception {
        try {
            FsCrawlerUtil.readMapping(config, "0", FsCrawlerUtil.INDEX_TYPE_DOC);
            fail("We should have thrown an exception for an unknown elasticsearch version");
        } catch (IllegalArgumentException ignored) {
            assertThat(ignored.getMessage(), containsString("does not exist for elasticsearch version"));
        }
    }

    @Test
    public void fs_mapping_for_folders_version_not_supported() throws Exception {
        try {
            FsCrawlerUtil.readMapping(config, "0", FsCrawlerUtil.INDEX_TYPE_FOLDER);
            fail("We should have thrown an exception for an unknown elasticsearch version");
        } catch (IllegalArgumentException ignored) {
            assertThat(ignored.getMessage(), containsString("does not exist for elasticsearch version"));
        }
    }
}
