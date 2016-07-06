/*
 * Licensed to Elasticsearch under one or more contributor
 * license agreements. See the NOTICE file distributed with
 * this work for additional information regarding copyright
 * ownership. Elasticsearch licenses this file to you under
 * the Apache License, Version 2.0 (the "License"); you may
 * not use this file except in compliance with the License.
 * You may obtain a copy of the License at
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

import fr.pilato.elasticsearch.crawler.fs.meta.doc.Doc;
import fr.pilato.elasticsearch.crawler.fs.meta.settings.FsSettings;
import fr.pilato.elasticsearch.crawler.fs.test.AbstractFSCrawlerTest;
import fr.pilato.elasticsearch.crawler.fs.tika.TikaDocParser;
import org.junit.Test;

import java.io.IOException;
import java.io.InputStream;
import java.io.UnsupportedEncodingException;
import java.net.URL;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.emptyIterable;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.nullValue;

public class TikaDocParserTest extends AbstractFSCrawlerTest {

    @Test
    public void testExtractFromOdt() throws IOException {
        Doc doc = extractFromFile("odt");

        // Extracted content
        assertThat(doc.getContent(), is("Bonjour David\n\n\n"));

        // Content Type
        assertThat(doc.getFile().getContentType(), is("application/vnd.oasis.opendocument.text"));

        // Meta data
        assertThat(doc.getMeta().getAuthor(), is(nullValue()));
        assertThat(doc.getMeta().getDate(), is(nullValue()));
        assertThat(doc.getMeta().getKeywords(), containsInAnyOrder("Mot cle", " elasticsearch"));
        assertThat(doc.getMeta().getTitle(), is("Mon titre"));
    }

    @Test
    public void testExtractFromDocx() throws IOException {
        Doc doc = extractFromFile("docx");

        // Extracted content
        assertThat(doc.getContent(), containsString("A short explanation of"));

        // Content Type
        assertThat(doc.getFile().getContentType(), is("application/vnd.openxmlformats-officedocument.wordprocessingml.document"));

        // Meta data
        assertThat(doc.getMeta().getAuthor(), is("Admin"));
        assertThat(doc.getMeta().getDate(), is(nullValue()));
        assertThat(doc.getMeta().getKeywords(), emptyIterable());
        assertThat(doc.getMeta().getTitle(), is(nullValue()));
    }

    @Test
    public void testExtractFromMp3() throws IOException {
        Doc doc = extractFromFile("mp3");

        // Extracted content
        assertThat(doc.getContent(), containsString("Test Tika"));

        // Content Type
        assertThat(doc.getFile().getContentType(), is("audio/mpeg"));

        // Meta data
        assertThat(doc.getMeta().getAuthor(), is("David Pilato"));
        assertThat(doc.getMeta().getDate(), is(nullValue()));
        assertThat(doc.getMeta().getKeywords(), emptyIterable());
        assertThat(doc.getMeta().getTitle(), is("Test Tika"));
    }

    @Test
    public void testExtractFromWav() throws IOException {
        Doc doc = extractFromFile("wav");

        // Extracted content
        assertThat(doc.getContent(), is(""));

        // Content Type
        assertThat(doc.getFile().getContentType(), is("audio/x-wav"));

        // Meta data
        assertThat(doc.getMeta().getAuthor(), is(nullValue()));
        assertThat(doc.getMeta().getDate(), is(nullValue()));
        assertThat(doc.getMeta().getKeywords(), emptyIterable());
        assertThat(doc.getMeta().getTitle(), is(nullValue()));
    }

    @Test
    public void testExtractFromTxt() throws IOException {
        Doc doc = extractFromFile("txt");

        // Extracted content
        assertThat(doc.getContent(), containsString("This file contains some words."));

        // Content Type
        assertThat(doc.getFile().getContentType(), containsString("text/plain"));

        // Meta data
        assertThat(doc.getMeta().getAuthor(), is(nullValue()));
        assertThat(doc.getMeta().getDate(), is(nullValue()));
        assertThat(doc.getMeta().getKeywords(), emptyIterable());
        assertThat(doc.getMeta().getTitle(), is(nullValue()));
    }

    private byte[] getBinaryContent(String filename) throws IOException {
        String url = getUrl("documents", filename);
        Path file = Paths.get(url);
        byte[] data = Files.readAllBytes(file);

        return data;
    }

    private Doc extractFromFile(String extension) throws IOException {
        logger.info("Test extraction of [{}] file", extension);
        byte[] data = getBinaryContent("test." + extension);
        Doc doc = new Doc();
        TikaDocParser.generate(
                FsSettings.builder(getCurrentTestName()).build(),
                data,
                "test." + extension,
                doc);
        return doc;
    }
}
