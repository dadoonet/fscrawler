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

package fr.pilato.elasticsearch.crawler.fs.test.unit.tika;

import fr.pilato.elasticsearch.crawler.fs.test.AbstractFSCrawlerTestCase;
import fr.pilato.elasticsearch.crawler.fs.tika.XmlDocParser;
import org.junit.Test;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Paths;

public class XmlDocParserTest extends AbstractFSCrawlerTestCase {

    @Test
    public void testXml() throws IOException {
        String doc = extractFromFile("issue-163.xml");
        logger.info("-> {}", doc);
    }

    private InputStream getBinaryContent(String filename) throws IOException {
        return Files.newInputStream(Paths.get(getUrl("documents", filename)));
    }

    private String extractFromFile(String filename) throws IOException {
        InputStream data = getBinaryContent(filename);
        return XmlDocParser.generate(data);
    }
}
