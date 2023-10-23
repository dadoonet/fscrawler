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

import fr.pilato.elasticsearch.crawler.fs.beans.Doc;
import fr.pilato.elasticsearch.crawler.fs.settings.Fs;
import fr.pilato.elasticsearch.crawler.fs.settings.FsSettings;
import fr.pilato.elasticsearch.crawler.fs.settings.Ocr;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.apache.tika.exception.TikaConfigException;
import org.apache.tika.parser.ocr.TesseractOCRParser;
import org.junit.BeforeClass;
import org.junit.Test;

import java.io.IOException;
import java.io.InputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.Map;

import static com.carrotsearch.randomizedtesting.RandomizedTest.randomBoolean;
import static fr.pilato.elasticsearch.crawler.fs.framework.FsCrawlerUtil.localDateTimeToDate;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.*;
import static org.junit.Assert.fail;
import static org.junit.Assume.assumeNoException;
import static org.junit.Assume.assumeTrue;

public class TikaDocParserTest extends DocParserTestCase {

    private static boolean isOcrAvailable;

    @BeforeClass
    public static void setOcrAvailable() {
        try {
            isOcrAvailable = new TesseractOCRParser().hasTesseract();
        } catch (TikaConfigException e) {
            staticLogger.warn("Can not configure Tesseract for tests, so we are supposing it won't be available");
            isOcrAvailable = false;
        }
    }

    /**
     * Test case for <a href="https://github.com/dadoonet/fscrawler/issues/162">https://github.com/dadoonet/fscrawler/issues/162</a>
     */
    @Test
    public void testLangDetect162() throws IOException {
        FsSettings fsSettings = FsSettings.builder(getCurrentTestName())
                .setFs(Fs.builder().setLangDetect(true).build())
                .build();
        Doc doc = extractFromFile("test.txt", fsSettings);
        assertThat(doc.getMeta().getLanguage(), is("en"));
        doc = extractFromFile("test-fr.txt", fsSettings);
        assertThat(doc.getMeta().getLanguage(), is("fr"));
        doc = extractFromFile("test-de.txt", fsSettings);
        assertThat(doc.getMeta().getLanguage(), is("de"));
        doc = extractFromFile("test-enfrde.txt", fsSettings);
        assertThat(doc.getMeta().getLanguage(), is("fr"));
    }

    /**
     * Test case for <a href="https://github.com/dadoonet/fscrawler/issues/221">https://github.com/dadoonet/fscrawler/issues/221</a>
     */
    @Test
    public void testPdfIssue221() throws IOException {
        // We test document 1
        Doc doc = extractFromFile("issue-221-doc1.pdf");

        // Extracted content
        assertThat(doc.getContent(), containsString("coucou"));

        // Content Type
        assertThat(doc.getFile().getContentType(), containsString("application/pdf"));

        // Meta data
        assertThat(doc.getMeta().getAuthor(), is(notNullValue()));
        assertThat(doc.getMeta().getDate(), is(localDateTimeToDate(LocalDateTime.of(2016, 9, 20, 9, 38, 56))));
        assertThat(doc.getMeta().getKeywords(), not(emptyIterable()));
        assertThat(doc.getMeta().getTitle(), containsString("Recherche"));

        // We test document 2
        doc = extractFromFile("issue-221-doc2.pdf");

        // Extracted content
        assertThat(doc.getContent(), containsString("FORMATIONS"));

        // Content Type
        assertThat(doc.getFile().getContentType(), containsString("application/pdf"));

        // Meta data
        assertThat(doc.getMeta().getAuthor(), is(nullValue()));
        assertThat(doc.getMeta().getDate(), is(localDateTimeToDate(LocalDateTime.of(2016, 9, 19, 14, 29, 37))));
        assertThat(doc.getMeta().getKeywords(), nullValue());
        assertThat(doc.getMeta().getTitle(), is(nullValue()));
    }

    /**
     * Test case for <a href="https://github.com/dadoonet/fscrawler/issues/163">https://github.com/dadoonet/fscrawler/issues/163</a>
     */
    @Test
    public void testXmlIssue163() throws IOException {
        FsSettings fsSettings = FsSettings.builder(getCurrentTestName())
                .setFs(Fs.builder().setRawMetadata(true).build())
                .build();
        Doc doc = extractFromFile("issue-163.xml", fsSettings);

        // Extracted content
        assertThat(doc.getContent(), is("   \n"));

        // Content Type
        assertThat(doc.getFile().getContentType(), containsString("application/xml"));

        // Meta data
        assertThat(doc.getMeta().getAuthor(), is(nullValue()));
        assertThat(doc.getMeta().getDate(), is(nullValue()));
        assertThat(doc.getMeta().getKeywords(), nullValue());
        assertThat(doc.getMeta().getTitle(), is(nullValue()));

        Map<String, String> raw = doc.getMeta().getRaw();
        assertThat(raw.entrySet(), iterableWithSize(4));
        assertThat(raw, hasEntry("X-TIKA:Parsed-By", "org.apache.tika.parser.DefaultParser"));
        assertThat(raw, hasEntry("X-TIKA:Parsed-By-Full-Set", "org.apache.tika.parser.DefaultParser"));
        assertThat(raw, hasEntry("resourceName", "issue-163.xml"));
        assertThat(raw, hasEntry("Content-Type", "application/xml"));
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
        assertThat(doc.getMeta().getDate(), is(localDateTimeToDate(LocalDateTime.of(2016, 7, 7, 8, 37, 0))));
        assertThat(doc.getMeta().getKeywords(), containsInAnyOrder("keyword1"," keyword2"));
        assertThat(doc.getMeta().getTitle(), is("Test Tika title"));

        Map<String, String> raw = doc.getMeta().getRaw();
        assertThat(raw.entrySet(), iterableWithSize(25));
        assertThat(raw, hasEntry("cp:revision", "2"));
        assertThat(raw, hasEntry("meta:word-count", "19"));
        assertThat(raw, hasEntry("X-TIKA:Parsed-By-Full-Set", "org.apache.tika.parser.DefaultParser"));
        assertThat(raw, hasEntry("dc:creator", "David Pilato"));
        assertThat(raw, hasEntry("extended-properties:Company", "elastic"));
        assertThat(raw, hasEntry("dcterms:created", "2016-07-07T08:37:00Z"));
        assertThat(raw, hasEntry("dcterms:modified", "2016-07-07T08:37:00Z"));
        assertThat(raw, hasEntry("meta:character-count", "68"));
        assertThat(raw, hasEntry("custom:Terminé le", "2016-07-06T22:00:00Z"));
        assertThat(raw, hasEntry("dc:title", "Test Tika title"));
        assertThat(raw, hasEntry("extended-properties:TotalTime", "600000000"));
        assertThat(raw, hasEntry("extended-properties:Manager", "My Mother"));
        assertThat(raw, hasEntry("custom:N° du document", "1234"));
        assertThat(raw, hasEntry("Content-Type", "application/msword"));
        assertThat(raw, hasEntry("w:Comments", "Comments"));
        assertThat(raw, hasEntry("dc:subject", "keyword1, keyword2"));
        assertThat(raw, hasEntry("extended-properties:Application", "Microsoft Macintosh Word"));
        assertThat(raw, hasEntry("meta:last-author", "David Pilato"));
        assertThat(raw, hasEntry("xmpTPg:NPages", "2"));
        assertThat(raw, hasEntry("resourceName", "test.doc"));
        assertThat(raw, hasEntry("extended-properties:Template", "Normal.dotm"));
        assertThat(raw, hasEntry("X-TIKA:Parsed-By", "org.apache.tika.parser.DefaultParser"));
        assertThat(raw, hasEntry("meta:keyword", "keyword1, keyword2"));
        assertThat(raw, hasEntry("meta:page-count", "2"));
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
        assertThat(doc.getMeta().getDate(), is(localDateTimeToDate(LocalDateTime.of(2016, 7, 7, 8, 36, 0))));
        assertThat(doc.getMeta().getKeywords(), containsInAnyOrder("keyword1"," keyword2"));
        assertThat(doc.getMeta().getTitle(), is("Test Tika title"));

        Map<String, String> raw = doc.getMeta().getRaw();
        assertThat(raw.entrySet(), iterableWithSize(31));
        assertThat(raw, hasEntry("cp:revision", "4"));
        assertThat(raw, hasEntry("dc:description", "Comments"));
        assertThat(raw, hasEntry("extended-properties:AppVersion", "15.0000"));
        assertThat(raw, hasEntry("meta:paragraph-count", "2"));
        assertThat(raw, hasEntry("meta:word-count", "19"));
        assertThat(raw, hasEntry("X-TIKA:Parsed-By-Full-Set", "org.apache.tika.parser.DefaultParser"));
        assertThat(raw, hasEntry("dc:creator", "David Pilato"));
        assertThat(raw, hasEntry("extended-properties:Company", "elastic"));
        assertThat(raw, hasEntry("dcterms:created", "2015-12-19T23:39:00Z"));
        assertThat(raw, hasEntry("meta:line-count", "3"));
        assertThat(raw, hasEntry("dcterms:modified", "2016-07-07T08:36:00Z"));
        assertThat(raw, hasEntry("meta:character-count", "65"));
        assertThat(raw, hasEntry("custom:Terminé le", "2016-07-06T22:00:00Z"));
        assertThat(raw, hasEntry("meta:character-count-with-spaces", "82"));
        assertThat(raw, hasEntry("dc:title", "Test Tika title"));
        assertThat(raw, hasEntry("extended-properties:TotalTime", "6"));
        assertThat(raw, hasEntry("extended-properties:Manager", "My Mother"));
        assertThat(raw, hasEntry("custom:N° du document", "1234"));
        assertThat(raw, hasEntry("Content-Type", "application/vnd.openxmlformats-officedocument.wordprocessingml.document"));
        assertThat(raw, hasEntry("dc:subject", "Test Tika Object"));
        assertThat(raw, hasEntry("extended-properties:Application", "Microsoft Macintosh Word"));
        assertThat(raw, hasEntry("meta:last-author", "David Pilato"));
        assertThat(raw, hasEntry("xmpTPg:NPages", "2"));
        assertThat(raw, hasEntry("resourceName", "test.docx"));
        assertThat(raw, hasEntry("extended-properties:Template", "Normal.dotm"));
        assertThat(raw, hasEntry("X-TIKA:Parsed-By", "org.apache.tika.parser.DefaultParser"));
        assertThat(raw, hasEntry("extended-properties:DocSecurityString", "None"));
        assertThat(raw, hasEntry("meta:keyword", "keyword1, keyword2"));
        assertThat(raw, hasEntry("cp:category", "test"));
        assertThat(raw, hasEntry("meta:page-count", "2"));
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
        assertThat(doc.getMeta().getKeywords(), nullValue());
        assertThat(doc.getMeta().getTitle(), is("Test Tika title"));

        Map<String, String> raw = doc.getMeta().getRaw();
        assertThat(raw.entrySet(), iterableWithSize(13));
        assertThat(raw, hasEntry("Titre", "Test Tika title"));
        assertThat(raw, hasEntry("Content-Location", "Web%20page"));
        assertThat(raw, hasEntry("X-TIKA:Parsed-By-Full-Set", "org.apache.tika.parser.DefaultParser"));
        assertThat(raw, hasEntry("resourceName", "test.html"));
        assertThat(raw, hasEntry("Mots clés", "keyword1, keyword2"));
        assertThat(raw, hasEntry("ProgId", "Word.Document"));
        assertThat(raw, hasEntry("Originator", "Microsoft Word 15"));
        assertThat(raw, hasEntry("X-TIKA:Parsed-By", "org.apache.tika.parser.DefaultParser"));
        assertThat(raw, hasEntry("dc:title", "Test Tika title"));
        assertThat(raw, hasEntry("Content-Encoding", "UTF-8"));
        assertThat(raw, hasEntry("Content-Type-Hint", "text/html; charset=macintosh"));
        assertThat(raw, hasEntry("Content-Type", "text/html; charset=UTF-8"));
        assertThat(raw, hasEntry("Generator", "Microsoft Word 15"));
    }

    /**
     * Test for #87: <a href="https://github.com/dadoonet/fscrawler/issues/87">https://github.com/dadoonet/fscrawler/issues/87</a>
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
        assertThat(doc.getMeta().getKeywords(), nullValue());
        assertThat(doc.getMeta().getTitle(), is("Test Tika"));

        Map<String, String> raw = doc.getMeta().getRaw();
        assertThat(raw.entrySet(), iterableWithSize(19));
        assertThat(raw, hasEntry("xmpDM:genre", "Vocal"));
        assertThat(raw, hasEntry("xmpDM:album", "FS Crawler"));
        assertThat(raw, hasEntry("xmpDM:trackNumber", "1"));
        assertThat(raw, hasEntry("xmpDM:releaseDate", "2016"));
        assertThat(raw, hasEntry("xmpDM:artist", "David Pilato"));
        assertThat(raw, hasEntry("X-TIKA:Parsed-By-Full-Set", "org.apache.tika.parser.DefaultParser"));
        assertThat(raw, hasEntry("dc:creator", "David Pilato"));
        assertThat(raw, hasEntry("xmpDM:audioCompressor", "MP3"));
        assertThat(raw, hasEntry("resourceName", "test.mp3"));
        assertThat(raw, hasEntry("xmpDM:audioChannelType", "Stereo"));
        assertThat(raw, hasEntry("version", "MPEG 3 Layer III Version 1"));
        assertThat(raw, hasEntry(is("xmpDM:logComment"), containsString("Hello but reverted")));
        assertThat(raw, hasEntry("xmpDM:audioSampleRate", "44100"));
        assertThat(raw, hasEntry("channels", "2"));
        assertThat(raw, hasEntry("X-TIKA:Parsed-By", "org.apache.tika.parser.DefaultParser"));
        assertThat(raw, hasEntry("dc:title", "Test Tika"));
        assertThat(raw, hasEntry("xmpDM:duration", "1.0187751054763794"));
        assertThat(raw, hasEntry("Content-Type", "audio/mpeg"));
        assertThat(raw, hasEntry("samplerate", "44100"));
    }

    @Test
    public void testExtractFromOdt() throws IOException {
        Doc doc = extractFromFileExtension("odt");

        // Extracted content
        assertThat(doc.getContent(), containsString("This is a sample text available in page"));

        // Content Type
        assertThat(doc.getFile().getContentType(), is("application/vnd.oasis.opendocument.text"));

        // Meta data
        assertThat(doc.getMeta().getAuthor(), is("David Pilato"));
        assertThat(doc.getMeta().getDate(), is(localDateTimeToDate(LocalDateTime.of(2016, 7, 7, 8, 37, 0))));
        assertThat(doc.getMeta().getKeywords(), containsInAnyOrder("keyword1", "  keyword2"));
        assertThat(doc.getMeta().getTitle(), is("Test Tika title"));

        Map<String, String> raw = doc.getMeta().getRaw();
        assertThat(raw.entrySet(), iterableWithSize(22));
        assertThat(raw, hasEntry("dc:description", "Comments"));
        assertThat(raw, hasEntry("meta:paragraph-count", "1"));
        assertThat(raw, hasEntry("meta:word-count", "12"));
        assertThat(raw, hasEntry("dc:subject", "keyword1,  keyword2"));
        assertThat(raw, hasEntry("X-TIKA:Parsed-By-Full-Set", "org.apache.tika.parser.DefaultParser"));
        assertThat(raw, hasEntry("dc:creator", "David Pilato"));
        assertThat(raw, hasEntry("generator", "MicrosoftOffice/15.0 MicrosoftWord"));
        assertThat(raw, hasEntry("xmpTPg:NPages", "1"));
        assertThat(raw, hasEntry("resourceName", "test.odt"));
        assertThat(raw, hasEntry("dcterms:created", "2016-07-07T08:37:00Z"));
        assertThat(raw, hasEntry("dcterms:modified", "2016-07-07T08:37:00Z"));
        assertThat(raw, hasEntry("editing-cycles", "2"));
        assertThat(raw, hasEntry("meta:character-count", "86"));
        assertThat(raw, hasEntry("custom:Terminé le", "2016-07-06T22:00:00Z"));
        assertThat(raw, hasEntry("X-TIKA:Parsed-By", "org.apache.tika.parser.DefaultParser"));
        assertThat(raw, hasEntry("dc:title", "Test Tika title"));
        assertThat(raw, hasEntry("odf:version", "1.2"));
        assertThat(raw, hasEntry("meta:keyword", "keyword1,  keyword2"));
        assertThat(raw, hasEntry("extended-properties:TotalTime", "PT0S"));
        assertThat(raw, hasEntry("cp:subject", "Test Tika Object"));
        assertThat(raw, hasEntry("meta:page-count", "1"));
        assertThat(raw, hasEntry("Content-Type", "application/vnd.oasis.opendocument.text"));
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
        assertThat(doc.getMeta().getDate(), is(localDateTimeToDate(LocalDateTime.of(2016, 7, 7, 8, 37, 42))));
        assertThat(doc.getMeta().getKeywords(), containsInAnyOrder("keyword1", " keyword2"));
        assertThat(doc.getMeta().getTitle(), is("Test Tika title"));

        Map<String, String> raw = doc.getMeta().getRaw();
        assertThat(raw.entrySet(), iterableWithSize(41));
        assertThat(raw, hasEntry("pdf:unmappedUnicodeCharsPerPage", "0"));
        assertThat(raw, hasEntry("pdf:PDFVersion", "1.5"));
        assertThat(raw, hasEntry("pdf:docinfo:title", "Test Tika title"));
        assertThat(raw, hasEntry("xmp:CreatorTool", "Microsoft Word"));
        assertThat(raw, hasEntry("pdf:hasXFA", "false"));
        assertThat(raw, hasEntry("access_permission:modify_annotations", "true"));
        assertThat(raw, hasEntry("access_permission:can_print_degraded", "true"));
        assertThat(raw, hasEntry("X-TIKA:Parsed-By-Full-Set", "org.apache.tika.parser.pdf.PDFParser"));
        assertThat(raw, hasEntry("dc:creator", "David Pilato"));
        assertThat(raw, hasEntry("pdf:num3DAnnotations", "0"));
        assertThat(raw, hasEntry("dcterms:created", "2016-07-07T08:37:42Z"));
        assertThat(raw, hasEntry("dcterms:modified", "2016-07-07T08:37:42Z"));
        assertThat(raw, hasEntry("dc:format", "application/pdf; version=1.5"));
        assertThat(raw, hasEntry("pdf:docinfo:creator_tool", "Microsoft Word"));
        assertThat(raw, hasEntry("pdf:overallPercentageUnmappedUnicodeChars", "0.0"));
        assertThat(raw, hasEntry("access_permission:fill_in_form", "true"));
        assertThat(raw, hasEntry("pdf:docinfo:keywords", "keyword1, keyword2"));
        assertThat(raw, hasEntry("pdf:docinfo:modified", "2016-07-07T08:37:42Z"));
        assertThat(raw, hasEntry("pdf:hasCollection", "false"));
        assertThat(raw, hasEntry("pdf:encrypted", "false"));
        assertThat(raw, hasEntry("dc:title", "Test Tika title"));
        assertThat(raw, hasEntry("pdf:containsNonEmbeddedFont", "false"));
        assertThat(raw, hasEntry("pdf:docinfo:subject", "Test Tika Object"));
        assertThat(raw, hasEntry("pdf:hasMarkedContent", "true"));
        assertThat(raw, hasEntry("Content-Type", "application/pdf"));
        assertThat(raw, hasEntry("pdf:docinfo:creator", "David Pilato"));
        assertThat(raw, hasEntry("dc:language", "en-US"));
        assertThat(raw, hasEntry("dc:subject", "keyword1, keyword2"));
        assertThat(raw, hasEntry("pdf:totalUnmappedUnicodeChars", "0"));
        assertThat(raw, hasEntry("access_permission:extract_for_accessibility", "true"));
        assertThat(raw, hasEntry("access_permission:assemble_document", "true"));
        assertThat(raw, hasEntry("xmpTPg:NPages", "2"));
        assertThat(raw, hasEntry("resourceName", "test.pdf"));
        assertThat(raw, hasEntry("pdf:hasXMP", "false"));
        assertThat(raw, hasEntry("pdf:charsPerPage", "42"));
        assertThat(raw, hasEntry("access_permission:extract_content", "true"));
        assertThat(raw, hasEntry("access_permission:can_print", "true"));
        assertThat(raw, hasEntry("X-TIKA:Parsed-By", "org.apache.tika.parser.pdf.PDFParser"));
        assertThat(raw, hasEntry("access_permission:can_modify", "true"));
        assertThat(raw, hasEntry("pdf:docinfo:created", "2016-07-07T08:37:42Z"));
        assertThat(raw, hasEntry("pdf:containsDamagedFont", "false"));
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
        assertThat(raw.entrySet(), iterableWithSize(15));
        assertThat(raw, hasEntry("meta:word-count", "19"));
        assertThat(raw, hasEntry("dc:subject", "Test Tika Object"));
        assertThat(raw, hasEntry("X-TIKA:Parsed-By-Full-Set", "org.apache.tika.parser.DefaultParser"));
        assertThat(raw, hasEntry("dc:creator", "David Pilato"));
        assertThat(raw, hasEntry("extended-properties:Company", "elastic"));
        assertThat(raw, hasEntry("resourceName", "test.rtf"));
        assertThat(raw, hasEntry(is("dcterms:created"), startsWith("2016-07-0")));
        assertThat(raw, hasEntry("meta:character-count", "68"));
        assertThat(raw, hasEntry("X-TIKA:Parsed-By", "org.apache.tika.parser.DefaultParser"));
        assertThat(raw, hasEntry("dc:title", "Test Tika title"));
        assertThat(raw, hasEntry("meta:keyword", "keyword1, keyword2"));
        assertThat(raw, hasEntry("extended-properties:Manager", "My Mother"));
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
        assertThat(doc.getMeta().getKeywords(), nullValue());
        assertThat(doc.getMeta().getTitle(), is(nullValue()));

        Map<String, String> raw = doc.getMeta().getRaw();
        assertThat(raw.entrySet(), iterableWithSize(5));
        assertThat(raw, hasEntry("X-TIKA:Parsed-By", "org.apache.tika.parser.DefaultParser"));
        assertThat(raw, hasEntry("X-TIKA:Parsed-By-Full-Set", "org.apache.tika.parser.DefaultParser"));
        assertThat(raw, hasEntry("Content-Encoding", "ISO-8859-1"));
        assertThat(raw, hasEntry("resourceName", "test.txt"));
        assertThat(raw, hasEntry("Content-Type", "text/plain; charset=ISO-8859-1"));

        assertThat(doc.getAttachment(), nullValue());
        assertThat(doc.getFile().getChecksum(), nullValue());
    }

    @Test
    public void testExtractFromWav() throws IOException {
        Doc doc = extractFromFileExtension("wav");

        // Extracted content
        assertThat(doc.getContent(), is(""));

        // Content Type
        assertThat(doc.getFile().getContentType(), is("audio/vnd.wave"));

        // Meta data
        assertThat(doc.getMeta().getAuthor(), is(nullValue()));
        assertThat(doc.getMeta().getDate(), is(nullValue()));
        assertThat(doc.getMeta().getKeywords(), nullValue());
        assertThat(doc.getMeta().getTitle(), is(nullValue()));

        Map<String, String> raw = doc.getMeta().getRaw();
        assertThat(raw.entrySet(), iterableWithSize(10));
        assertThat(raw, hasEntry("xmpDM:audioSampleRate", "44100"));
        assertThat(raw, hasEntry("channels", "2"));
        assertThat(raw, hasEntry("X-TIKA:Parsed-By", "org.apache.tika.parser.DefaultParser"));
        assertThat(raw, hasEntry("X-TIKA:Parsed-By-Full-Set", "org.apache.tika.parser.DefaultParser"));
        assertThat(raw, hasEntry("bits", "16"));
        assertThat(raw, hasEntry("resourceName", "test.wav"));
        assertThat(raw, hasEntry("encoding", "PCM_SIGNED"));
        assertThat(raw, hasEntry("xmpDM:audioSampleType", "16Int"));
        assertThat(raw, hasEntry("Content-Type", "audio/vnd.wave"));
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
    public void testExtractFromTxtStoreSourceAndNoIndexContent() throws IOException {
        Doc doc = extractFromFile("test.txt",
                FsSettings.builder(getCurrentTestName())
                        .setFs(Fs.builder().setStoreSource(true).setIndexContent(false).build())
                        .build());

        // Extracted content
        assertThat(doc.getContent(), nullValue());
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

    @Test
    public void testOcr() throws IOException {
        assumeTrue("Tesseract is not installed so we are skipping this test", isOcrAvailable);

        // Test with OCR On (default)
        Doc doc = extractFromFile("test-ocr.png");
        assertThat(doc.getContent(), containsString("This file contains some words."));
        doc = extractFromFile("test-ocr.pdf");
        assertThat(doc.getContent(), containsString("This file contains some words."));
        assertThat(doc.getContent(), containsString("This file also contains text."));
        doc = extractFromFile("test-ocr.docx");
        assertThat(doc.getContent(), containsString("This file contains some words."));
        assertThat(doc.getContent(), containsString("This file also contains text."));

        // Test with OCR On and PDF Strategy set to no_ocr (meaning that PDF are not OCRed)
        FsSettings fsSettings = FsSettings.builder(getCurrentTestName())
                .setFs(Fs.builder().setOcr(Ocr.builder()
                        .setPdfStrategy("no_ocr")
                        .build()).build())
                .build();
        doc = extractFromFile("test-ocr.png", fsSettings);
        assertThat(doc.getContent(), containsString("This file contains some words."));
        doc = extractFromFile("test-ocr.pdf", fsSettings);
        assertThat(doc.getContent(), containsString("This file also contains text."));
        assertThat(doc.getContent(), not(containsString("This file contains some words.")));
        doc = extractFromFile("test-ocr.docx", fsSettings);
        assertThat(doc.getContent(), containsString("This file also contains text."));
        assertThat(doc.getContent(), containsString("This file contains some words."));

        // Test with OCR On and PDF Strategy set to ocr_only (meaning that PDF only OCRed and no text is extracted)
        fsSettings = FsSettings.builder(getCurrentTestName())
                .setFs(Fs.builder().setOcr(Ocr.builder()
                        .setPdfStrategy("ocr_only")
                        .build()).build())
                .build();
        doc = extractFromFile("test-ocr.png", fsSettings);
        assertThat(doc.getContent(), containsString("This file contains some words."));
        doc = extractFromFile("test-ocr.pdf", fsSettings);
        assertThat(doc.getContent(), containsString("This file contains some words."));
        // TODO: for a strange reason ocr_only also extracts text.
        // assertThat(doc.getContent(), not(containsString("This file also contains text.")));
        doc = extractFromFile("test-ocr.docx", fsSettings);
        assertThat(doc.getContent(), containsString("This file contains some words."));
        assertThat(doc.getContent(), containsString("This file also contains text."));

        // Test with OCR On and PDF Strategy set to auto (meaning that PDF will be only OCRed if less than 10 characters are found)
        fsSettings = FsSettings.builder(getCurrentTestName())
                .setFs(Fs.builder().setOcr(Ocr.builder()
                        .setPdfStrategy("auto")
                        .build()).build())
                .build();
        doc = extractFromFile("test-ocr.pdf", fsSettings);
        assertThat(doc.getContent(), not(containsString("This file contains some words.")));
        assertThat(doc.getContent(), containsString("This file also contains text."));
        doc = extractFromFile("test-ocr-notext.pdf", fsSettings);
        assertThat(doc.getContent(), containsString("This file contains some words."));

        // Test with OCR Off
        fsSettings = FsSettings.builder(getCurrentTestName())
                .setFs(Fs.builder().setOcr(Ocr.builder()
                        .setEnabled(false)
                        .build()).build())
                .build();
        doc = extractFromFile("test-ocr.png", fsSettings);
        assertThat(doc.getContent(), isEmptyString());
        doc = extractFromFile("test-ocr.pdf", fsSettings);
        assertThat(doc.getContent(), not(containsString("This file contains some words.")));
        doc = extractFromFile("test-ocr.docx", fsSettings);
        assertThat(doc.getContent(), not(containsString("This file contains some words.")));

        // Test with OCR On (default) but a wrong path to tesseract
        fsSettings = FsSettings.builder(getCurrentTestName())
                .setFs(Fs.builder().setOcr(Ocr.builder()
                        .setPath("/path/to/doesnotexist")
                        .setDataPath("/path/to/doesnotexist")
                        .build()).build())
                .build();
        doc = extractFromFile("test-ocr.png", fsSettings);
        assertThat(doc.getContent(), isEmptyString());
        doc = extractFromFile("test-ocr.pdf", fsSettings);
        assertThat(doc.getContent(), not(containsString("This file contains some words.")));

        // Test with OCR On with hocr output type
        fsSettings = FsSettings.builder(getCurrentTestName())
                .setFs(Fs.builder().setOcr(Ocr.builder().setOutputType("hocr").build()).build())
                .build();
        doc = extractFromFile("test-ocr.png", fsSettings);
        assertThat(doc.getContent(), stringContainsInOrder(Arrays.asList("This", "file", "contains", "some", "words.")));
        doc = extractFromFile("test-ocr.pdf", fsSettings);
        assertThat(doc.getContent(), stringContainsInOrder(Arrays.asList("This", "file", "contains", "some", "words.")));

        // Test with heb language
        fsSettings = FsSettings.builder(getCurrentTestName())
                .setFs(Fs.builder().setOcr(Ocr.builder().setLanguage("heb").build()).build())
                .build();
        doc = extractFromFile("test-ocr-heb.pdf", fsSettings);
        try {
            // This test requires to have the hebrew language pack, so we don't fail the test but just log
            assertThat(doc.getContent(), containsString("המבודדים מתקבלים"));
        } catch (AssertionError e) {
            logger.info("We were not able to get the Hebrew content with OCR. May be the language pack was not installed?");
        }
    }

    @Test
    public void testCustomTikaConfig() throws IOException {
        InputStream tikaConfigIS = getClass().getResourceAsStream("/config/tikaConfig.xml");
        Path testTikaConfig = rootTmpDir.resolve("tika-config");
        if (Files.notExists(testTikaConfig)) {
            Files.createDirectory(testTikaConfig);
        }
        Files.copy(tikaConfigIS, testTikaConfig.resolve("tikaConfig.xml"));

        FsSettings fsSettings = FsSettings.builder(getCurrentTestName())
            .setFs(Fs.builder().setTikaConfigPath(testTikaConfig.resolve("tikaConfig.xml").toString()).build())
            .build();

        // Test that default parser for HTML is HTML parser
        Doc doc = extractFromFile("test.html");
        assertThat(doc.getContent(), not(containsString("Test Tika title")));
        assertThat(doc.getContent(), containsString("This second part of the text is in Page 2"));

        // Test HTML parser is never used, TXT parser used instead
        doc = extractFromFile("test.html", fsSettings);
        assertThat(doc.getContent(), containsString("<title>Test Tika title</title>"));

        // Test that default parser for XHTML is HTML parser
        doc = extractFromFile("test.xhtml");
        assertThat(doc.getContent(), not(containsString("Test Tika title")));
        assertThat(doc.getContent(), containsString("This is an example of XHTML"));

        // Test XML parser is used to parse XHTML
        doc = extractFromFile("test.xhtml", fsSettings);
        assertThat(doc.getContent(), containsString("Test Tika title"));
        assertThat(doc.getContent(), not(containsString("<title>Test Tika title</title>")));
    }

    @Test
    public void testShiftJisEncoding() throws IOException {
        Doc doc = extractFromFile("issue-400-shiftjis.txt");
        assertThat(doc.getContent(), not(isEmptyOrNullString()));
    }

    /**
     * Test case for <a href="https://github.com/dadoonet/fscrawler/issues/1097">https://github.com/dadoonet/fscrawler/issues/1097</a>.
     * Related to <a href="https://issues.apache.org/jira/browse/TIKA-3364">https://issues.apache.org/jira/browse/TIKA-3364</a>.
     * @throws IOException In case something goes wrong
     */
    @Test
    public void testPdfIssue1097() throws IOException {
        // Run the test with or without OCR as the behavior changes
        boolean withOcr = isOcrAvailable && randomBoolean();
        FsSettings fsSettings = FsSettings.builder(getCurrentTestName())
                .setFs(Fs.builder().setRawMetadata(true).setOcr(Ocr.builder().setEnabled(withOcr).build()).build())
                .build();
        Doc doc = extractFromFile("issue-1097.pdf", fsSettings);
        // TODO This test is now passing but should be failing with ocr when
        // https://issues.apache.org/jira/browse/TIKA-3364 is solved
        assertThat(doc.getContent(), is(withOcr ?
                "\nDummy PDF file\n\nDummy PDF file\n\n\n\n" :
                "\nDummy PDF file\n\n\n"));

        // Meta data
        assertThat(doc.getMeta().getAuthor(), not(nullValue()));
        assertThat(doc.getMeta().getDate(), is(nullValue()));
        assertThat(doc.getMeta().getKeywords(), nullValue());
        assertThat(doc.getMeta().getTitle(), is(nullValue()));

        Map<String, String> raw = doc.getMeta().getRaw();
        assertThat(raw.entrySet(), iterableWithSize(35));
        assertThat(raw, hasEntry("pdf:unmappedUnicodeCharsPerPage", "0"));
        assertThat(raw, hasEntry("pdf:PDFVersion", "1.4"));
        assertThat(raw, hasEntry("xmp:CreatorTool", "Writer"));
        assertThat(raw, hasEntry("pdf:hasXFA", "false"));
        assertThat(raw, hasEntry("access_permission:modify_annotations", "true"));
        assertThat(raw, hasEntry("access_permission:can_print_degraded", "true"));
        assertThat(raw, hasEntry("X-TIKA:Parsed-By-Full-Set", "org.apache.tika.parser.pdf.PDFParser"));
        assertThat(raw, hasEntry("dc:creator", "Evangelos Vlachogiannis"));
        assertThat(raw, hasEntry("pdf:num3DAnnotations", "0"));
        assertThat(raw, hasEntry("dcterms:created", "2007-02-23T15:56:37Z"));
        assertThat(raw, hasEntry("dc:format", "application/pdf; version=1.4"));
        assertThat(raw, hasEntry("pdf:docinfo:creator_tool", "Writer"));
        assertThat(raw, hasEntry("pdf:overallPercentageUnmappedUnicodeChars", "0.0"));
        assertThat(raw, hasEntry("access_permission:fill_in_form", "true"));
        assertThat(raw, hasEntry("pdf:hasCollection", "false"));
        assertThat(raw, hasEntry("pdf:encrypted", "false"));
        assertThat(raw, hasEntry("pdf:containsNonEmbeddedFont", "false"));
        assertThat(raw, hasEntry("pdf:hasMarkedContent", "false"));
        assertThat(raw, hasEntry("Content-Type", "application/pdf"));
        assertThat(raw, hasEntry("pdf:docinfo:creator", "Evangelos Vlachogiannis"));
        assertThat(raw, hasEntry("pdf:producer", "OpenOffice.org 2.1"));
        assertThat(raw, hasEntry("pdf:totalUnmappedUnicodeChars", "0"));
        assertThat(raw, hasEntry("access_permission:extract_for_accessibility", "true"));
        assertThat(raw, hasEntry("access_permission:assemble_document", "true"));
        assertThat(raw, hasEntry("xmpTPg:NPages", "1"));
        assertThat(raw, hasEntry("resourceName", "issue-1097.pdf"));
        assertThat(raw, hasEntry("pdf:hasXMP", "false"));
        assertThat(raw, hasEntry("pdf:charsPerPage", "14"));
        assertThat(raw, hasEntry("access_permission:extract_content", "true"));
        assertThat(raw, hasEntry("access_permission:can_print", "true"));
        assertThat(raw, hasEntry("X-TIKA:Parsed-By", "org.apache.tika.parser.pdf.PDFParser"));
        assertThat(raw, hasEntry("access_permission:can_modify", "true"));
        assertThat(raw, hasEntry("pdf:docinfo:producer", "OpenOffice.org 2.1"));
        assertThat(raw, hasEntry("pdf:docinfo:created", "2007-02-23T15:56:37Z"));
        assertThat(raw, hasEntry("pdf:containsDamagedFont", "false"));
    }

    /**
     * Test case for <a href="https://github.com/dadoonet/fscrawler/issues/834">https://github.com/dadoonet/fscrawler/issues/834</a>.
     * @throws IOException In case something goes wrong
     */
    @Test
    public void testEmptyFileIssue834() throws IOException {
        FsSettings fsSettings = FsSettings.builder(getCurrentTestName())
                .setFs(Fs.builder().setRawMetadata(true).build())
                .build();
        Doc doc = extractFromFile("issue-834.txt", fsSettings);
        assertThat(doc.getContent(), isEmptyString());

        // Meta data
        Map<String, String> raw = doc.getMeta().getRaw();
        assertThat(raw.entrySet(), iterableWithSize(2));
        assertThat(raw, hasEntry("Content-Type", "text/plain"));
        assertThat(raw, hasEntry("resourceName", "issue-834.txt"));
    }

    /**
     * Test protected document
     */
    @Test
    public void testProtectedDocument() throws IOException {
        FsSettings fsSettings = FsSettings.builder(getCurrentTestName()).build();
        Doc doc = extractFromFile("test-protected.docx", fsSettings);
        assertThat(doc.getFile().getContentType(), is("application/x-tika-ooxml-protected"));
    }

    @Test
    public void testDocxWithEmbeddedBadPDF() throws IOException {
        Doc doc = extractFromFile("issue-stackoverflow.docx");
        assertThat(doc.getContent(), not(isEmptyOrNullString()));
    }

    private Doc extractFromFileExtension(String extension) throws IOException {
        FsSettings fsSettings = FsSettings.builder(getCurrentTestName())
                .setFs(Fs.builder().setRawMetadata(true).build())
                .build();
        return extractFromFile("test." + extension, fsSettings);
    }

    private Doc extractFromFile(String filename) throws IOException {
        return extractFromFile(filename, FsSettings.builder(getCurrentTestName()).build());
    }

    private Doc extractFromFile(String filename, FsSettings fsSettings) throws IOException {
        logger.info("Test extraction of [{}]", filename);
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

        // We make sure we reload a new Tika instance any time we test
        TikaInstance.reloadTika();
        TikaDocParser.generate(
                fsSettings,
                data,
                filename,
                "/documents/" + filename,
                doc,
                messageDigest,
                0);

        logger.debug("Generated Content: [{}]", doc.getContent());
        logger.debug("Generated Raw Metadata: [{}]", doc.getMeta().getRaw());

        return doc;
    }
}
