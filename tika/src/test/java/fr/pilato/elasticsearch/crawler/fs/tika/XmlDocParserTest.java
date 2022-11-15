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

package fr.pilato.elasticsearch.crawler.fs.tika;

import org.junit.Test;

import java.io.IOException;
import java.io.InputStream;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;

public class XmlDocParserTest extends DocParserTestCase {

    @Test
    public void testXml() throws IOException {
        String doc = extractFromFile("issue-163.xml");
        assertThat(doc, is("{\"version\":\"1.0\",\"subscription-update\":{\"subscriptionid\":\"0\",\"requestid\":\"0\"," +
                "\"last_push\":\"2016-06-03 06:21:34\",\"current_push\":\"2016-06-03 06:21:37\",\"exec\":\"0.002\"," +
                "\"lineup\":{\"id\":\"0\",\"del\":\"no\"}}}"));
    }

    @Test
    public void testXmlNestedObjects() throws IOException {
        String doc = extractFromFile("issue-592.xml");
        assertThat(doc, is("{\"object\":[{\"id\":\"1\",\"name\":\"foo\"},{\"id\":\"2\",\"name\":\"bar\"}]}"));
    }

    private String extractFromFile(String filename) {
        InputStream data = getBinaryContent(filename);
        return XmlDocParser.generate(data);
    }
}
