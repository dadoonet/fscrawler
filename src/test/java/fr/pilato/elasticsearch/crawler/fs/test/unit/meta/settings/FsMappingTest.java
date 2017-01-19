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


import fr.pilato.elasticsearch.crawler.fs.test.AbstractFSCrawlerTestCase;
import fr.pilato.elasticsearch.crawler.fs.util.FsCrawlerUtil;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.junit.BeforeClass;
import org.junit.Test;

import java.io.IOException;
import java.net.URISyntaxException;
import java.nio.file.Path;

import static fr.pilato.elasticsearch.crawler.fs.util.FsCrawlerUtil.copyResourceFile;
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

        for (String filename : FsCrawlerUtil.MAPPING_RESOURCES) {
            staticLogger.debug("Copying [{}]...", filename);
            Path target = targetResourceDir.resolve(filename);
            copyResourceFile(CLASSPATH_RESOURCES_ROOT + filename, target);
        }

        staticLogger.debug("  --> Mappings generated in [{}]", targetResourceDir);
    }


    @Test
    public void fsMappingForFilesVersion1() throws Exception {
        String mapping = FsCrawlerUtil.readJsonFile(rootTmpDir, metadataDir, "1", FsCrawlerUtil.INDEX_TYPE_DOC);
        logger.info("Mapping used for files v1 : " + mapping);
        assertThat(mapping, is("{\n" +
                "  \"properties\": {\n" +
                "    \"content\": {\n" +
                "      \"type\": \"string\"\n" +
                "    },\n" +
                "    \"attachment\": {\n" +
                "      \"type\": \"binary\",\n" +
                "      \"doc_values\": false\n" +
                "    },\n" +
                "    \"meta\": {\n" +
                "      \"properties\": {\n" +
                "        \"author\": {\n" +
                "          \"type\": \"string\"\n" +
                "        },\n" +
                "        \"title\": {\n" +
                "          \"type\": \"string\"\n" +
                "        },\n" +
                "        \"date\": {\n" +
                "          \"type\": \"date\",\n" +
                "          \"format\": \"dateOptionalTime\"\n" +
                "        },\n" +
                "        \"keywords\": {\n" +
                "          \"type\": \"string\"\n" +
                "        },\n" +
                "        \"language\" : {\n" +
                "          \"type\" : \"string\",\n" +
                "          \"index\": \"not_analyzed\"\n" +
                "        }\n" +
                "      }\n" +
                "    },\n" +
                "    \"file\": {\n" +
                "      \"properties\": {\n" +
                "        \"content_type\": {\n" +
                "          \"type\": \"string\",\n" +
                "          \"index\": \"not_analyzed\"\n" +
                "        },\n" +
                "        \"last_modified\": {\n" +
                "          \"type\": \"date\",\n" +
                "          \"format\": \"dateOptionalTime\"\n" +
                "        },\n" +
                "        \"indexing_date\": {\n" +
                "          \"type\": \"date\",\n" +
                "          \"format\": \"dateOptionalTime\"\n" +
                "        },\n" +
                "        \"filesize\": {\n" +
                "          \"type\": \"long\"\n" +
                "        },\n" +
                "        \"indexed_chars\": {\n" +
                "          \"type\": \"long\"\n" +
                "        },\n" +
                "        \"filename\": {\n" +
                "          \"type\": \"string\",\n" +
                "          \"index\": \"not_analyzed\"\n" +
                "        },\n" +
                "        \"extension\": {\n" +
                "          \"type\": \"string\",\n" +
                "          \"index\": \"not_analyzed\"\n" +
                "        },\n" +
                "        \"checksum\": {\n" +
                "          \"type\": \"string\",\n" +
                "          \"index\": \"not_analyzed\"\n" +
                "        },\n" +
                "        \"url\": {\n" +
                "          \"type\": \"string\",\n" +
                "          \"index\": \"no\"\n" +
                "        }\n" +
                "      }\n" +
                "    },\n" +
                "    \"path\": {\n" +
                "      \"properties\": {\n" +
                "        \"encoded\": {\n" +
                "          \"type\": \"string\",\n" +
                "          \"index\": \"not_analyzed\"\n" +
                "        },\n" +
                "        \"virtual\": {\n" +
                "          \"type\": \"string\",\n" +
                "          \"index\": \"not_analyzed\",\n" +
                "          \"fields\": {\n" +
                "            \"tree\": {\n" +
                "              \"type\": \"string\",\n" +
                "              \"analyzer\": \"fscrawler_path\"\n" +
                "            }\n" +
                "          }\n" +
                "        },\n" +
                "        \"root\": {\n" +
                "          \"type\": \"string\",\n" +
                "          \"index\": \"not_analyzed\"\n" +
                "        },\n" +
                "        \"real\": {\n" +
                "          \"type\": \"string\",\n" +
                "          \"index\": \"not_analyzed\",\n" +
                "          \"fields\": {\n" +
                "            \"tree\": {\n" +
                "              \"type\": \"string\",\n" +
                "              \"analyzer\": \"fscrawler_path\"\n" +
                "            }\n" +
                "          }\n" +
                "        }\n" +
                "      }\n" +
                "    },\n" +
                "    \"attributes\": {\n" +
                "      \"properties\": {\n" +
                "        \"owner\": {\n" +
                "          \"type\": \"string\",\n" +
                "          \"index\": \"not_analyzed\"\n" +
                "        },\n" +
                "        \"group\": {\n" +
                "          \"type\": \"string\",\n" +
                "          \"index\": \"not_analyzed\"\n" +
                "        }\n" +
                "      }\n" +
                "    }\n" +
                "  }\n" +
                "}\n"));
    }

    @Test
    public void fsMappingForFoldersVersion1() throws Exception {
        String mapping = FsCrawlerUtil.readJsonFile(rootTmpDir, metadataDir, "1", FsCrawlerUtil.INDEX_TYPE_FOLDER);
        logger.info("Mapping used for folders v1 : " + mapping);
        assertThat(mapping, is("{\n" +
                "  \"properties\": {\n" +
                "    \"name\": {\n" +
                "      \"type\": \"string\",\n" +
                "      \"store\": true,\n" +
                "      \"index\": \"not_analyzed\"\n" +
                "    },\n" +
                "    \"real\": {\n" +
                "      \"type\": \"string\",\n" +
                "      \"store\": true,\n" +
                "      \"index\": \"not_analyzed\"\n" +
                "    },\n" +
                "    \"encoded\": {\n" +
                "      \"type\": \"string\",\n" +
                "      \"store\": true,\n" +
                "      \"index\": \"not_analyzed\"\n" +
                "    },\n" +
                "    \"root\": {\n" +
                "      \"type\": \"string\",\n" +
                "      \"store\": true,\n" +
                "      \"index\": \"not_analyzed\"\n" +
                "    },\n" +
                "    \"virtual\": {\n" +
                "      \"type\": \"string\",\n" +
                "      \"store\": true,\n" +
                "      \"index\": \"not_analyzed\"\n" +
                "    }\n" +
                "  }\n" +
                "}\n"));
    }

    @Test
    public void fsIndexSettingsVersion1() throws Exception {
        String settings = FsCrawlerUtil.readJsonFile(rootTmpDir, metadataDir, "1", FsCrawlerUtil.INDEX_SETTINGS_FILE);
        logger.info("Settings used for index v1 : " + settings);
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
                "  }\n" +
                "}\n"));
    }

    @Test
    public void fsMappingForFilesForSpecificJobVersion1() throws Exception {
        String mapping = FsCrawlerUtil.readJsonFile(metadataDir.resolve("jobtest").resolve("_mappings"), metadataDir, "1", FsCrawlerUtil.INDEX_TYPE_DOC);
        logger.info("Mapping used for files v1 : " + mapping);
        assertThat(mapping, is("{\n" +
                "  // This is a doc mapping version 1\n" +
                "}\n"));
    }

    @Test
    public void fsMappingForFoldersForSpecificJobVersion1() throws Exception {
        String mapping = FsCrawlerUtil.readJsonFile(metadataDir.resolve("jobtest").resolve("_mappings"), metadataDir, "1", FsCrawlerUtil.INDEX_TYPE_FOLDER);
        logger.info("Mapping used for files v1 : " + mapping);
        assertThat(mapping, is("{\n" +
                "  // This is a folder mapping version 1\n" +
                "}\n"));
    }

    @Test
    public void fsIndexSettingsForSpecificJobVersion1() throws Exception {
        String settings = FsCrawlerUtil.readJsonFile(metadataDir.resolve("jobtest").resolve("_mappings"), metadataDir, "1", FsCrawlerUtil.INDEX_SETTINGS_FILE);
        logger.info("Settings used for index v1 : " + settings);
        assertThat(settings, is("{\n" +
                "  // This is settings for version 1\n" +
                "}\n"));
    }

    @Test
    public void fsMappingForFilesVersion2() throws Exception {
        String mapping = FsCrawlerUtil.readJsonFile(rootTmpDir, metadataDir, "2", FsCrawlerUtil.INDEX_TYPE_DOC);
        logger.info("Mapping used for files v2 : " + mapping);
        assertThat(mapping, is("{\n" +
                "  \"properties\": {\n" +
                "    \"content\": {\n" +
                "      \"type\": \"string\"\n" +
                "    },\n" +
                "    \"attachment\": {\n" +
                "      \"type\": \"binary\",\n" +
                "      \"doc_values\": false\n" +
                "    },\n" +
                "    \"meta\": {\n" +
                "      \"properties\": {\n" +
                "        \"author\": {\n" +
                "          \"type\": \"string\"\n" +
                "        },\n" +
                "        \"title\": {\n" +
                "          \"type\": \"string\"\n" +
                "        },\n" +
                "        \"date\": {\n" +
                "          \"type\": \"date\",\n" +
                "          \"format\": \"dateOptionalTime\"\n" +
                "        },\n" +
                "        \"keywords\": {\n" +
                "          \"type\": \"string\"\n" +
                "        },\n" +
                "        \"language\" : {\n" +
                "          \"type\" : \"string\",\n" +
                "          \"index\": \"not_analyzed\"\n" +
                "        }\n" +
                "      }\n" +
                "    },\n" +
                "    \"file\": {\n" +
                "      \"properties\": {\n" +
                "        \"content_type\": {\n" +
                "          \"type\": \"string\",\n" +
                "          \"index\": \"not_analyzed\"\n" +
                "        },\n" +
                "        \"last_modified\": {\n" +
                "          \"type\": \"date\",\n" +
                "          \"format\": \"dateOptionalTime\"\n" +
                "        },\n" +
                "        \"indexing_date\": {\n" +
                "          \"type\": \"date\",\n" +
                "          \"format\": \"dateOptionalTime\"\n" +
                "        },\n" +
                "        \"filesize\": {\n" +
                "          \"type\": \"long\"\n" +
                "        },\n" +
                "        \"indexed_chars\": {\n" +
                "          \"type\": \"long\"\n" +
                "        },\n" +
                "        \"filename\": {\n" +
                "          \"type\": \"string\",\n" +
                "          \"index\": \"not_analyzed\"\n" +
                "        },\n" +
                "        \"extension\": {\n" +
                "          \"type\": \"string\",\n" +
                "          \"index\": \"not_analyzed\"\n" +
                "        },\n" +
                "        \"checksum\": {\n" +
                "          \"type\": \"string\",\n" +
                "          \"index\": \"not_analyzed\"\n" +
                "        },\n" +
                "        \"url\": {\n" +
                "          \"type\": \"string\",\n" +
                "          \"index\": \"no\"\n" +
                "        }\n" +
                "      }\n" +
                "    },\n" +
                "    \"path\": {\n" +
                "      \"properties\": {\n" +
                "        \"encoded\": {\n" +
                "          \"type\": \"string\",\n" +
                "          \"index\": \"not_analyzed\"\n" +
                "        },\n" +
                "        \"virtual\": {\n" +
                "          \"type\": \"string\",\n" +
                "          \"index\": \"not_analyzed\",\n" +
                "          \"fields\": {\n" +
                "            \"tree\": {\n" +
                "              \"type\": \"string\",\n" +
                "              \"analyzer\": \"fscrawler_path\"\n" +
                "            }\n" +
                "          }\n" +
                "        },\n" +
                "        \"root\": {\n" +
                "          \"type\": \"string\",\n" +
                "          \"index\": \"not_analyzed\"\n" +
                "        },\n" +
                "        \"real\": {\n" +
                "          \"type\": \"string\",\n" +
                "          \"index\": \"not_analyzed\",\n" +
                "          \"fields\": {\n" +
                "            \"tree\": {\n" +
                "              \"type\": \"string\",\n" +
                "              \"analyzer\": \"fscrawler_path\"\n" +
                "            }\n" +
                "          }\n" +
                "        }\n" +
                "      }\n" +
                "    },\n" +
                "    \"attributes\": {\n" +
                "      \"properties\": {\n" +
                "        \"owner\": {\n" +
                "          \"type\": \"string\",\n" +
                "          \"index\": \"not_analyzed\"\n" +
                "        },\n" +
                "        \"group\": {\n" +
                "          \"type\": \"string\",\n" +
                "          \"index\": \"not_analyzed\"\n" +
                "        }\n" +
                "      }\n" +
                "    }\n" +
                "  }\n" +
                "}\n"));
    }

    @Test
    public void fsMappingForFoldersVersion2() throws Exception {
        String mapping = FsCrawlerUtil.readJsonFile(rootTmpDir, metadataDir, "2", FsCrawlerUtil.INDEX_TYPE_FOLDER);
        logger.info("Mapping used for folders v2 : " + mapping);
        assertThat(mapping, is("{\n" +
                "  \"properties\": {\n" +
                "    \"name\": {\n" +
                "      \"type\": \"string\",\n" +
                "      \"store\": true,\n" +
                "      \"index\": \"not_analyzed\"\n" +
                "    },\n" +
                "    \"real\": {\n" +
                "      \"type\": \"string\",\n" +
                "      \"store\": true,\n" +
                "      \"index\": \"not_analyzed\"\n" +
                "    },\n" +
                "    \"encoded\": {\n" +
                "      \"type\": \"string\",\n" +
                "      \"store\": true,\n" +
                "      \"index\": \"not_analyzed\"\n" +
                "    },\n" +
                "    \"root\": {\n" +
                "      \"type\": \"string\",\n" +
                "      \"store\": true,\n" +
                "      \"index\": \"not_analyzed\"\n" +
                "    },\n" +
                "    \"virtual\": {\n" +
                "      \"type\": \"string\",\n" +
                "      \"store\": true,\n" +
                "      \"index\": \"not_analyzed\"\n" +
                "    }\n" +
                "  }\n" +
                "}\n"));
    }

    @Test
    public void fsIndexSettingsVersion2() throws Exception {
        String settings = FsCrawlerUtil.readJsonFile(rootTmpDir, metadataDir, "2", FsCrawlerUtil.INDEX_SETTINGS_FILE);
        logger.info("Settings used for index v2 : " + settings);
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
                "  }\n" +
                "}\n"));
    }

    @Test
    public void fsMappingForFilesForSpecificJobVersion2() throws Exception {
        String mapping = FsCrawlerUtil.readJsonFile(metadataDir.resolve("jobtest").resolve("_mappings"), metadataDir, "2", FsCrawlerUtil.INDEX_TYPE_DOC);
        logger.info("Mapping used for files v2 : " + mapping);
        assertThat(mapping, is("{\n" +
                "  // This is a doc mapping version 2\n" +
                "}\n"));
    }

    @Test
    public void fsMappingForFoldersForSpecificJobVersion2() throws Exception {
        String mapping = FsCrawlerUtil.readJsonFile(metadataDir.resolve("jobtest").resolve("_mappings"), metadataDir, "2", FsCrawlerUtil.INDEX_TYPE_FOLDER);
        logger.info("Mapping used for folders v2 : " + mapping);
        assertThat(mapping, is("{\n" +
                "  // This is a folder mapping version 2\n" +
                "}\n"));
    }

    @Test
    public void fsIndexSettingsForSpecificJobVersion2() throws Exception {
        String settings = FsCrawlerUtil.readJsonFile(metadataDir.resolve("jobtest").resolve("_mappings"), metadataDir, "2", FsCrawlerUtil.INDEX_SETTINGS_FILE);
        logger.info("Settings used for index v2 : " + settings);
        assertThat(settings, is("{\n" +
                "  // This is settings for version 2\n" +
                "}\n"));
    }

    @Test
    public void fsMappingForFilesVersion5() throws Exception {
        String mapping = FsCrawlerUtil.readJsonFile(rootTmpDir, metadataDir, "5", FsCrawlerUtil.INDEX_TYPE_DOC);
        logger.info("Mapping used for files v5 : " + mapping);
        assertThat(mapping, is("{\n" +
                "  \"properties\" : {\n" +
                "    \"attachment\" : {\n" +
                "      \"type\" : \"binary\",\n" +
                "      \"doc_values\": false\n" +
                "    },\n" +
                "    \"attributes\" : {\n" +
                "      \"properties\" : {\n" +
                "        \"group\" : {\n" +
                "          \"type\" : \"keyword\"\n" +
                "        },\n" +
                "        \"owner\" : {\n" +
                "          \"type\" : \"keyword\"\n" +
                "        }\n" +
                "      }\n" +
                "    },\n" +
                "    \"content\" : {\n" +
                "      \"type\" : \"text\"\n" +
                "    },\n" +
                "    \"file\" : {\n" +
                "      \"properties\" : {\n" +
                "        \"content_type\" : {\n" +
                "          \"type\" : \"keyword\"\n" +
                "        },\n" +
                "        \"filename\" : {\n" +
                "          \"type\" : \"keyword\"\n" +
                "        },\n" +
                "        \"extension\" : {\n" +
                "          \"type\" : \"keyword\"\n" +
                "        },\n" +
                "        \"filesize\" : {\n" +
                "          \"type\" : \"long\"\n" +
                "        },\n" +
                "        \"indexed_chars\" : {\n" +
                "          \"type\" : \"long\"\n" +
                "        },\n" +
                "        \"indexing_date\" : {\n" +
                "          \"type\" : \"date\",\n" +
                "          \"format\" : \"dateOptionalTime\"\n" +
                "        },\n" +
                "        \"last_modified\" : {\n" +
                "          \"type\" : \"date\",\n" +
                "          \"format\" : \"dateOptionalTime\"\n" +
                "        },\n" +
                "        \"checksum\": {\n" +
                "          \"type\": \"keyword\"\n" +
                "        },\n" +
                "        \"url\" : {\n" +
                "          \"type\" : \"keyword\",\n" +
                "          \"index\" : false\n" +
                "        }\n" +
                "      }\n" +
                "    },\n" +
                "    \"meta\" : {\n" +
                "      \"properties\" : {\n" +
                "        \"author\" : {\n" +
                "          \"type\" : \"text\"\n" +
                "        },\n" +
                "        \"date\" : {\n" +
                "          \"type\" : \"date\",\n" +
                "          \"format\" : \"dateOptionalTime\"\n" +
                "        },\n" +
                "        \"keywords\" : {\n" +
                "          \"type\" : \"text\"\n" +
                "        },\n" +
                "        \"title\" : {\n" +
                "          \"type\" : \"text\"\n" +
                "        },\n" +
                "        \"language\" : {\n" +
                "          \"type\" : \"keyword\"\n" +
                "        }\n" +
                "      }\n" +
                "    },\n" +
                "    \"path\" : {\n" +
                "      \"properties\" : {\n" +
                "        \"encoded\" : {\n" +
                "          \"type\" : \"keyword\"\n" +
                "        },\n" +
                "        \"real\" : {\n" +
                "          \"type\" : \"keyword\",\n" +
                "          \"fields\": {\n" +
                "            \"tree\": {\n" +
                "              \"type\" : \"text\",\n" +
                "              \"analyzer\": \"fscrawler_path\",\n" +
                "              \"fielddata\": true\n" +
                "            }\n" +
                "          }\n" +
                "        },\n" +
                "        \"root\" : {\n" +
                "          \"type\" : \"keyword\"\n" +
                "        },\n" +
                "        \"virtual\" : {\n" +
                "          \"type\" : \"keyword\",\n" +
                "          \"fields\": {\n" +
                "            \"tree\": {\n" +
                "              \"type\" : \"text\",\n" +
                "              \"analyzer\": \"fscrawler_path\",\n" +
                "              \"fielddata\": true\n" +
                "            }\n" +
                "          }\n" +
                "        }\n" +
                "      }\n" +
                "    }\n" +
                "  }\n" +
                "}\n"));
    }

    @Test
    public void fsMappingForFoldersVersion5() throws Exception {
        String mapping = FsCrawlerUtil.readJsonFile(rootTmpDir, metadataDir, "5", FsCrawlerUtil.INDEX_TYPE_FOLDER);
        logger.info("Mapping used for folders v5 : " + mapping);
        assertThat(mapping, is("{\n" +
                "  \"properties\" : {\n" +
                "    \"encoded\" : {\n" +
                "      \"type\" : \"keyword\",\n" +
                "      \"store\" : true\n" +
                "    },\n" +
                "    \"name\" : {\n" +
                "      \"type\" : \"keyword\",\n" +
                "      \"store\" : true\n" +
                "    },\n" +
                "    \"real\" : {\n" +
                "      \"type\" : \"keyword\",\n" +
                "      \"store\" : true\n" +
                "    },\n" +
                "    \"root\" : {\n" +
                "      \"type\" : \"keyword\",\n" +
                "      \"store\" : true\n" +
                "    },\n" +
                "    \"virtual\" : {\n" +
                "      \"type\" : \"keyword\",\n" +
                "      \"store\" : true\n" +
                "    }\n" +
                "  }\n" +
                "}\n"));
    }

    @Test
    public void fsIndexSettingsVersion5() throws Exception {
        String settings = FsCrawlerUtil.readJsonFile(rootTmpDir, metadataDir, "5", FsCrawlerUtil.INDEX_SETTINGS_FILE);
        logger.info("Settings used for index v5 : " + settings);
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
                "  }\n" +
                "}\n"));
    }

    @Test
    public void fsMappingForFilesForSpecificJobVersion5() throws Exception {
        String mapping = FsCrawlerUtil.readJsonFile(metadataDir.resolve("jobtest").resolve("_mappings"), metadataDir, "5", FsCrawlerUtil.INDEX_TYPE_DOC);
        logger.info("Mapping used for files v5 : " + mapping);
        assertThat(mapping, is("{\n" +
                "  // This is a doc mapping version 5\n" +
                "}\n"));
    }

    @Test
    public void fsMappingForFoldersForSpecificJobVersion5() throws Exception {
        String mapping = FsCrawlerUtil.readJsonFile(metadataDir.resolve("jobtest").resolve("_mappings"), metadataDir, "5", FsCrawlerUtil.INDEX_TYPE_FOLDER);
        logger.info("Mapping used for folders v5 : " + mapping);
        assertThat(mapping, is("{\n" +
                "  // This is a folder mapping version 5\n" +
                "}\n"));
    }

    @Test
    public void fsIndexSettingsForSpecificJobVersion5() throws Exception {
        String mapping = FsCrawlerUtil.readJsonFile(metadataDir.resolve("jobtest").resolve("_mappings"), metadataDir, "5", FsCrawlerUtil.INDEX_SETTINGS_FILE);
        logger.info("Settings used for index v5 : " + mapping);
        assertThat(mapping, is("{\n" +
                "  // This is settings for version 5\n" +
                "}\n"));
    }

    @Test
    public void fsMappingForFilesVersionNotSupported() throws Exception {
        try {
            FsCrawlerUtil.readJsonFile(rootTmpDir, metadataDir, "0", FsCrawlerUtil.INDEX_TYPE_DOC);
            fail("We should have thrown an exception for an unknown elasticsearch version");
        } catch (IllegalArgumentException ignored) {
            assertThat(ignored.getMessage(), containsString("does not exist for elasticsearch version"));
        }
    }

    @Test
    public void fsMappingForFoldersVersionNotSupported() throws Exception {
        try {
            FsCrawlerUtil.readJsonFile(rootTmpDir, metadataDir, "0", FsCrawlerUtil.INDEX_TYPE_FOLDER);
            fail("We should have thrown an exception for an unknown elasticsearch version");
        } catch (IllegalArgumentException ignored) {
            assertThat(ignored.getMessage(), containsString("does not exist for elasticsearch version"));
        }
    }

    @Test
    public void fsIndexSettingsVersionNotSupported() throws Exception {
        try {
            FsCrawlerUtil.readJsonFile(rootTmpDir, metadataDir, "0", FsCrawlerUtil.INDEX_SETTINGS_FILE);
            fail("We should have thrown an exception for an unknown elasticsearch version");
        } catch (IllegalArgumentException ignored) {
            assertThat(ignored.getMessage(), containsString("does not exist for elasticsearch version"));
        }
    }
}
