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

import fr.pilato.elasticsearch.crawler.fs.test.framework.AbstractFSCrawlerMetadataTestCase;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.junit.BeforeClass;
import org.junit.Test;

import java.io.IOException;
import java.nio.file.Path;

import static fr.pilato.elasticsearch.crawler.fs.framework.FsCrawlerUtil.*;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.is;
import static org.junit.Assert.fail;

public class FsMappingTest extends AbstractFSCrawlerMetadataTestCase {

    private final Logger logger = LogManager.getLogger(FsMappingTest.class);

    private static final String CLASSPATH_RESOURCES_ROOT = "/jobtest/";

    @BeforeClass
    public static void generateSpecificJobMappings() throws IOException {
        Path targetResourceDir = metadataDir.resolve("jobtest").resolve("_mappings");

        for (String filename : MAPPING_RESOURCES) {
            staticLogger.debug("Copying [{}]...", filename);
            Path target = targetResourceDir.resolve(filename);
            copyResourceFile(CLASSPATH_RESOURCES_ROOT + filename, target);
        }

        staticLogger.debug("  --> Mappings generated in [{}]", targetResourceDir);
    }

    @Test
    public void fsSettingsForDocVersion6() throws Exception {
        String settings = readJsonFile(rootTmpDir, metadataDir, 6, INDEX_SETTINGS_FILE);
        logger.info("Settings used for doc index v6 : " + settings);
        assertThat(settings, is("{\n" +
                "  \"settings\": {\n" +
                "    \"number_of_shards\": 1,\n" +
                "    \"index.mapping.total_fields.limit\": 2000,\n" +
                "    \"analysis\": {\n" +
                "      \"analyzer\": {\n" +
                "        \"fscrawler_path\": {\n" +
                "          \"tokenizer\": \"fscrawler_path\"\n" +
                "        }\n" +
                "      },\n" +
                "      \"tokenizer\": {\n" +
                "        \"fscrawler_path\": {\n" +
                "          \"type\": \"path_hierarchy\"\n" +
                "        }\n" +
                "      }\n" +
                "    }\n" +
                "  },\n" +
                "  \"mappings\": {\n" +
                "    \"dynamic_templates\": [\n" +
                "      {\n" +
                "        \"raw_as_text\": {\n" +
                "          \"path_match\": \"meta.raw.*\",\n" +
                "          \"mapping\": {\n" +
                "            \"type\": \"text\",\n" +
                "            \"fields\": {\n" +
                "              \"keyword\": {\n" +
                "                \"type\": \"keyword\",\n" +
                "                \"ignore_above\": 256\n" +
                "              }\n" +
                "            }\n" +
                "          }\n" +
                "        }\n" +
                "      }\n" +
                "    ],\n" +
                "    \"properties\": {\n" +
                "      \"attachment\": {\n" +
                "        \"type\": \"binary\",\n" +
                "        \"doc_values\": false\n" +
                "      },\n" +
                "      \"attributes\": {\n" +
                "        \"properties\": {\n" +
                "          \"group\": {\n" +
                "            \"type\": \"keyword\"\n" +
                "          },\n" +
                "          \"owner\": {\n" +
                "            \"type\": \"keyword\"\n" +
                "          }\n" +
                "        }\n" +
                "      },\n" +
                "      \"content\": {\n" +
                "        \"type\": \"text\"\n" +
                "      },\n" +
                "      \"file\": {\n" +
                "        \"properties\": {\n" +
                "          \"content_type\": {\n" +
                "            \"type\": \"keyword\"\n" +
                "          },\n" +
                "          \"filename\": {\n" +
                "            \"type\": \"keyword\",\n" +
                "            \"store\": true\n" +
                "          },\n" +
                "          \"extension\": {\n" +
                "            \"type\": \"keyword\"\n" +
                "          },\n" +
                "          \"filesize\": {\n" +
                "            \"type\": \"long\"\n" +
                "          },\n" +
                "          \"indexed_chars\": {\n" +
                "            \"type\": \"long\"\n" +
                "          },\n" +
                "          \"indexing_date\": {\n" +
                "            \"type\": \"date\",\n" +
                "            \"format\": \"date_optional_time\"\n" +
                "          },\n" +
                "          \"created\": {\n" +
                "            \"type\": \"date\",\n" +
                "            \"format\": \"date_optional_time\"\n" +
                "          },\n" +
                "          \"last_modified\": {\n" +
                "            \"type\": \"date\",\n" +
                "            \"format\": \"date_optional_time\"\n" +
                "          },\n" +
                "          \"last_accessed\": {\n" +
                "            \"type\": \"date\",\n" +
                "            \"format\": \"date_optional_time\"\n" +
                "          },\n" +
                "          \"checksum\": {\n" +
                "            \"type\": \"keyword\"\n" +
                "          },\n" +
                "          \"url\": {\n" +
                "            \"type\": \"keyword\",\n" +
                "            \"index\": false\n" +
                "          }\n" +
                "        }\n" +
                "      },\n" +
                "      \"meta\": {\n" +
                "        \"properties\": {\n" +
                "          \"author\": {\n" +
                "            \"type\": \"text\"\n" +
                "          },\n" +
                "          \"date\": {\n" +
                "            \"type\": \"date\",\n" +
                "            \"format\": \"date_optional_time\"\n" +
                "          },\n" +
                "          \"keywords\": {\n" +
                "            \"type\": \"text\"\n" +
                "          },\n" +
                "          \"title\": {\n" +
                "            \"type\": \"text\"\n" +
                "          },\n" +
                "          \"language\": {\n" +
                "            \"type\": \"keyword\"\n" +
                "          },\n" +
                "          \"format\": {\n" +
                "            \"type\": \"text\"\n" +
                "          },\n" +
                "          \"identifier\": {\n" +
                "            \"type\": \"text\"\n" +
                "          },\n" +
                "          \"contributor\": {\n" +
                "            \"type\": \"text\"\n" +
                "          },\n" +
                "          \"coverage\": {\n" +
                "            \"type\": \"text\"\n" +
                "          },\n" +
                "          \"modifier\": {\n" +
                "            \"type\": \"text\"\n" +
                "          },\n" +
                "          \"creator_tool\": {\n" +
                "            \"type\": \"keyword\"\n" +
                "          },\n" +
                "          \"publisher\": {\n" +
                "            \"type\": \"text\"\n" +
                "          },\n" +
                "          \"relation\": {\n" +
                "            \"type\": \"text\"\n" +
                "          },\n" +
                "          \"rights\": {\n" +
                "            \"type\": \"text\"\n" +
                "          },\n" +
                "          \"source\": {\n" +
                "            \"type\": \"text\"\n" +
                "          },\n" +
                "          \"type\": {\n" +
                "            \"type\": \"text\"\n" +
                "          },\n" +
                "          \"description\": {\n" +
                "            \"type\": \"text\"\n" +
                "          },\n" +
                "          \"created\": {\n" +
                "            \"type\": \"date\",\n" +
                "            \"format\": \"date_optional_time\"\n" +
                "          },\n" +
                "          \"print_date\": {\n" +
                "            \"type\": \"date\",\n" +
                "            \"format\": \"date_optional_time\"\n" +
                "          },\n" +
                "          \"metadata_date\": {\n" +
                "            \"type\": \"date\",\n" +
                "            \"format\": \"date_optional_time\"\n" +
                "          },\n" +
                "          \"latitude\": {\n" +
                "            \"type\": \"text\"\n" +
                "          },\n" +
                "          \"longitude\": {\n" +
                "            \"type\": \"text\"\n" +
                "          },\n" +
                "          \"altitude\": {\n" +
                "            \"type\": \"text\"\n" +
                "          },\n" +
                "          \"rating\": {\n" +
                "            \"type\": \"byte\"\n" +
                "          },\n" +
                "          \"comments\": {\n" +
                "            \"type\": \"text\"\n" +
                "          }\n" +
                "        }\n" +
                "      },\n" +
                "      \"path\": {\n" +
                "        \"properties\": {\n" +
                "          \"real\": {\n" +
                "            \"type\": \"keyword\",\n" +
                "            \"fields\": {\n" +
                "              \"tree\": {\n" +
                "                \"type\": \"text\",\n" +
                "                \"analyzer\": \"fscrawler_path\",\n" +
                "                \"fielddata\": true\n" +
                "              },\n" +
                "              \"fulltext\": {\n" +
                "                \"type\": \"text\"\n" +
                "              }\n" +
                "            }\n" +
                "          },\n" +
                "          \"root\": {\n" +
                "            \"type\": \"keyword\"\n" +
                "          },\n" +
                "          \"virtual\": {\n" +
                "            \"type\": \"keyword\",\n" +
                "            \"fields\": {\n" +
                "              \"tree\": {\n" +
                "                \"type\": \"text\",\n" +
                "                \"analyzer\": \"fscrawler_path\",\n" +
                "                \"fielddata\": true\n" +
                "              },\n" +
                "              \"fulltext\": {\n" +
                "                \"type\": \"text\"\n" +
                "              }\n" +
                "            }\n" +
                "          }\n" +
                "        }\n" +
                "      }\n" +
                "    }\n" +
                "  }\n" +
                "}\n"));
    }

    @Test
    public void fsSettingsForFolderVersion6() throws Exception {
        String settings = readJsonFile(rootTmpDir, metadataDir, 6, INDEX_SETTINGS_FOLDER_FILE);
        logger.info("Settings used for folder index v6 : " + settings);
        assertThat(settings, is("{\n" +
                "  \"settings\": {\n" +
                "    \"analysis\": {\n" +
                "      \"analyzer\": {\n" +
                "        \"fscrawler_path\": {\n" +
                "          \"tokenizer\": \"fscrawler_path\"\n" +
                "        }\n" +
                "      },\n" +
                "      \"tokenizer\": {\n" +
                "        \"fscrawler_path\": {\n" +
                "          \"type\": \"path_hierarchy\"\n" +
                "        }\n" +
                "      }\n" +
                "    }\n" +
                "  },\n" +
                "  \"mappings\": {\n" +
                "    \"properties\" : {\n" +
                "      \"file\": {\n" +
                "        \"properties\": {\n" +
                "          \"content_type\": {\n" +
                "            \"type\": \"keyword\"\n" +
                "          },\n" +
                "          \"filename\": {\n" +
                "            \"type\": \"keyword\",\n" +
                "            \"store\": true\n" +
                "          }\n" +
                "        }\n" +
                "      },\n" +
                "      \"path\": {\n" +
                "        \"properties\": {\n" +
                "          \"real\": {\n" +
                "            \"type\": \"keyword\",\n" +
                "            \"fields\": {\n" +
                "              \"tree\": {\n" +
                "                \"type\": \"text\",\n" +
                "                \"analyzer\": \"fscrawler_path\",\n" +
                "                \"fielddata\": true\n" +
                "              },\n" +
                "              \"fulltext\": {\n" +
                "                \"type\": \"text\"\n" +
                "              }\n" +
                "            }\n" +
                "          },\n" +
                "          \"root\": {\n" +
                "            \"type\": \"keyword\"\n" +
                "          },\n" +
                "          \"virtual\": {\n" +
                "            \"type\": \"keyword\",\n" +
                "            \"fields\": {\n" +
                "              \"tree\": {\n" +
                "                \"type\": \"text\",\n" +
                "                \"analyzer\": \"fscrawler_path\",\n" +
                "                \"fielddata\": true\n" +
                "              },\n" +
                "              \"fulltext\": {\n" +
                "                \"type\": \"text\"\n" +
                "              }\n" +
                "            }\n" +
                "          }\n" +
                "        }\n" +
                "      }\n" +
                "    }\n" +
                "  }\n" +
                "}\n"));
    }

    @Test
    public void fsSettingsForDocSpecificJobVersion6() throws Exception {
        String settings = readJsonFile(metadataDir.resolve("jobtest").resolve("_mappings"), metadataDir, 6, INDEX_SETTINGS_FILE);
        assertThat(settings, is("{\n" +
                "  // This is settings for version 6\n" +
                "}\n"));
    }

    @Test
    public void fsSettingsForFolderSpecificJobVersion6() throws Exception {
        String settings = readJsonFile(metadataDir.resolve("jobtest").resolve("_mappings"), metadataDir, 6, INDEX_SETTINGS_FOLDER_FILE);
        assertThat(settings, is("{\n" +
                "  // This is folder settings for version 6\n" +
                "}\n"));
    }

    @Test
    public void fsSettingsForDocVersionNotSupported() throws Exception {
        try {
            readJsonFile(rootTmpDir, metadataDir, 0, INDEX_SETTINGS_FILE);
            fail("We should have thrown an exception for an unknown elasticsearch version");
        } catch (IllegalArgumentException e) {
            assertThat(e.getMessage(), containsString("does not exist for elasticsearch version"));
        }
    }

    @Test
    public void fsSettingsForDocVersion7() throws Exception {
        String settings = readJsonFile(rootTmpDir, metadataDir, 7, INDEX_SETTINGS_FILE);
        logger.info("Settings used for doc index v7 : " + settings);
        assertThat(settings, is("{\n" +
                "  \"settings\": {\n" +
                "    \"number_of_shards\": 1,\n" +
                "    \"index.mapping.total_fields.limit\": 2000,\n" +
                "    \"analysis\": {\n" +
                "      \"analyzer\": {\n" +
                "        \"fscrawler_path\": {\n" +
                "          \"tokenizer\": \"fscrawler_path\"\n" +
                "        }\n" +
                "      },\n" +
                "      \"tokenizer\": {\n" +
                "        \"fscrawler_path\": {\n" +
                "          \"type\": \"path_hierarchy\"\n" +
                "        }\n" +
                "      }\n" +
                "    }\n" +
                "  },\n" +
                "  \"mappings\": {\n" +
                "    \"dynamic_templates\": [\n" +
                "      {\n" +
                "        \"raw_as_text\": {\n" +
                "          \"path_match\": \"meta.raw.*\",\n" +
                "          \"mapping\": {\n" +
                "            \"type\": \"text\",\n" +
                "            \"fields\": {\n" +
                "              \"keyword\": {\n" +
                "                \"type\": \"keyword\",\n" +
                "                \"ignore_above\": 256\n" +
                "              }\n" +
                "            }\n" +
                "          }\n" +
                "        }\n" +
                "      }\n" +
                "    ],\n" +
                "    \"properties\": {\n" +
                "      \"attachment\": {\n" +
                "        \"type\": \"binary\",\n" +
                "        \"doc_values\": false\n" +
                "      },\n" +
                "      \"attributes\": {\n" +
                "        \"properties\": {\n" +
                "          \"group\": {\n" +
                "            \"type\": \"keyword\"\n" +
                "          },\n" +
                "          \"owner\": {\n" +
                "            \"type\": \"keyword\"\n" +
                "          }\n" +
                "        }\n" +
                "      },\n" +
                "      \"content\": {\n" +
                "        \"type\": \"text\"\n" +
                "      },\n" +
                "      \"file\": {\n" +
                "        \"properties\": {\n" +
                "          \"content_type\": {\n" +
                "            \"type\": \"keyword\"\n" +
                "          },\n" +
                "          \"filename\": {\n" +
                "            \"type\": \"keyword\",\n" +
                "            \"store\": true\n" +
                "          },\n" +
                "          \"extension\": {\n" +
                "            \"type\": \"keyword\"\n" +
                "          },\n" +
                "          \"filesize\": {\n" +
                "            \"type\": \"long\"\n" +
                "          },\n" +
                "          \"indexed_chars\": {\n" +
                "            \"type\": \"long\"\n" +
                "          },\n" +
                "          \"indexing_date\": {\n" +
                "            \"type\": \"date\",\n" +
                "            \"format\": \"date_optional_time\"\n" +
                "          },\n" +
                "          \"created\": {\n" +
                "            \"type\": \"date\",\n" +
                "            \"format\": \"date_optional_time\"\n" +
                "          },\n" +
                "          \"last_modified\": {\n" +
                "            \"type\": \"date\",\n" +
                "            \"format\": \"date_optional_time\"\n" +
                "          },\n" +
                "          \"last_accessed\": {\n" +
                "            \"type\": \"date\",\n" +
                "            \"format\": \"date_optional_time\"\n" +
                "          },\n" +
                "          \"checksum\": {\n" +
                "            \"type\": \"keyword\"\n" +
                "          },\n" +
                "          \"url\": {\n" +
                "            \"type\": \"keyword\",\n" +
                "            \"index\": false\n" +
                "          }\n" +
                "        }\n" +
                "      },\n" +
                "      \"meta\": {\n" +
                "        \"properties\": {\n" +
                "          \"author\": {\n" +
                "            \"type\": \"text\"\n" +
                "          },\n" +
                "          \"date\": {\n" +
                "            \"type\": \"date\",\n" +
                "            \"format\": \"date_optional_time\"\n" +
                "          },\n" +
                "          \"keywords\": {\n" +
                "            \"type\": \"text\"\n" +
                "          },\n" +
                "          \"title\": {\n" +
                "            \"type\": \"text\"\n" +
                "          },\n" +
                "          \"language\": {\n" +
                "            \"type\": \"keyword\"\n" +
                "          },\n" +
                "          \"format\": {\n" +
                "            \"type\": \"text\"\n" +
                "          },\n" +
                "          \"identifier\": {\n" +
                "            \"type\": \"text\"\n" +
                "          },\n" +
                "          \"contributor\": {\n" +
                "            \"type\": \"text\"\n" +
                "          },\n" +
                "          \"coverage\": {\n" +
                "            \"type\": \"text\"\n" +
                "          },\n" +
                "          \"modifier\": {\n" +
                "            \"type\": \"text\"\n" +
                "          },\n" +
                "          \"creator_tool\": {\n" +
                "            \"type\": \"keyword\"\n" +
                "          },\n" +
                "          \"publisher\": {\n" +
                "            \"type\": \"text\"\n" +
                "          },\n" +
                "          \"relation\": {\n" +
                "            \"type\": \"text\"\n" +
                "          },\n" +
                "          \"rights\": {\n" +
                "            \"type\": \"text\"\n" +
                "          },\n" +
                "          \"source\": {\n" +
                "            \"type\": \"text\"\n" +
                "          },\n" +
                "          \"type\": {\n" +
                "            \"type\": \"text\"\n" +
                "          },\n" +
                "          \"description\": {\n" +
                "            \"type\": \"text\"\n" +
                "          },\n" +
                "          \"created\": {\n" +
                "            \"type\": \"date\",\n" +
                "            \"format\": \"date_optional_time\"\n" +
                "          },\n" +
                "          \"print_date\": {\n" +
                "            \"type\": \"date\",\n" +
                "            \"format\": \"date_optional_time\"\n" +
                "          },\n" +
                "          \"metadata_date\": {\n" +
                "            \"type\": \"date\",\n" +
                "            \"format\": \"date_optional_time\"\n" +
                "          },\n" +
                "          \"latitude\": {\n" +
                "            \"type\": \"text\"\n" +
                "          },\n" +
                "          \"longitude\": {\n" +
                "            \"type\": \"text\"\n" +
                "          },\n" +
                "          \"altitude\": {\n" +
                "            \"type\": \"text\"\n" +
                "          },\n" +
                "          \"rating\": {\n" +
                "            \"type\": \"byte\"\n" +
                "          },\n" +
                "          \"comments\": {\n" +
                "            \"type\": \"text\"\n" +
                "          }\n" +
                "        }\n" +
                "      },\n" +
                "      \"path\": {\n" +
                "        \"properties\": {\n" +
                "          \"real\": {\n" +
                "            \"type\": \"keyword\",\n" +
                "            \"fields\": {\n" +
                "              \"tree\": {\n" +
                "                \"type\": \"text\",\n" +
                "                \"analyzer\": \"fscrawler_path\",\n" +
                "                \"fielddata\": true\n" +
                "              },\n" +
                "              \"fulltext\": {\n" +
                "                \"type\": \"text\"\n" +
                "              }\n" +
                "            }\n" +
                "          },\n" +
                "          \"root\": {\n" +
                "            \"type\": \"keyword\"\n" +
                "          },\n" +
                "          \"virtual\": {\n" +
                "            \"type\": \"keyword\",\n" +
                "            \"fields\": {\n" +
                "              \"tree\": {\n" +
                "                \"type\": \"text\",\n" +
                "                \"analyzer\": \"fscrawler_path\",\n" +
                "                \"fielddata\": true\n" +
                "              },\n" +
                "              \"fulltext\": {\n" +
                "                \"type\": \"text\"\n" +
                "              }\n" +
                "            }\n" +
                "          }\n" +
                "        }\n" +
                "      }\n" +
                "    }\n" +
                "  }\n" +
                "}\n"));
    }

    @Test
    public void fsSettingsForFolderVersion7() throws Exception {
        String settings = readJsonFile(rootTmpDir, metadataDir, 7, INDEX_SETTINGS_FOLDER_FILE);
        logger.info("Settings used for folder index v7 : " + settings);
        assertThat(settings, is("{\n" +
                "  \"settings\": {\n" +
                "    \"analysis\": {\n" +
                "      \"analyzer\": {\n" +
                "        \"fscrawler_path\": {\n" +
                "          \"tokenizer\": \"fscrawler_path\"\n" +
                "        }\n" +
                "      },\n" +
                "      \"tokenizer\": {\n" +
                "        \"fscrawler_path\": {\n" +
                "          \"type\": \"path_hierarchy\"\n" +
                "        }\n" +
                "      }\n" +
                "    }\n" +
                "  },\n" +
                "  \"mappings\": {\n" +
                "    \"properties\" : {\n" +
                "      \"file\": {\n" +
                "        \"properties\": {\n" +
                "          \"content_type\": {\n" +
                "            \"type\": \"keyword\"\n" +
                "          },\n" +
                "          \"filename\": {\n" +
                "            \"type\": \"keyword\",\n" +
                "            \"store\": true\n" +
                "          }\n" +
                "        }\n" +
                "      },\n" +
                "      \"path\": {\n" +
                "        \"properties\": {\n" +
                "          \"real\": {\n" +
                "            \"type\": \"keyword\",\n" +
                "            \"fields\": {\n" +
                "              \"tree\": {\n" +
                "                \"type\": \"text\",\n" +
                "                \"analyzer\": \"fscrawler_path\",\n" +
                "                \"fielddata\": true\n" +
                "              },\n" +
                "              \"fulltext\": {\n" +
                "                \"type\": \"text\"\n" +
                "              }\n" +
                "            }\n" +
                "          },\n" +
                "          \"root\": {\n" +
                "            \"type\": \"keyword\"\n" +
                "          },\n" +
                "          \"virtual\": {\n" +
                "            \"type\": \"keyword\",\n" +
                "            \"fields\": {\n" +
                "              \"tree\": {\n" +
                "                \"type\": \"text\",\n" +
                "                \"analyzer\": \"fscrawler_path\",\n" +
                "                \"fielddata\": true\n" +
                "              },\n" +
                "              \"fulltext\": {\n" +
                "                \"type\": \"text\"\n" +
                "              }\n" +
                "            }\n" +
                "          }\n" +
                "        }\n" +
                "      }\n" +
                "    }\n" +
                "  }\n" +
                "}\n"));
    }

    @Test
    public void fsSettingsForWorkplaceSearchVersion7() throws Exception {
        String settings = readJsonFile(rootTmpDir, metadataDir, 7, INDEX_WORKPLACE_SEARCH_SETTINGS_FILE);
        logger.info("Settings used for workplace search v7 : " + settings);
        assertThat(settings, is("{\n" +
                "  \"name\": \"SOURCE_NAME\",\n" +
                "  \"context\": \"organization\",\n" +
                "  \"schema\": {\n" +
                "    \"body\": \"text\",\n" +
                "    \"comments\": \"text\",\n" +
                "    \"tags\": \"text\",\n" +
                "    \"title\": \"text\",\n" +
                "    \"type\": \"text\",\n" +
                "    \"url\": \"text\",\n" +
                "    \"extension\": \"text\",\n" +
                "    \"mime_type\": \"text\",\n" +
                "    \"path\": \"text\",\n" +
                "    \"size\": \"number\",\n" +
                "    \"created_by\": \"text\",\n" +
                "    \"name\": \"text\",\n" +
                "    \"language\": \"text\",\n" +
                "    \"text_size\": \"number\",\n" +
                "    \"created_at\": \"date\",\n" +
                "    \"last_modified\": \"date\"\n" +
                "  },\n" +
                "  \"display\": {\n" +
                "    \"title_field\": \"title\",\n" +
                "    \"subtitle_field\": \"name\",\n" +
                "    \"description_field\": \"body\",\n" +
                "    \"url_field\": \"url\",\n" +
                "    \"media_type_field\": \"mime_type\",\n" +
                "    \"created_by_field\": \"created_by\",\n" +
                "    \"detail_fields\": [\n" +
                "      {\n" +
                "        \"field_name\": \"created_by\",\n" +
                "        \"label\": \"Author\"\n" +
                "      },\n" +
                "      {\n" +
                "        \"field_name\": \"tags\",\n" +
                "        \"label\": \"Tags\"\n" +
                "      },\n" +
                "      {\n" +
                "        \"field_name\": \"language\",\n" +
                "        \"label\": \"Language\"\n" +
                "      },\n" +
                "      {\n" +
                "        \"field_name\": \"last_modified\",\n" +
                "        \"label\": \"Last Modification Date\"\n" +
                "      },\n" +
                "      {\n" +
                "        \"field_name\": \"created_at\",\n" +
                "        \"label\": \"Creation date\"\n" +
                "      },\n" +
                "      {\n" +
                "        \"field_name\": \"comments\",\n" +
                "        \"label\": \"Comments\"\n" +
                "      },\n" +
                "      {\n" +
                "        \"field_name\": \"extension\",\n" +
                "        \"label\": \"Extension\"\n" +
                "      },\n" +
                "      {\n" +
                "        \"field_name\": \"size\",\n" +
                "        \"label\": \"File size\"\n" +
                "      },\n" +
                "      {\n" +
                "        \"field_name\": \"text_size\",\n" +
                "        \"label\": \"Extracted text size\"\n" +
                "      },\n" +
                "      {\n" +
                "        \"field_name\": \"path\",\n" +
                "        \"label\": \"Path\"\n" +
                "      },\n" +
                "      {\n" +
                "        \"field_name\": \"body\",\n" +
                "        \"label\": \"Content\"\n" +
                "      }\n" +
                "    ],\n" +
                "    \"color\": \"#000000\"\n" +
                "  },\n" +
                "  \"facets\":\n" +
                "  {\n" +
                "    \"overrides\":\n" +
                "    [\n" +
                "      {\n" +
                "        \"display_name\": \"Media Type\",\n" +
                "        \"field\": \"mime_type\",\n" +
                "        \"enabled\": true\n" +
                "      },\n" +
                "      {\n" +
                "        \"display_name\": \"Extension\",\n" +
                "        \"field\": \"extension\",\n" +
                "        \"enabled\": true\n" +
                "      },\n" +
                "      {\n" +
                "        \"display_name\": \"Tags\",\n" +
                "        \"field\": \"tags\",\n" +
                "        \"enabled\": true\n" +
                "      },\n" +
                "      {\n" +
                "        \"display_name\": \"Created By\",\n" +
                "        \"field\": \"created_by\",\n" +
                "        \"enabled\": true\n" +
                "      },\n" +
                "      {\n" +
                "        \"display_name\": \"Language\",\n" +
                "        \"field\": \"language\",\n" +
                "        \"enabled\": true\n" +
                "      }\n" +
                "    ]\n" +
                "  },\n" +
                "  \"is_searchable\": true\n" +
                "}\n"));
    }

    @Test
    public void fsSettingsForDocSpecificJobVersion7() throws Exception {
        String settings = readJsonFile(metadataDir.resolve("jobtest").resolve("_mappings"), metadataDir, 7, INDEX_SETTINGS_FILE);
        assertThat(settings, is("{\n" +
                "  // This is settings for version 7\n" +
                "}\n"));
    }

    @Test
    public void fsSettingsForFolderSpecificJobVersion7() throws Exception {
        String settings = readJsonFile(metadataDir.resolve("jobtest").resolve("_mappings"), metadataDir, 7, INDEX_SETTINGS_FOLDER_FILE);
        assertThat(settings, is("{\n" +
                "  // This is folder settings for version 7\n" +
                "}\n"));
    }

    @Test
    public void fsSettingsForWorkplaceSearchSpecificJobVersion7() throws Exception {
        String settings = readJsonFile(metadataDir.resolve("jobtest").resolve("_mappings"), metadataDir, 7, INDEX_WORKPLACE_SEARCH_SETTINGS_FILE);
        assertThat(settings, is("{\n" +
                "  \"name\": \"SOURCE_NAME\",\n" +
                "  \"schema\": {\n" +
                "    \"title\": \"text\"\n" +
                "  },\n" +
                "  \"is_searchable\": true\n" +
                "}\n"));
    }

    @Test
    public void fsSettingsForDocVersion8() throws Exception {
        String settings = readJsonFile(rootTmpDir, metadataDir, 8, INDEX_SETTINGS_FILE);
        logger.info("Settings used for doc index v8 : " + settings);
        assertThat(settings, is("{\n" +
                "  \"settings\": {\n" +
                "    \"number_of_shards\": 1,\n" +
                "    \"index.mapping.total_fields.limit\": 2000,\n" +
                "    \"analysis\": {\n" +
                "      \"analyzer\": {\n" +
                "        \"fscrawler_path\": {\n" +
                "          \"tokenizer\": \"fscrawler_path\"\n" +
                "        }\n" +
                "      },\n" +
                "      \"tokenizer\": {\n" +
                "        \"fscrawler_path\": {\n" +
                "          \"type\": \"path_hierarchy\"\n" +
                "        }\n" +
                "      }\n" +
                "    }\n" +
                "  },\n" +
                "  \"mappings\": {\n" +
                "    \"dynamic_templates\": [\n" +
                "      {\n" +
                "        \"raw_as_text\": {\n" +
                "          \"path_match\": \"meta.raw.*\",\n" +
                "          \"mapping\": {\n" +
                "            \"type\": \"text\",\n" +
                "            \"fields\": {\n" +
                "              \"keyword\": {\n" +
                "                \"type\": \"keyword\",\n" +
                "                \"ignore_above\": 256\n" +
                "              }\n" +
                "            }\n" +
                "          }\n" +
                "        }\n" +
                "      }\n" +
                "    ],\n" +
                "    \"properties\": {\n" +
                "      \"attachment\": {\n" +
                "        \"type\": \"binary\",\n" +
                "        \"doc_values\": false\n" +
                "      },\n" +
                "      \"attributes\": {\n" +
                "        \"properties\": {\n" +
                "          \"group\": {\n" +
                "            \"type\": \"keyword\"\n" +
                "          },\n" +
                "          \"owner\": {\n" +
                "            \"type\": \"keyword\"\n" +
                "          }\n" +
                "        }\n" +
                "      },\n" +
                "      \"content\": {\n" +
                "        \"type\": \"text\"\n" +
                "      },\n" +
                "      \"file\": {\n" +
                "        \"properties\": {\n" +
                "          \"content_type\": {\n" +
                "            \"type\": \"keyword\"\n" +
                "          },\n" +
                "          \"filename\": {\n" +
                "            \"type\": \"keyword\",\n" +
                "            \"store\": true\n" +
                "          },\n" +
                "          \"extension\": {\n" +
                "            \"type\": \"keyword\"\n" +
                "          },\n" +
                "          \"filesize\": {\n" +
                "            \"type\": \"long\"\n" +
                "          },\n" +
                "          \"indexed_chars\": {\n" +
                "            \"type\": \"long\"\n" +
                "          },\n" +
                "          \"indexing_date\": {\n" +
                "            \"type\": \"date\",\n" +
                "            \"format\": \"date_optional_time\"\n" +
                "          },\n" +
                "          \"created\": {\n" +
                "            \"type\": \"date\",\n" +
                "            \"format\": \"date_optional_time\"\n" +
                "          },\n" +
                "          \"last_modified\": {\n" +
                "            \"type\": \"date\",\n" +
                "            \"format\": \"date_optional_time\"\n" +
                "          },\n" +
                "          \"last_accessed\": {\n" +
                "            \"type\": \"date\",\n" +
                "            \"format\": \"date_optional_time\"\n" +
                "          },\n" +
                "          \"checksum\": {\n" +
                "            \"type\": \"keyword\"\n" +
                "          },\n" +
                "          \"url\": {\n" +
                "            \"type\": \"keyword\",\n" +
                "            \"index\": false\n" +
                "          }\n" +
                "        }\n" +
                "      },\n" +
                "      \"meta\": {\n" +
                "        \"properties\": {\n" +
                "          \"author\": {\n" +
                "            \"type\": \"text\"\n" +
                "          },\n" +
                "          \"date\": {\n" +
                "            \"type\": \"date\",\n" +
                "            \"format\": \"date_optional_time\"\n" +
                "          },\n" +
                "          \"keywords\": {\n" +
                "            \"type\": \"text\"\n" +
                "          },\n" +
                "          \"title\": {\n" +
                "            \"type\": \"text\"\n" +
                "          },\n" +
                "          \"language\": {\n" +
                "            \"type\": \"keyword\"\n" +
                "          },\n" +
                "          \"format\": {\n" +
                "            \"type\": \"text\"\n" +
                "          },\n" +
                "          \"identifier\": {\n" +
                "            \"type\": \"text\"\n" +
                "          },\n" +
                "          \"contributor\": {\n" +
                "            \"type\": \"text\"\n" +
                "          },\n" +
                "          \"coverage\": {\n" +
                "            \"type\": \"text\"\n" +
                "          },\n" +
                "          \"modifier\": {\n" +
                "            \"type\": \"text\"\n" +
                "          },\n" +
                "          \"creator_tool\": {\n" +
                "            \"type\": \"keyword\"\n" +
                "          },\n" +
                "          \"publisher\": {\n" +
                "            \"type\": \"text\"\n" +
                "          },\n" +
                "          \"relation\": {\n" +
                "            \"type\": \"text\"\n" +
                "          },\n" +
                "          \"rights\": {\n" +
                "            \"type\": \"text\"\n" +
                "          },\n" +
                "          \"source\": {\n" +
                "            \"type\": \"text\"\n" +
                "          },\n" +
                "          \"type\": {\n" +
                "            \"type\": \"text\"\n" +
                "          },\n" +
                "          \"description\": {\n" +
                "            \"type\": \"text\"\n" +
                "          },\n" +
                "          \"created\": {\n" +
                "            \"type\": \"date\",\n" +
                "            \"format\": \"date_optional_time\"\n" +
                "          },\n" +
                "          \"print_date\": {\n" +
                "            \"type\": \"date\",\n" +
                "            \"format\": \"date_optional_time\"\n" +
                "          },\n" +
                "          \"metadata_date\": {\n" +
                "            \"type\": \"date\",\n" +
                "            \"format\": \"date_optional_time\"\n" +
                "          },\n" +
                "          \"latitude\": {\n" +
                "            \"type\": \"text\"\n" +
                "          },\n" +
                "          \"longitude\": {\n" +
                "            \"type\": \"text\"\n" +
                "          },\n" +
                "          \"altitude\": {\n" +
                "            \"type\": \"text\"\n" +
                "          },\n" +
                "          \"rating\": {\n" +
                "            \"type\": \"byte\"\n" +
                "          },\n" +
                "          \"comments\": {\n" +
                "            \"type\": \"text\"\n" +
                "          }\n" +
                "        }\n" +
                "      },\n" +
                "      \"path\": {\n" +
                "        \"properties\": {\n" +
                "          \"real\": {\n" +
                "            \"type\": \"keyword\",\n" +
                "            \"fields\": {\n" +
                "              \"tree\": {\n" +
                "                \"type\": \"text\",\n" +
                "                \"analyzer\": \"fscrawler_path\",\n" +
                "                \"fielddata\": true\n" +
                "              },\n" +
                "              \"fulltext\": {\n" +
                "                \"type\": \"text\"\n" +
                "              }\n" +
                "            }\n" +
                "          },\n" +
                "          \"root\": {\n" +
                "            \"type\": \"keyword\"\n" +
                "          },\n" +
                "          \"virtual\": {\n" +
                "            \"type\": \"keyword\",\n" +
                "            \"fields\": {\n" +
                "              \"tree\": {\n" +
                "                \"type\": \"text\",\n" +
                "                \"analyzer\": \"fscrawler_path\",\n" +
                "                \"fielddata\": true\n" +
                "              },\n" +
                "              \"fulltext\": {\n" +
                "                \"type\": \"text\"\n" +
                "              }\n" +
                "            }\n" +
                "          }\n" +
                "        }\n" +
                "      }\n" +
                "    }\n" +
                "  }\n" +
                "}\n"));
    }

    @Test
    public void fsSettingsForFolderVersion8() throws Exception {
        String settings = readJsonFile(rootTmpDir, metadataDir, 8, INDEX_SETTINGS_FOLDER_FILE);
        logger.info("Settings used for folder index v8 : " + settings);
        assertThat(settings, is("{\n" +
                "  \"settings\": {\n" +
                "    \"analysis\": {\n" +
                "      \"analyzer\": {\n" +
                "        \"fscrawler_path\": {\n" +
                "          \"tokenizer\": \"fscrawler_path\"\n" +
                "        }\n" +
                "      },\n" +
                "      \"tokenizer\": {\n" +
                "        \"fscrawler_path\": {\n" +
                "          \"type\": \"path_hierarchy\"\n" +
                "        }\n" +
                "      }\n" +
                "    }\n" +
                "  },\n" +
                "  \"mappings\": {\n" +
                "    \"properties\" : {\n" +
                "      \"file\": {\n" +
                "        \"properties\": {\n" +
                "          \"content_type\": {\n" +
                "            \"type\": \"keyword\"\n" +
                "          },\n" +
                "          \"filename\": {\n" +
                "            \"type\": \"keyword\",\n" +
                "            \"store\": true\n" +
                "          }\n" +
                "        }\n" +
                "      },\n" +
                "      \"path\": {\n" +
                "        \"properties\": {\n" +
                "          \"real\": {\n" +
                "            \"type\": \"keyword\",\n" +
                "            \"fields\": {\n" +
                "              \"tree\": {\n" +
                "                \"type\": \"text\",\n" +
                "                \"analyzer\": \"fscrawler_path\",\n" +
                "                \"fielddata\": true\n" +
                "              },\n" +
                "              \"fulltext\": {\n" +
                "                \"type\": \"text\"\n" +
                "              }\n" +
                "            }\n" +
                "          },\n" +
                "          \"root\": {\n" +
                "            \"type\": \"keyword\"\n" +
                "          },\n" +
                "          \"virtual\": {\n" +
                "            \"type\": \"keyword\",\n" +
                "            \"fields\": {\n" +
                "              \"tree\": {\n" +
                "                \"type\": \"text\",\n" +
                "                \"analyzer\": \"fscrawler_path\",\n" +
                "                \"fielddata\": true\n" +
                "              },\n" +
                "              \"fulltext\": {\n" +
                "                \"type\": \"text\"\n" +
                "              }\n" +
                "            }\n" +
                "          }\n" +
                "        }\n" +
                "      }\n" +
                "    }\n" +
                "  }\n" +
                "}\n"));
    }

    @Test
    public void fsSettingsForWorkplaceSearchVersion8() throws Exception {
        String settings = readJsonFile(rootTmpDir, metadataDir, 8, INDEX_WORKPLACE_SEARCH_SETTINGS_FILE);
        logger.info("Settings used for workplace search v8 : " + settings);
        assertThat(settings, is("{\n" +
                "  \"name\": \"SOURCE_NAME\",\n" +
                "  \"context\": \"organization\",\n" +
                "  \"schema\": {\n" +
                "    \"body\": \"text\",\n" +
                "    \"comments\": \"text\",\n" +
                "    \"tags\": \"text\",\n" +
                "    \"title\": \"text\",\n" +
                "    \"type\": \"text\",\n" +
                "    \"url\": \"text\",\n" +
                "    \"extension\": \"text\",\n" +
                "    \"mime_type\": \"text\",\n" +
                "    \"path\": \"text\",\n" +
                "    \"size\": \"number\",\n" +
                "    \"created_by\": \"text\",\n" +
                "    \"name\": \"text\",\n" +
                "    \"language\": \"text\",\n" +
                "    \"text_size\": \"number\",\n" +
                "    \"created_at\": \"date\",\n" +
                "    \"last_modified\": \"date\"\n" +
                "  },\n" +
                "  \"display\": {\n" +
                "    \"title_field\": \"title\",\n" +
                "    \"subtitle_field\": \"name\",\n" +
                "    \"description_field\": \"body\",\n" +
                "    \"url_field\": \"url\",\n" +
                "    \"media_type_field\": \"mime_type\",\n" +
                "    \"created_by_field\": \"created_by\",\n" +
                "    \"detail_fields\": [\n" +
                "      {\n" +
                "        \"field_name\": \"created_by\",\n" +
                "        \"label\": \"Author\"\n" +
                "      },\n" +
                "      {\n" +
                "        \"field_name\": \"tags\",\n" +
                "        \"label\": \"Tags\"\n" +
                "      },\n" +
                "      {\n" +
                "        \"field_name\": \"language\",\n" +
                "        \"label\": \"Language\"\n" +
                "      },\n" +
                "      {\n" +
                "        \"field_name\": \"last_modified\",\n" +
                "        \"label\": \"Last Modification Date\"\n" +
                "      },\n" +
                "      {\n" +
                "        \"field_name\": \"created_at\",\n" +
                "        \"label\": \"Creation date\"\n" +
                "      },\n" +
                "      {\n" +
                "        \"field_name\": \"comments\",\n" +
                "        \"label\": \"Comments\"\n" +
                "      },\n" +
                "      {\n" +
                "        \"field_name\": \"extension\",\n" +
                "        \"label\": \"Extension\"\n" +
                "      },\n" +
                "      {\n" +
                "        \"field_name\": \"size\",\n" +
                "        \"label\": \"File size\"\n" +
                "      },\n" +
                "      {\n" +
                "        \"field_name\": \"text_size\",\n" +
                "        \"label\": \"Extracted text size\"\n" +
                "      },\n" +
                "      {\n" +
                "        \"field_name\": \"path\",\n" +
                "        \"label\": \"Path\"\n" +
                "      },\n" +
                "      {\n" +
                "        \"field_name\": \"body\",\n" +
                "        \"label\": \"Content\"\n" +
                "      }\n" +
                "    ],\n" +
                "    \"color\": \"#000000\"\n" +
                "  },\n" +
                "  \"facets\":\n" +
                "  {\n" +
                "    \"overrides\":\n" +
                "    [\n" +
                "      {\n" +
                "        \"display_name\": \"Media Type\",\n" +
                "        \"field\": \"mime_type\",\n" +
                "        \"enabled\": true\n" +
                "      },\n" +
                "      {\n" +
                "        \"display_name\": \"Extension\",\n" +
                "        \"field\": \"extension\",\n" +
                "        \"enabled\": true\n" +
                "      },\n" +
                "      {\n" +
                "        \"display_name\": \"Tags\",\n" +
                "        \"field\": \"tags\",\n" +
                "        \"enabled\": true\n" +
                "      },\n" +
                "      {\n" +
                "        \"display_name\": \"Created By\",\n" +
                "        \"field\": \"created_by\",\n" +
                "        \"enabled\": true\n" +
                "      },\n" +
                "      {\n" +
                "        \"display_name\": \"Language\",\n" +
                "        \"field\": \"language\",\n" +
                "        \"enabled\": true\n" +
                "      }\n" +
                "    ]\n" +
                "  },\n" +
                "  \"is_searchable\": true\n" +
                "}\n"));
    }

    @Test
    public void fsSettingsForDocSpecificJobVersion8() throws Exception {
        String settings = readJsonFile(metadataDir.resolve("jobtest").resolve("_mappings"), metadataDir, 8, INDEX_SETTINGS_FILE);
        assertThat(settings, is("{\n" +
                "  // This is settings for version 8\n" +
                "}\n"));
    }

    @Test
    public void fsSettingsForFolderSpecificJobVersion8() throws Exception {
        String settings = readJsonFile(metadataDir.resolve("jobtest").resolve("_mappings"), metadataDir, 8, INDEX_SETTINGS_FOLDER_FILE);
        assertThat(settings, is("{\n" +
                "  // This is folder settings for version 8\n" +
                "}\n"));
    }

    @Test
    public void fsSettingsForWorkplaceSearchSpecificJobVersion8() throws Exception {
        String settings = readJsonFile(metadataDir.resolve("jobtest").resolve("_mappings"), metadataDir, 8, INDEX_WORKPLACE_SEARCH_SETTINGS_FILE);
        assertThat(settings, is("{\n" +
                "  \"name\": \"SOURCE_NAME\",\n" +
                "  \"schema\": {\n" +
                "    \"title\": \"text\"\n" +
                "  },\n" +
                "  \"is_searchable\": true\n" +
                "}\n"));
    }

    @Test
    public void fsSettingsForFolderVersionNotSupported() throws Exception {
        try {
            readJsonFile(rootTmpDir, metadataDir, 0, INDEX_SETTINGS_FOLDER_FILE);
            fail("We should have thrown an exception for an unknown elasticsearch version");
        } catch (IllegalArgumentException e) {
            assertThat(e.getMessage(), containsString("does not exist for elasticsearch version"));
        }
    }
}
