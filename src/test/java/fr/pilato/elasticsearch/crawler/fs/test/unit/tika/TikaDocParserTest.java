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
import fr.pilato.elasticsearch.crawler.fs.meta.settings.Fs;
import fr.pilato.elasticsearch.crawler.fs.meta.settings.FsSettings;
import fr.pilato.elasticsearch.crawler.fs.test.AbstractFSCrawlerTestCase;
import fr.pilato.elasticsearch.crawler.fs.tika.TikaDocParser;
import org.junit.Test;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Map;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.emptyIterable;
import static org.hamcrest.Matchers.hasEntry;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;
import static org.hamcrest.Matchers.nullValue;
import static org.junit.Assert.fail;
import static org.junit.Assume.assumeNoException;

public class TikaDocParserTest extends AbstractFSCrawlerTestCase {

    /**
     * Test case for https://github.com/dadoonet/fscrawler/issues/163
     */
    @Test
    public void testXmlIssue163() throws IOException {
        Doc doc = extractFromFile("issue-163.xml");

        // Extracted content
        assertThat(doc.getContent(), is("   \n"));

        // Content Type
        assertThat(doc.getFile().getContentType(), containsString("application/xml"));

        // Meta data
        assertThat(doc.getMeta().getAuthor(), is(nullValue()));
        assertThat(doc.getMeta().getDate(), is(nullValue()));
        assertThat(doc.getMeta().getKeywords(), emptyIterable());
        assertThat(doc.getMeta().getTitle(), is(nullValue()));

        Map<String, String> raw = doc.getMeta().getRaw();
        assertThat(raw, hasEntry("X-Parsed-By", "org.apache.tika.parser.DefaultParser"));
        assertThat(raw, hasEntry(is("Content-Type"), containsString("application/xml")));
    }

    @Test
    public void testExtractFromDoc() throws IOException {
        Doc doc = extractFromFileExtension("doc");

        // Extracted content
        assertThat(doc.getContent(), containsString("This is a sample text available in page"));

        // Content Type
        assertThat(doc.getFile().getContentType(), is("application/msword"));

        // Meta data
        assertThat(doc.getMeta().getAuthor(), is("David Pilato"));
        assertThat(doc.getMeta().getDate(), is(nullValue()));
        assertThat(doc.getMeta().getKeywords(), containsInAnyOrder("keyword1"," keyword2"));
        assertThat(doc.getMeta().getTitle(), is("Test Tika title"));

        Map<String, String> raw = doc.getMeta().getRaw();
        assertThat(raw, hasEntry("date", "2016-07-07T08:37:00Z"));
        assertThat(raw, hasEntry("Company", "elastic"));
        assertThat(raw, hasEntry("subject", "Test Tika Object"));
        assertThat(raw, hasEntry("Word-Count", "19"));
        assertThat(raw, hasEntry("Manager", "My Mother"));
        assertThat(raw, hasEntry("Template", "Normal.dotm"));
        assertThat(raw, hasEntry("dc:title", "Test Tika title"));
        assertThat(raw, hasEntry("modified", "2016-07-07T08:37:00Z"));
        assertThat(raw, hasEntry("cp:subject", "Test Tika Object"));
        assertThat(raw, hasEntry("custom:N° du document", "1234"));
        assertThat(raw, hasEntry("meta:author", "David Pilato"));
        assertThat(raw, hasEntry("extended-properties:Application", "Microsoft Macintosh Word"));
        assertThat(raw, hasEntry("meta:creation-date", "2016-07-07T08:37:00Z"));
        assertThat(raw, hasEntry("Creation-Date", "2016-07-07T08:37:00Z"));
        assertThat(raw, hasEntry("Last-Author", "David Pilato"));
        assertThat(raw, hasEntry("w:comments", "Comments"));
        assertThat(raw, hasEntry("Character Count", "68"));
        assertThat(raw, hasEntry("Page-Count", "2"));
        assertThat(raw, hasEntry("extended-properties:Template", "Normal.dotm"));
        assertThat(raw, hasEntry("Author", "David Pilato"));
        assertThat(raw, hasEntry("meta:page-count", "2"));
        assertThat(raw, hasEntry("cp:revision", "2"));
        assertThat(raw, hasEntry("Keywords", "keyword1, keyword2"));
        assertThat(raw, hasEntry("meta:word-count", "19"));
        assertThat(raw, hasEntry("Category", "test"));
        assertThat(raw, hasEntry("dc:creator", "David Pilato"));
        assertThat(raw, hasEntry("extended-properties:Company", "elastic"));
        assertThat(raw, hasEntry("dcterms:created", "2016-07-07T08:37:00Z"));
        assertThat(raw, hasEntry("dcterms:modified", "2016-07-07T08:37:00Z"));
        assertThat(raw, hasEntry("Last-Modified", "2016-07-07T08:37:00Z"));
        assertThat(raw, hasEntry("title", "Test Tika title"));
        assertThat(raw, hasEntry("Last-Save-Date", "2016-07-07T08:37:00Z"));
        assertThat(raw, hasEntry("meta:character-count", "68"));
        assertThat(raw, hasEntry("custom:Terminé le", "2016-07-06T22:00:00Z"));
        assertThat(raw, hasEntry("meta:save-date", "2016-07-07T08:37:00Z"));
        assertThat(raw, hasEntry("Application-Name", "Microsoft Macintosh Word"));
        assertThat(raw, hasEntry("Edit-Time", "600000000"));
        assertThat(raw, hasEntry("extended-properties:Manager", "My Mother"));
        assertThat(raw, hasEntry("Content-Type", "application/msword"));
        assertThat(raw, hasEntry("X-Parsed-By", "org.apache.tika.parser.DefaultParser"));
        assertThat(raw, hasEntry("creator", "David Pilato"));
        assertThat(raw, hasEntry("dc:subject", "keyword1, keyword2"));
        assertThat(raw, hasEntry("meta:last-author", "David Pilato"));
        assertThat(raw, hasEntry("Comments", "Comments"));
        assertThat(raw, hasEntry("xmpTPg:NPages", "2"));
        assertThat(raw, hasEntry("Revision-Number", "2"));
        assertThat(raw, hasEntry("meta:keyword", "keyword1, keyword2"));
        assertThat(raw, hasEntry("comment", "Comments"));
        assertThat(raw, hasEntry("cp:category", "test"));
    }

    @Test
    public void testExtractFromDocx() throws IOException {
        Doc doc = extractFromFileExtension("docx");

        // Extracted content
        assertThat(doc.getContent(), containsString("This is a sample text available in page"));

        // Content Type
        assertThat(doc.getFile().getContentType(), is("application/vnd.openxmlformats-officedocument.wordprocessingml.document"));

        // Meta data
        assertThat(doc.getMeta().getAuthor(), is("David Pilato"));
        assertThat(doc.getMeta().getDate(), is(nullValue()));
        assertThat(doc.getMeta().getKeywords(), containsInAnyOrder("keyword1"," keyword2"));
        assertThat(doc.getMeta().getTitle(), is("Test Tika title"));

        Map<String, String> raw = doc.getMeta().getRaw();
        assertThat(raw, hasEntry("date", "2016-07-07T08:36:00Z"));
        assertThat(raw, hasEntry("Total-Time", "6"));
        assertThat(raw, hasEntry("extended-properties:AppVersion", "15.0000"));
        assertThat(raw, hasEntry("meta:paragraph-count", "2"));
        assertThat(raw, hasEntry("subject", "Test Tika Object"));
        assertThat(raw, hasEntry("Word-Count", "19"));
        assertThat(raw, hasEntry("meta:line-count", "3"));
        assertThat(raw, hasEntry("Manager", "My Mother"));
        assertThat(raw, hasEntry("Template", "Normal.dotm"));
        assertThat(raw, hasEntry("Paragraph-Count", "2"));
        assertThat(raw, hasEntry("meta:character-count-with-spaces", "82"));
        assertThat(raw, hasEntry("dc:title", "Test Tika title"));
        assertThat(raw, hasEntry("modified", "2016-07-07T08:36:00Z"));
        assertThat(raw, hasEntry("cp:subject", "Test Tika Object"));
        assertThat(raw, hasEntry("custom:N° du document", "1234"));
        assertThat(raw, hasEntry("meta:author", "David Pilato"));
        assertThat(raw, hasEntry("meta:creation-date", "2015-12-19T23:39:00Z"));
        assertThat(raw, hasEntry("extended-properties:Application", "Microsoft Macintosh Word"));
        assertThat(raw, hasEntry("Creation-Date", "2015-12-19T23:39:00Z"));
        assertThat(raw, hasEntry("Character-Count-With-Spaces", "82"));
        assertThat(raw, hasEntry("Last-Author", "David Pilato"));
        assertThat(raw, hasEntry("Character Count", "65"));
        assertThat(raw, hasEntry("Page-Count", "2"));
        assertThat(raw, hasEntry("Application-Version", "15.0000"));
        assertThat(raw, hasEntry("extended-properties:Template", "Normal.dotm"));
        assertThat(raw, hasEntry("Author", "David Pilato"));
        assertThat(raw, hasEntry("publisher", "elastic"));
        assertThat(raw, hasEntry("meta:page-count", "2"));
        assertThat(raw, hasEntry("cp:revision", "4"));
        assertThat(raw, hasEntry("dc:description", "Comments"));
        assertThat(raw, hasEntry("Keywords", "keyword1, keyword2"));
        assertThat(raw, hasEntry("Category", "test"));
        assertThat(raw, hasEntry("meta:word-count", "19"));
        assertThat(raw, hasEntry("dc:creator", "David Pilato"));
        assertThat(raw, hasEntry("extended-properties:Company", "elastic"));
        assertThat(raw, hasEntry("description", "Comments"));
        assertThat(raw, hasEntry("dcterms:created", "2015-12-19T23:39:00Z"));
        assertThat(raw, hasEntry("Last-Modified", "2016-07-07T08:36:00Z"));
        assertThat(raw, hasEntry("dcterms:modified", "2016-07-07T08:36:00Z"));
        assertThat(raw, hasEntry("title", "Test Tika title"));
        assertThat(raw, hasEntry("Last-Save-Date", "2016-07-07T08:36:00Z"));
        assertThat(raw, hasEntry("meta:character-count", "65"));
        assertThat(raw, hasEntry("custom:Terminé le", "2016-07-06T22:00:00Z"));
        assertThat(raw, hasEntry("Line-Count", "3"));
        assertThat(raw, hasEntry("meta:save-date", "2016-07-07T08:36:00Z"));
        assertThat(raw, hasEntry("Application-Name", "Microsoft Macintosh Word"));
        assertThat(raw, hasEntry("extended-properties:TotalTime", "6"));
        assertThat(raw, hasEntry("extended-properties:Manager", "My Mother"));
        assertThat(raw, hasEntry("Content-Type", "application/vnd.openxmlformats-officedocument.wordprocessingml.document"));
        assertThat(raw, hasEntry("X-Parsed-By", "org.apache.tika.parser.DefaultParser"));
        assertThat(raw, hasEntry("creator", "David Pilato"));
        assertThat(raw, hasEntry("dc:subject", "keyword1, keyword2"));
        assertThat(raw, hasEntry("meta:last-author", "David Pilato"));
        assertThat(raw, hasEntry("xmpTPg:NPages", "2"));
        assertThat(raw, hasEntry("Revision-Number", "4"));
        assertThat(raw, hasEntry("meta:keyword", "keyword1, keyword2"));
        assertThat(raw, hasEntry("cp:category", "test"));
        assertThat(raw, hasEntry("dc:publisher", "elastic"));
    }

    @Test
    public void testExtractFromHtml() throws IOException {
        Doc doc = extractFromFileExtension("html");

        // Extracted content
        assertThat(doc.getContent(), containsString("a sample text available in"));

        // Content Type
        assertThat(doc.getFile().getContentType(), containsString("text/html"));

        // Meta data
        assertThat(doc.getMeta().getAuthor(), is(nullValue()));
        assertThat(doc.getMeta().getDate(), is(nullValue()));
        assertThat(doc.getMeta().getKeywords(), emptyIterable());
        assertThat(doc.getMeta().getTitle(), is("Test Tika title"));

        Map<String, String> raw = doc.getMeta().getRaw();
        assertThat(raw, hasEntry("X-Parsed-By", "org.apache.tika.parser.DefaultParser"));
        assertThat(raw, hasEntry("Titre", "Test Tika title"));
        assertThat(raw, hasEntry("Originator", "Microsoft Word 15"));
        assertThat(raw, hasEntry("Mots clés", "keyword1, keyword2"));
        assertThat(raw, hasEntry("Content-Location", "Web%20page"));
        assertThat(raw, hasEntry("dc:title", "Test Tika title"));
        assertThat(raw, hasEntry("Content-Encoding", "UTF-8"));
        assertThat(raw, hasEntry("Content-Type-Hint", "text/html; charset=macintosh"));
        assertThat(raw, hasEntry("ProgId", "Word.Document"));
        assertThat(raw, hasEntry("title", "Test Tika title"));
        assertThat(raw, hasEntry(is("Content-Type"), containsString("text/html")));
        assertThat(raw, hasEntry("Generator", "Microsoft Word 15"));
    }

    /**
     * Test for #87: https://github.com/dadoonet/fscrawler/issues/87
     */
    @Test
    public void testExtractFromMp3() throws IOException {
        Doc doc = extractFromFileExtension("mp3");

        // Extracted content
        assertThat(doc.getContent(), containsString("Test Tika"));

        // Content Type
        assertThat(doc.getFile().getContentType(), is("audio/mpeg"));

        // Meta data
        assertThat(doc.getMeta().getAuthor(), is("David Pilato"));
        assertThat(doc.getMeta().getDate(), is(nullValue()));
        assertThat(doc.getMeta().getKeywords(), emptyIterable());
        assertThat(doc.getMeta().getTitle(), is("Test Tika"));

        Map<String, String> raw = doc.getMeta().getRaw();
        assertThat(raw, hasEntry("xmpDM:genre", "Vocal"));
        assertThat(raw, hasEntry("X-Parsed-By", "org.apache.tika.parser.DefaultParser"));
        assertThat(raw, hasEntry("creator", "David Pilato"));
        assertThat(raw, hasEntry("xmpDM:album", "FS Crawler"));
        assertThat(raw, hasEntry("xmpDM:trackNumber", "1"));
        assertThat(raw, hasEntry("xmpDM:releaseDate", "2016"));
        assertThat(raw, hasEntry("meta:author", "David Pilato"));
        assertThat(raw, hasEntry("xmpDM:artist", "David Pilato"));
        assertThat(raw, hasEntry("dc:creator", "David Pilato"));
        assertThat(raw, hasEntry("xmpDM:audioCompressor", "MP3"));
        assertThat(raw, hasEntry("title", "Test Tika"));
        assertThat(raw, hasEntry("xmpDM:audioChannelType", "Stereo"));
        assertThat(raw, hasEntry("version", "MPEG 3 Layer III Version 1"));
        assertThat(raw, hasEntry(is("xmpDM:logComment"), containsString("Hello but reverted")));
        assertThat(raw, hasEntry("xmpDM:audioSampleRate", "44100"));
        assertThat(raw, hasEntry("channels", "2"));
        assertThat(raw, hasEntry("dc:title", "Test Tika"));
        assertThat(raw, hasEntry("Author", "David Pilato"));
        assertThat(raw, hasEntry("xmpDM:duration", "1018.775146484375"));
        assertThat(raw, hasEntry("Content-Type", "audio/mpeg"));
        assertThat(raw, hasEntry("samplerate", "44100"));
    }

    @Test
    public void testExtractFromOdt() throws IOException {
        Doc doc = extractFromFileExtension("odt");

        // Extracted content
        // TODO Fix when issue https://issues.apache.org/jira/browse/TIKA-2030 will be resolved
        assertThat(doc.getContent(), containsString("This isa sample text available in page"));

        // Content Type
        assertThat(doc.getFile().getContentType(), is("application/vnd.oasis.opendocument.text"));

        // Meta data
        assertThat(doc.getMeta().getAuthor(), is("David Pilato"));
        assertThat(doc.getMeta().getDate(), is(nullValue()));
        assertThat(doc.getMeta().getKeywords(), containsInAnyOrder("keyword1", "  keyword2"));
        assertThat(doc.getMeta().getTitle(), is("Test Tika title"));

        Map<String, String> raw = doc.getMeta().getRaw();
        assertThat(raw, hasEntry("date", "2016-07-07T08:37:00Z"));
        assertThat(raw, hasEntry("dc:description", "Comments"));
        assertThat(raw, hasEntry("Keywords", "keyword1,  keyword2"));
        assertThat(raw, hasEntry("meta:paragraph-count", "1"));
        assertThat(raw, hasEntry("meta:word-count", "12"));
        assertThat(raw, hasEntry("subject", "Test Tika Object"));
        assertThat(raw, hasEntry("meta:initial-author", "David Pilato"));
        assertThat(raw, hasEntry("initial-creator", "David Pilato"));
        assertThat(raw, hasEntry("dc:creator", "David Pilato"));
        assertThat(raw, hasEntry("generator", "MicrosoftOffice/15.0 MicrosoftWord"));
        assertThat(raw, hasEntry("description", "Comments"));
        assertThat(raw, hasEntry("Word-Count", "12"));
        assertThat(raw, hasEntry("dcterms:created", "2016-07-07T08:37:00Z"));
        assertThat(raw, hasEntry("dcterms:modified", "2016-07-07T08:37:00Z"));
        assertThat(raw, hasEntry("Last-Modified", "2016-07-07T08:37:00Z"));
        assertThat(raw, hasEntry("nbPara", "1"));
        assertThat(raw, hasEntry("title", "Test Tika title"));
        assertThat(raw, hasEntry("Last-Save-Date", "2016-07-07T08:37:00Z"));
        assertThat(raw, hasEntry("meta:character-count", "86"));
        assertThat(raw, hasEntry("custom:Terminé le", "2016-07-06T22:00:00Z"));
        assertThat(raw, hasEntry("Paragraph-Count", "1"));
        assertThat(raw, hasEntry("meta:save-date", "2016-07-07T08:37:00Z"));
        assertThat(raw, hasEntry("dc:title", "Test Tika title"));
        assertThat(raw, hasEntry("modified", "2016-07-07T08:37:00Z"));
        assertThat(raw, hasEntry("Edit-Time", "PT0S"));
        assertThat(raw, hasEntry("cp:subject", "Test Tika Object"));
        assertThat(raw, hasEntry("nbCharacter", "86"));
        assertThat(raw, hasEntry("nbPage", "1"));
        assertThat(raw, hasEntry("nbWord", "12"));
        assertThat(raw, hasEntry("Content-Type", "application/vnd.oasis.opendocument.text"));
        assertThat(raw, hasEntry("X-Parsed-By", "org.apache.tika.parser.DefaultParser"));
        assertThat(raw, hasEntry("creator", "David Pilato"));
        assertThat(raw, hasEntry("dc:subject", "keyword1,  keyword2"));
        assertThat(raw, hasEntry("meta:author", "David Pilato"));
        assertThat(raw, hasEntry("meta:creation-date", "2016-07-07T08:37:00Z"));
        assertThat(raw, hasEntry("Creation-Date", "2016-07-07T08:37:00Z"));
        assertThat(raw, hasEntry("xmpTPg:NPages", "1"));
        assertThat(raw, hasEntry("Character Count", "86"));
        assertThat(raw, hasEntry("editing-cycles", "2"));
        assertThat(raw, hasEntry("Page-Count", "1"));
        assertThat(raw, hasEntry("Author", "David Pilato"));
        assertThat(raw, hasEntry("meta:page-count", "1"));
    }

    @Test
    public void testExtractFromPdf() throws IOException {
        Doc doc = extractFromFileExtension("pdf");

        // Extracted content
        assertThat(doc.getContent(), containsString("This is a sample text available in page"));

        // Content Type
        assertThat(doc.getFile().getContentType(), is("application/pdf"));

        // Meta data
        assertThat(doc.getMeta().getAuthor(), is("David Pilato"));
        assertThat(doc.getMeta().getDate(), is(nullValue()));
        assertThat(doc.getMeta().getKeywords(), containsInAnyOrder("keyword1", " keyword2"));
        assertThat(doc.getMeta().getTitle(), is("Test Tika title"));

        Map<String, String> raw = doc.getMeta().getRaw();
        assertThat(raw, hasEntry("date", "2016-07-07T08:37:42Z"));
        assertThat(raw, hasEntry("pdf:PDFVersion", "1.5"));
        assertThat(raw, hasEntry("xmp:CreatorTool", "Microsoft Word"));
        assertThat(raw, hasEntry("Keywords", "keyword1, keyword2"));
        assertThat(raw, hasEntry("access_permission:modify_annotations", "true"));
        assertThat(raw, hasEntry("access_permission:can_print_degraded", "true"));
        assertThat(raw, hasEntry("subject", "Test Tika Object"));
        assertThat(raw, hasEntry("dc:creator", "David Pilato"));
        assertThat(raw, hasEntry("dcterms:created", "2016-07-07T08:37:42Z"));
        assertThat(raw, hasEntry("Last-Modified", "2016-07-07T08:37:42Z"));
        assertThat(raw, hasEntry("dcterms:modified", "2016-07-07T08:37:42Z"));
        assertThat(raw, hasEntry("dc:format", "application/pdf; version=1.5"));
        assertThat(raw, hasEntry("title", "Test Tika title"));
        assertThat(raw, hasEntry("Last-Save-Date", "2016-07-07T08:37:42Z"));
        assertThat(raw, hasEntry("access_permission:fill_in_form", "true"));
        assertThat(raw, hasEntry("meta:save-date", "2016-07-07T08:37:42Z"));
        assertThat(raw, hasEntry("pdf:encrypted", "false"));
        assertThat(raw, hasEntry("dc:title", "Test Tika title"));
        assertThat(raw, hasEntry("modified", "2016-07-07T08:37:42Z"));
        assertThat(raw, hasEntry("cp:subject", "Test Tika Object"));
        assertThat(raw, hasEntry("Content-Type", "application/pdf"));
        assertThat(raw, hasEntry("X-Parsed-By", "org.apache.tika.parser.DefaultParser"));
        assertThat(raw, hasEntry("creator", "David Pilato"));
        assertThat(raw, hasEntry("meta:author", "David Pilato"));
        assertThat(raw, hasEntry("dc:subject", "keyword1, keyword2"));
        assertThat(raw, hasEntry("meta:creation-date", "2016-07-07T08:37:42Z"));
        assertThat(raw, hasEntry(is("created"), containsString("Jul 0")));
        assertThat(raw, hasEntry("access_permission:extract_for_accessibility", "true"));
        assertThat(raw, hasEntry("access_permission:assemble_document", "true"));
        assertThat(raw, hasEntry("xmpTPg:NPages", "2"));
        assertThat(raw, hasEntry("Creation-Date", "2016-07-07T08:37:42Z"));
        assertThat(raw, hasEntry("access_permission:extract_content", "true"));
        assertThat(raw, hasEntry("access_permission:can_print", "true"));
        assertThat(raw, hasEntry("meta:keyword", "keyword1, keyword2"));
        assertThat(raw, hasEntry("Author", "David Pilato"));
        assertThat(raw, hasEntry("access_permission:can_modify", "true"));
    }

    @Test
    public void testExtractFromRtf() throws IOException {
        Doc doc = extractFromFileExtension("rtf");

        // Extracted content
        assertThat(doc.getContent(), containsString("This is a sample text available in page"));

        // Content Type
        assertThat(doc.getFile().getContentType(), is("application/rtf"));

        // Meta data
        assertThat(doc.getMeta().getAuthor(), is("David Pilato"));
        assertThat(doc.getMeta().getDate(), is(nullValue()));
        assertThat(doc.getMeta().getKeywords(), containsInAnyOrder("keyword1", " keyword2"));
        assertThat(doc.getMeta().getTitle(), is("Test Tika title"));

        Map<String, String> raw = doc.getMeta().getRaw();
        assertThat(raw, hasEntry("X-Parsed-By", "org.apache.tika.parser.DefaultParser"));
        assertThat(raw, hasEntry("creator", "David Pilato"));
        assertThat(raw, hasEntry("Keywords", "keyword1, keyword2"));
        assertThat(raw, hasEntry("meta:word-count", "19"));
        assertThat(raw, hasEntry("meta:author", "David Pilato"));
        assertThat(raw, hasEntry("dc:subject", "keyword1, keyword2"));
        assertThat(raw, hasEntry(is("meta:creation-date"), containsString("2016-07-0")));
        assertThat(raw, hasEntry("subject", "Test Tika Object"));
        assertThat(raw, hasEntry("dc:creator", "David Pilato"));
        assertThat(raw, hasEntry("extended-properties:Company", "elastic"));
        assertThat(raw, hasEntry(is("Creation-Date"), containsString("2016-07-")));
        assertThat(raw, hasEntry(is("dcterms:created"), containsString("2016-07-")));
        assertThat(raw, hasEntry("title", "Test Tika title"));
        assertThat(raw, hasEntry("meta:character-count", "68"));
        assertThat(raw, hasEntry("dc:title", "Test Tika title"));
        assertThat(raw, hasEntry("Author", "David Pilato"));
        assertThat(raw, hasEntry("extended-properties:Manager", "My Mother"));
        assertThat(raw, hasEntry("cp:subject", "Test Tika Object"));
        assertThat(raw, hasEntry("meta:page-count", "2"));
        assertThat(raw, hasEntry("cp:category", "test"));
        assertThat(raw, hasEntry("Content-Type", "application/rtf"));
    }

    @Test
    public void testExtractFromTxt() throws IOException {
        Doc doc = extractFromFileExtension("txt");

        // Extracted content
        assertThat(doc.getContent(), containsString("This file contains some words."));

        // Content Type
        assertThat(doc.getFile().getContentType(), containsString("text/plain"));

        // Meta data
        assertThat(doc.getMeta().getAuthor(), is(nullValue()));
        assertThat(doc.getMeta().getDate(), is(nullValue()));
        assertThat(doc.getMeta().getKeywords(), emptyIterable());
        assertThat(doc.getMeta().getTitle(), is(nullValue()));

        Map<String, String> raw = doc.getMeta().getRaw();
        assertThat(raw, hasEntry("X-Parsed-By", "org.apache.tika.parser.DefaultParser"));
        assertThat(raw, hasEntry(is("Content-Encoding"), notNullValue()));
        assertThat(raw, hasEntry(is("Content-Type"), containsString("text/plain")));

        assertThat(doc.getAttachment(), nullValue());
        assertThat(doc.getFile().getChecksum(), nullValue());
    }

    @Test
    public void testExtractFromWav() throws IOException {
        Doc doc = extractFromFileExtension("wav");

        // Extracted content
        assertThat(doc.getContent(), is(""));

        // Content Type
        assertThat(doc.getFile().getContentType(), is("audio/x-wav"));

        // Meta data
        assertThat(doc.getMeta().getAuthor(), is(nullValue()));
        assertThat(doc.getMeta().getDate(), is(nullValue()));
        assertThat(doc.getMeta().getKeywords(), emptyIterable());
        assertThat(doc.getMeta().getTitle(), is(nullValue()));

        Map<String, String> raw = doc.getMeta().getRaw();
        assertThat(raw, hasEntry("X-Parsed-By", "org.apache.tika.parser.DefaultParser"));
        assertThat(raw, hasEntry("xmpDM:audioSampleRate", "44100"));
        assertThat(raw, hasEntry("channels", "2"));
        assertThat(raw, hasEntry("bits", "16"));
        assertThat(raw, hasEntry("encoding", "PCM_SIGNED"));
        assertThat(raw, hasEntry("xmpDM:audioSampleType", "16Int"));
        assertThat(raw, hasEntry("Content-Type", "audio/x-wav"));
        assertThat(raw, hasEntry("samplerate", "44100.0"));
    }

    @Test
    public void testExtractFromTxtAndStoreSource() throws IOException {
        Doc doc = extractFromFile("test.txt",
                FsSettings.builder(getCurrentTestName())
                        .setFs(Fs.builder().setStoreSource(true).build())
                        .build());

        // Extracted content
        assertThat(doc.getContent(), containsString("This file contains some words."));
        assertThat(doc.getAttachment(), notNullValue());
    }

    @Test
    public void testExtractFromTxtAndStoreSourceWithDigest() throws IOException {
        try {
            MessageDigest.getInstance("MD5");
        } catch (NoSuchAlgorithmException e) {
            assumeNoException(e);
        }

        Doc doc = extractFromFile("test.txt",
                FsSettings.builder(getCurrentTestName())
                        .setFs(Fs.builder().setStoreSource(true).setChecksum("MD5").build())
                        .build());

        // Extracted content
        assertThat(doc.getContent(), containsString("This file contains some words."));
        assertThat(doc.getAttachment(), notNullValue());
        assertThat(doc.getFile().getChecksum(), notNullValue());
    }

    @Test
    public void testExtractFromTxtWithDigest() throws IOException {
        try {
            MessageDigest.getInstance("MD5");
        } catch (NoSuchAlgorithmException e) {
            assumeNoException(e);
        }

        Doc doc = extractFromFile("test.txt",
                FsSettings.builder(getCurrentTestName())
                        .setFs(Fs.builder().setChecksum("MD5").build())
                        .build());

        // Extracted content
        assertThat(doc.getContent(), containsString("This file contains some words."));
        assertThat(doc.getAttachment(), nullValue());
        assertThat(doc.getFile().getChecksum(), notNullValue());
    }

    private InputStream getBinaryContent(String filename) throws IOException {
        return Files.newInputStream(Paths.get(getUrl("documents", filename)));
    }

    private Doc extractFromFileExtension(String extension) throws IOException {
        logger.info("Test extraction of [{}] file", extension);
        Doc doc = extractFromFile("test." + extension);
        assertThat(doc.getFile().getExtension(), is(extension));
        return doc;
    }

    private Doc extractFromFile(String filename) throws IOException {
        return extractFromFile(filename, FsSettings.builder(getCurrentTestName()).build());
    }

    private Doc extractFromFile(String filename, FsSettings fsSettings) throws IOException {
        InputStream data = getBinaryContent(filename);
        Doc doc = new Doc();
        MessageDigest messageDigest = null;
        if (fsSettings.getFs() != null && fsSettings.getFs().getChecksum() != null) {
            try {
                messageDigest = MessageDigest.getInstance(fsSettings.getFs().getChecksum());
            } catch (NoSuchAlgorithmException e) {
                fail("Algorithm [" + fsSettings.getFs().getChecksum() + "] not found: " + e.getMessage());
            }
        }

        TikaDocParser.generate(
                fsSettings,
                data,
                filename,
                doc,
                messageDigest,
                0);

        logger.debug("Generated Content: [{}]", doc.getContent());
        logger.debug("Generated Raw Metadata: [{}]", doc.getMeta().getRaw());

        return doc;
    }
}
