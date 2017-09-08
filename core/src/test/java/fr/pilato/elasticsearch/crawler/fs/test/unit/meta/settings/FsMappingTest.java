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

package fr.pilato.elasticsearch.crawler.fs.test.unit.meta.settings;

import fr.pilato.elasticsearch.crawler.fs.test.framework.AbstractFSCrawlerTestCase;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.junit.BeforeClass;
import org.junit.Test;

import java.io.IOException;
import java.net.URISyntaxException;
import java.nio.file.Path;

import static fr.pilato.elasticsearch.crawler.fs.framework.FsCrawlerUtil.INDEX_SETTINGS_FILE;
import static fr.pilato.elasticsearch.crawler.fs.framework.FsCrawlerUtil.INDEX_SETTINGS_FOLDER_FILE;
import static fr.pilato.elasticsearch.crawler.fs.framework.FsCrawlerUtil.MAPPING_RESOURCES;
import static fr.pilato.elasticsearch.crawler.fs.framework.FsCrawlerUtil.copyResourceFile;
import static fr.pilato.elasticsearch.crawler.fs.framework.FsCrawlerUtil.readJsonFile;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.is;
import static org.junit.Assert.fail;

public class FsMappingTest extends AbstractFSCrawlerTestCase {

    private final Logger logger = LogManager.getLogger(FsMappingTest.class);

    private static final String CLASSPATH_RESOURCES_ROOT = "/jobtest/";

    @BeforeClass
    public static void generateSpecificJobMappings() throws IOException, URISyntaxException {
        Path targetResourceDir = metadataDir.resolve("jobtest").resolve("_mappings");

        for (String filename : MAPPING_RESOURCES) {
            staticLogger.debug("Copying [{}]...", filename);
            Path target = targetResourceDir.resolve(filename);
            copyResourceFile(CLASSPATH_RESOURCES_ROOT + filename, target);
        }

        staticLogger.debug("  --> Mappings generated in [{}]", targetResourceDir);
    }

    @Test
    public void fsSettingsForDocVersion2() throws Exception {
        String settings = readJsonFile(rootTmpDir, metadataDir, "2", INDEX_SETTINGS_FILE);
        logger.info("Settings used for doc index v2 : " + settings);
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
                "    \"doc\": {\n" +
                "      \"properties\": {\n" +
                "        \"content\": {\n" +
                "          \"type\": \"string\"\n" +
                "        },\n" +
                "        \"attachment\": {\n" +
                "          \"type\": \"binary\",\n" +
                "          \"doc_values\": false\n" +
                "        },\n" +
                "        \"meta\": {\n" +
                "          \"properties\": {\n" +
                "            \"author\": {\n" +
                "              \"type\": \"string\"\n" +
                "            },\n" +
                "            \"title\": {\n" +
                "              \"type\": \"string\"\n" +
                "            },\n" +
                "            \"date\": {\n" +
                "              \"type\": \"date\",\n" +
                "              \"format\": \"dateOptionalTime\"\n" +
                "            },\n" +
                "            \"keywords\": {\n" +
                "              \"type\": \"string\"\n" +
                "            },\n" +
                "            \"language\" : {\n" +
                "              \"type\" : \"string\",\n" +
                "              \"index\": \"not_analyzed\"\n" +
                "            },\n" +
                "            \"format\" : {\n" +
                "              \"type\" : \"string\"\n" +
                "            },\n" +
                "            \"identifier\" : {\n" +
                "              \"type\" : \"string\"\n" +
                "            },\n" +
                "            \"contributor\" : {\n" +
                "              \"type\" : \"string\"\n" +
                "            },\n" +
                "            \"coverage\" : {\n" +
                "              \"type\" : \"string\"\n" +
                "            },\n" +
                "            \"modifier\" : {\n" +
                "              \"type\" : \"string\"\n" +
                "            },\n" +
                "            \"creator_tool\" : {\n" +
                "              \"type\" : \"string\",\n" +
                "              \"index\": \"not_analyzed\"\n" +
                "            },\n" +
                "            \"publisher\" : {\n" +
                "              \"type\" : \"string\"\n" +
                "            },\n" +
                "            \"relation\" : {\n" +
                "              \"type\" : \"string\"\n" +
                "            },\n" +
                "            \"rights\" : {\n" +
                "              \"type\" : \"string\"\n" +
                "            },\n" +
                "            \"source\" : {\n" +
                "              \"type\" : \"string\"\n" +
                "            },\n" +
                "            \"type\" : {\n" +
                "              \"type\" : \"string\"\n" +
                "            },\n" +
                "            \"description\" : {\n" +
                "              \"type\" : \"string\"\n" +
                "            },\n" +
                "            \"created\" : {\n" +
                "              \"type\" : \"date\",\n" +
                "              \"format\" : \"dateOptionalTime\"\n" +
                "            },\n" +
                "            \"print_date\" : {\n" +
                "              \"type\" : \"date\",\n" +
                "              \"format\" : \"dateOptionalTime\"\n" +
                "            },\n" +
                "            \"metadata_date\" : {\n" +
                "              \"type\" : \"date\",\n" +
                "              \"format\" : \"dateOptionalTime\"\n" +
                "            },\n" +
                "            \"latitude\" : {\n" +
                "              \"type\" : \"string\"\n" +
                "            },\n" +
                "            \"longitude\" : {\n" +
                "              \"type\" : \"string\"\n" +
                "            },\n" +
                "            \"altitude\" : {\n" +
                "              \"type\" : \"string\"\n" +
                "            },\n" +
                "            \"rating\" : {\n" +
                "              \"type\" : \"byte\"\n" +
                "            },\n" +
                "            \"comments\" : {\n" +
                "              \"type\" : \"string\"\n" +
                "            }\n" +
                "          }\n" +
                "        },\n" +
                "        \"file\": {\n" +
                "          \"properties\": {\n" +
                "            \"content_type\": {\n" +
                "              \"type\": \"string\",\n" +
                "              \"index\": \"not_analyzed\"\n" +
                "            },\n" +
                "            \"last_modified\": {\n" +
                "              \"type\": \"date\",\n" +
                "              \"format\": \"dateOptionalTime\"\n" +
                "            },\n" +
                "            \"indexing_date\": {\n" +
                "              \"type\": \"date\",\n" +
                "              \"format\": \"dateOptionalTime\"\n" +
                "            },\n" +
                "            \"filesize\": {\n" +
                "              \"type\": \"long\"\n" +
                "            },\n" +
                "            \"indexed_chars\": {\n" +
                "              \"type\": \"long\"\n" +
                "            },\n" +
                "            \"filename\": {\n" +
                "              \"type\": \"string\",\n" +
                "              \"index\": \"not_analyzed\",\n" +
                "              \"store\": true\n" +
                "            },\n" +
                "            \"extension\": {\n" +
                "              \"type\": \"string\",\n" +
                "              \"index\": \"not_analyzed\"\n" +
                "            },\n" +
                "            \"checksum\": {\n" +
                "              \"type\": \"string\",\n" +
                "              \"index\": \"not_analyzed\"\n" +
                "            },\n" +
                "            \"url\": {\n" +
                "              \"type\": \"string\",\n" +
                "              \"index\": \"no\"\n" +
                "            }\n" +
                "          }\n" +
                "        },\n" +
                "        \"path\": {\n" +
                "          \"properties\": {\n" +
                "            \"virtual\": {\n" +
                "              \"type\": \"string\",\n" +
                "              \"index\": \"not_analyzed\",\n" +
                "              \"fields\": {\n" +
                "                \"tree\": {\n" +
                "                  \"type\": \"string\",\n" +
                "                  \"analyzer\": \"fscrawler_path\"\n" +
                "                }\n" +
                "              }\n" +
                "            },\n" +
                "            \"root\": {\n" +
                "              \"type\": \"string\",\n" +
                "              \"index\": \"not_analyzed\"\n" +
                "            },\n" +
                "            \"real\": {\n" +
                "              \"type\": \"string\",\n" +
                "              \"index\": \"not_analyzed\",\n" +
                "              \"fields\": {\n" +
                "                \"tree\": {\n" +
                "                  \"type\": \"string\",\n" +
                "                  \"analyzer\": \"fscrawler_path\"\n" +
                "                }\n" +
                "              }\n" +
                "            }\n" +
                "          }\n" +
                "        },\n" +
                "        \"attributes\": {\n" +
                "          \"properties\": {\n" +
                "            \"owner\": {\n" +
                "              \"type\": \"string\",\n" +
                "              \"index\": \"not_analyzed\"\n" +
                "            },\n" +
                "            \"group\": {\n" +
                "              \"type\": \"string\",\n" +
                "              \"index\": \"not_analyzed\"\n" +
                "            }\n" +
                "          }\n" +
                "        }\n" +
                "      }\n" +
                "    }\n" +
                "  }\n" +
                "}\n"));
    }

    @Test
    public void fsSettingsForFolderVersion2() throws Exception {
        String settings = readJsonFile(rootTmpDir, metadataDir, "2", INDEX_SETTINGS_FOLDER_FILE);
        logger.info("Settings used for folder index v2 : " + settings);
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
                "    \"doc\": {\n" +
                "      \"properties\": {\n" +
                "        \"real\": {\n" +
                "          \"type\": \"string\",\n" +
                "          \"store\": true,\n" +
                "          \"index\": \"not_analyzed\"\n" +
                "        },\n" +
                "        \"root\": {\n" +
                "          \"type\": \"string\",\n" +
                "          \"store\": true,\n" +
                "          \"index\": \"not_analyzed\"\n" +
                "        },\n" +
                "        \"virtual\": {\n" +
                "          \"type\": \"string\",\n" +
                "          \"store\": true,\n" +
                "          \"index\": \"not_analyzed\"\n" +
                "        }\n" +
                "      }\n" +
                "    }\n" +
                "  }\n" +
                "}\n"));
    }

    @Test
    public void fsSettingsForDocSpecificJobVersion2() throws Exception {
        String settings = readJsonFile(metadataDir.resolve("jobtest").resolve("_mappings"), metadataDir, "2", INDEX_SETTINGS_FILE);
        assertThat(settings, is("{\n" +
                "  // This is settings for version 2\n" +
                "}\n"));
    }

    @Test
    public void fsSettingsForFolderSpecificJobVersion2() throws Exception {
        String settings = readJsonFile(metadataDir.resolve("jobtest").resolve("_mappings"), metadataDir, "2", INDEX_SETTINGS_FOLDER_FILE);
        assertThat(settings, is("{\n" +
                "  // This is folder settings for version 2\n" +
                "}\n"));
    }

    @Test
    public void fsSettingsForDocVersion5() throws Exception {
        String settings = readJsonFile(rootTmpDir, metadataDir, "5", INDEX_SETTINGS_FILE);
        logger.info("Settings used for doc index v5 : " + settings);
        assertThat(settings, is("{\n" +
                "  \"settings\": {\n" +
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
                "    \"doc\": {\n" +
                "      \"properties\" : {\n" +
                "        \"attachment\" : {\n" +
                "          \"type\" : \"binary\",\n" +
                "          \"doc_values\": false\n" +
                "        },\n" +
                "        \"attributes\" : {\n" +
                "          \"properties\" : {\n" +
                "            \"group\" : {\n" +
                "              \"type\" : \"keyword\"\n" +
                "            },\n" +
                "            \"owner\" : {\n" +
                "              \"type\" : \"keyword\"\n" +
                "            }\n" +
                "          }\n" +
                "        },\n" +
                "        \"content\" : {\n" +
                "          \"type\" : \"text\"\n" +
                "        },\n" +
                "        \"file\" : {\n" +
                "          \"properties\" : {\n" +
                "            \"content_type\" : {\n" +
                "              \"type\" : \"keyword\"\n" +
                "            },\n" +
                "            \"filename\" : {\n" +
                "              \"type\" : \"keyword\",\n" +
                "              \"store\" : true\n" +
                "            },\n" +
                "            \"extension\" : {\n" +
                "              \"type\" : \"keyword\"\n" +
                "            },\n" +
                "            \"filesize\" : {\n" +
                "              \"type\" : \"long\"\n" +
                "            },\n" +
                "            \"indexed_chars\" : {\n" +
                "              \"type\" : \"long\"\n" +
                "            },\n" +
                "            \"indexing_date\" : {\n" +
                "              \"type\" : \"date\",\n" +
                "              \"format\" : \"dateOptionalTime\"\n" +
                "            },\n" +
                "            \"last_modified\" : {\n" +
                "              \"type\" : \"date\",\n" +
                "              \"format\" : \"dateOptionalTime\"\n" +
                "            },\n" +
                "            \"checksum\": {\n" +
                "              \"type\": \"keyword\"\n" +
                "            },\n" +
                "            \"url\" : {\n" +
                "              \"type\" : \"keyword\",\n" +
                "              \"index\" : false\n" +
                "            }\n" +
                "          }\n" +
                "        },\n" +
                "        \"meta\" : {\n" +
                "          \"properties\" : {\n" +
                "            \"author\" : {\n" +
                "              \"type\" : \"text\"\n" +
                "            },\n" +
                "            \"date\" : {\n" +
                "              \"type\" : \"date\",\n" +
                "              \"format\" : \"dateOptionalTime\"\n" +
                "            },\n" +
                "            \"keywords\" : {\n" +
                "              \"type\" : \"text\"\n" +
                "            },\n" +
                "            \"title\" : {\n" +
                "              \"type\" : \"text\"\n" +
                "            },\n" +
                "            \"language\" : {\n" +
                "              \"type\" : \"keyword\"\n" +
                "            },\n" +
                "            \"format\" : {\n" +
                "              \"type\" : \"text\"\n" +
                "            },\n" +
                "            \"identifier\" : {\n" +
                "              \"type\" : \"text\"\n" +
                "            },\n" +
                "            \"contributor\" : {\n" +
                "              \"type\" : \"text\"\n" +
                "            },\n" +
                "            \"coverage\" : {\n" +
                "              \"type\" : \"text\"\n" +
                "            },\n" +
                "            \"modifier\" : {\n" +
                "              \"type\" : \"text\"\n" +
                "            },\n" +
                "            \"creator_tool\" : {\n" +
                "              \"type\" : \"keyword\"\n" +
                "            },\n" +
                "            \"publisher\" : {\n" +
                "              \"type\" : \"text\"\n" +
                "            },\n" +
                "            \"relation\" : {\n" +
                "              \"type\" : \"text\"\n" +
                "            },\n" +
                "            \"rights\" : {\n" +
                "              \"type\" : \"text\"\n" +
                "            },\n" +
                "            \"source\" : {\n" +
                "              \"type\" : \"text\"\n" +
                "            },\n" +
                "            \"type\" : {\n" +
                "              \"type\" : \"text\"\n" +
                "            },\n" +
                "            \"description\" : {\n" +
                "              \"type\" : \"text\"\n" +
                "            },\n" +
                "            \"created\" : {\n" +
                "              \"type\" : \"date\",\n" +
                "              \"format\" : \"dateOptionalTime\"\n" +
                "            },\n" +
                "            \"print_date\" : {\n" +
                "              \"type\" : \"date\",\n" +
                "              \"format\" : \"dateOptionalTime\"\n" +
                "            },\n" +
                "            \"metadata_date\" : {\n" +
                "              \"type\" : \"date\",\n" +
                "              \"format\" : \"dateOptionalTime\"\n" +
                "            },\n" +
                "            \"latitude\" : {\n" +
                "              \"type\" : \"text\"\n" +
                "            },\n" +
                "            \"longitude\" : {\n" +
                "              \"type\" : \"text\"\n" +
                "            },\n" +
                "            \"altitude\" : {\n" +
                "              \"type\" : \"text\"\n" +
                "            },\n" +
                "            \"rating\" : {\n" +
                "              \"type\" : \"byte\"\n" +
                "            },\n" +
                "            \"comments\" : {\n" +
                "              \"type\" : \"text\"\n" +
                "            }\n" +
                "          }\n" +
                "        },\n" +
                "        \"path\" : {\n" +
                "          \"properties\" : {\n" +
                "            \"real\" : {\n" +
                "              \"type\" : \"keyword\",\n" +
                "              \"fields\": {\n" +
                "                \"tree\": {\n" +
                "                  \"type\" : \"text\",\n" +
                "                  \"analyzer\": \"fscrawler_path\",\n" +
                "                  \"fielddata\": true\n" +
                "                }\n" +
                "              }\n" +
                "            },\n" +
                "            \"root\" : {\n" +
                "              \"type\" : \"keyword\"\n" +
                "            },\n" +
                "            \"virtual\" : {\n" +
                "              \"type\" : \"keyword\",\n" +
                "              \"fields\": {\n" +
                "                \"tree\": {\n" +
                "                  \"type\" : \"text\",\n" +
                "                  \"analyzer\": \"fscrawler_path\",\n" +
                "                  \"fielddata\": true\n" +
                "                }\n" +
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
    public void fsSettingsForFolderVersion5() throws Exception {
        String settings = readJsonFile(rootTmpDir, metadataDir, "5", INDEX_SETTINGS_FOLDER_FILE);
        logger.info("Settings used for folder index v5 : " + settings);
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
                "    \"doc\": {\n" +
                "      \"properties\" : {\n" +
                "        \"real\" : {\n" +
                "          \"type\" : \"keyword\",\n" +
                "          \"store\" : true\n" +
                "        },\n" +
                "        \"root\" : {\n" +
                "          \"type\" : \"keyword\",\n" +
                "          \"store\" : true\n" +
                "        },\n" +
                "        \"virtual\" : {\n" +
                "          \"type\" : \"keyword\",\n" +
                "          \"store\" : true\n" +
                "        }\n" +
                "      }\n" +
                "    }\n" +
                "  }\n" +
                "}\n"));
    }

    @Test
    public void fsSettingsForDocSpecificJobVersion5() throws Exception {
        String settings = readJsonFile(metadataDir.resolve("jobtest").resolve("_mappings"), metadataDir, "5", INDEX_SETTINGS_FILE);
        assertThat(settings, is("{\n" +
                "  // This is settings for version 5\n" +
                "}\n"));
    }

    @Test
    public void fsSettingsForFolderSpecificJobVersion5() throws Exception {
        String settings = readJsonFile(metadataDir.resolve("jobtest").resolve("_mappings"), metadataDir, "5", INDEX_SETTINGS_FOLDER_FILE);
        assertThat(settings, is("{\n" +
                "  // This is folder settings for version 5\n" +
                "}\n"));
    }

    @Test
    public void fsSettingsForDocVersion6() throws Exception {
        String settings = readJsonFile(rootTmpDir, metadataDir, "6", INDEX_SETTINGS_FILE);
        logger.info("Settings used for doc index v6 : " + settings);
        assertThat(settings, is("{\n" +
                "  \"settings\": {\n" +
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
                "    \"doc\": {\n" +
                "      \"properties\" : {\n" +
                "        \"attachment\" : {\n" +
                "          \"type\" : \"binary\",\n" +
                "          \"doc_values\": false\n" +
                "        },\n" +
                "        \"attributes\" : {\n" +
                "          \"properties\" : {\n" +
                "            \"group\" : {\n" +
                "              \"type\" : \"keyword\"\n" +
                "            },\n" +
                "            \"owner\" : {\n" +
                "              \"type\" : \"keyword\"\n" +
                "            }\n" +
                "          }\n" +
                "        },\n" +
                "        \"content\" : {\n" +
                "          \"type\" : \"text\"\n" +
                "        },\n" +
                "        \"file\" : {\n" +
                "          \"properties\" : {\n" +
                "            \"content_type\" : {\n" +
                "              \"type\" : \"keyword\"\n" +
                "            },\n" +
                "            \"filename\" : {\n" +
                "              \"type\" : \"keyword\",\n" +
                "              \"store\" : true\n" +
                "            },\n" +
                "            \"extension\" : {\n" +
                "              \"type\" : \"keyword\"\n" +
                "            },\n" +
                "            \"filesize\" : {\n" +
                "              \"type\" : \"long\"\n" +
                "            },\n" +
                "            \"indexed_chars\" : {\n" +
                "              \"type\" : \"long\"\n" +
                "            },\n" +
                "            \"indexing_date\" : {\n" +
                "              \"type\" : \"date\",\n" +
                "              \"format\" : \"dateOptionalTime\"\n" +
                "            },\n" +
                "            \"last_modified\" : {\n" +
                "              \"type\" : \"date\",\n" +
                "              \"format\" : \"dateOptionalTime\"\n" +
                "            },\n" +
                "            \"checksum\": {\n" +
                "              \"type\": \"keyword\"\n" +
                "            },\n" +
                "            \"url\" : {\n" +
                "              \"type\" : \"keyword\",\n" +
                "              \"index\" : false\n" +
                "            }\n" +
                "          }\n" +
                "        },\n" +
                "        \"meta\" : {\n" +
                "          \"properties\" : {\n" +
                "            \"author\" : {\n" +
                "              \"type\" : \"text\"\n" +
                "            },\n" +
                "            \"date\" : {\n" +
                "              \"type\" : \"date\",\n" +
                "              \"format\" : \"dateOptionalTime\"\n" +
                "            },\n" +
                "            \"keywords\" : {\n" +
                "              \"type\" : \"text\"\n" +
                "            },\n" +
                "            \"title\" : {\n" +
                "              \"type\" : \"text\"\n" +
                "            },\n" +
                "            \"language\" : {\n" +
                "              \"type\" : \"keyword\"\n" +
                "            },\n" +
                "            \"format\" : {\n" +
                "              \"type\" : \"text\"\n" +
                "            },\n" +
                "            \"identifier\" : {\n" +
                "              \"type\" : \"text\"\n" +
                "            },\n" +
                "            \"contributor\" : {\n" +
                "              \"type\" : \"text\"\n" +
                "            },\n" +
                "            \"coverage\" : {\n" +
                "              \"type\" : \"text\"\n" +
                "            },\n" +
                "            \"modifier\" : {\n" +
                "              \"type\" : \"text\"\n" +
                "            },\n" +
                "            \"creator_tool\" : {\n" +
                "              \"type\" : \"keyword\"\n" +
                "            },\n" +
                "            \"publisher\" : {\n" +
                "              \"type\" : \"text\"\n" +
                "            },\n" +
                "            \"relation\" : {\n" +
                "              \"type\" : \"text\"\n" +
                "            },\n" +
                "            \"rights\" : {\n" +
                "              \"type\" : \"text\"\n" +
                "            },\n" +
                "            \"source\" : {\n" +
                "              \"type\" : \"text\"\n" +
                "            },\n" +
                "            \"type\" : {\n" +
                "              \"type\" : \"text\"\n" +
                "            },\n" +
                "            \"description\" : {\n" +
                "              \"type\" : \"text\"\n" +
                "            },\n" +
                "            \"created\" : {\n" +
                "              \"type\" : \"date\",\n" +
                "              \"format\" : \"dateOptionalTime\"\n" +
                "            },\n" +
                "            \"print_date\" : {\n" +
                "              \"type\" : \"date\",\n" +
                "              \"format\" : \"dateOptionalTime\"\n" +
                "            },\n" +
                "            \"metadata_date\" : {\n" +
                "              \"type\" : \"date\",\n" +
                "              \"format\" : \"dateOptionalTime\"\n" +
                "            },\n" +
                "            \"latitude\" : {\n" +
                "              \"type\" : \"text\"\n" +
                "            },\n" +
                "            \"longitude\" : {\n" +
                "              \"type\" : \"text\"\n" +
                "            },\n" +
                "            \"altitude\" : {\n" +
                "              \"type\" : \"text\"\n" +
                "            },\n" +
                "            \"rating\" : {\n" +
                "              \"type\" : \"byte\"\n" +
                "            },\n" +
                "            \"comments\" : {\n" +
                "              \"type\" : \"text\"\n" +
                "            }\n" +
                "          }\n" +
                "        },\n" +
                "        \"path\" : {\n" +
                "          \"properties\" : {\n" +
                "            \"real\" : {\n" +
                "              \"type\" : \"keyword\",\n" +
                "              \"fields\": {\n" +
                "                \"tree\": {\n" +
                "                  \"type\" : \"text\",\n" +
                "                  \"analyzer\": \"fscrawler_path\",\n" +
                "                  \"fielddata\": true\n" +
                "                }\n" +
                "              }\n" +
                "            },\n" +
                "            \"root\" : {\n" +
                "              \"type\" : \"keyword\"\n" +
                "            },\n" +
                "            \"virtual\" : {\n" +
                "              \"type\" : \"keyword\",\n" +
                "              \"fields\": {\n" +
                "                \"tree\": {\n" +
                "                  \"type\" : \"text\",\n" +
                "                  \"analyzer\": \"fscrawler_path\",\n" +
                "                  \"fielddata\": true\n" +
                "                }\n" +
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
        String settings = readJsonFile(rootTmpDir, metadataDir, "6", INDEX_SETTINGS_FOLDER_FILE);
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
                "    \"doc\": {\n" +
                "      \"properties\" : {\n" +
                "        \"real\" : {\n" +
                "          \"type\" : \"keyword\",\n" +
                "          \"store\" : true\n" +
                "        },\n" +
                "        \"root\" : {\n" +
                "          \"type\" : \"keyword\",\n" +
                "          \"store\" : true\n" +
                "        },\n" +
                "        \"virtual\" : {\n" +
                "          \"type\" : \"keyword\",\n" +
                "          \"store\" : true\n" +
                "        }\n" +
                "      }\n" +
                "    }\n" +
                "  }\n" +
                "}\n"));
    }

    @Test
    public void fsSettingsForDocSpecificJobVersion6() throws Exception {
        String settings = readJsonFile(metadataDir.resolve("jobtest").resolve("_mappings"), metadataDir, "6", INDEX_SETTINGS_FILE);
        assertThat(settings, is("{\n" +
                "  // This is settings for version 6\n" +
                "}\n"));
    }

    @Test
    public void fsSettingsForFolderSpecificJobVersion6() throws Exception {
        String settings = readJsonFile(metadataDir.resolve("jobtest").resolve("_mappings"), metadataDir, "6", INDEX_SETTINGS_FOLDER_FILE);
        assertThat(settings, is("{\n" +
                "  // This is folder settings for version 6\n" +
                "}\n"));
    }

    @Test
    public void fsSettingsForDocVersionNotSupported() throws Exception {
        try {
            readJsonFile(rootTmpDir, metadataDir, "0", INDEX_SETTINGS_FILE);
            fail("We should have thrown an exception for an unknown elasticsearch version");
        } catch (IllegalArgumentException ignored) {
            assertThat(ignored.getMessage(), containsString("does not exist for elasticsearch version"));
        }
    }

    @Test
    public void fsSettingsForFolderVersionNotSupported() throws Exception {
        try {
            readJsonFile(rootTmpDir, metadataDir, "0", INDEX_SETTINGS_FOLDER_FILE);
            fail("We should have thrown an exception for an unknown elasticsearch version");
        } catch (IllegalArgumentException ignored) {
            assertThat(ignored.getMessage(), containsString("does not exist for elasticsearch version"));
        }
    }
}
