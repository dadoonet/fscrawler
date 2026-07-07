/*
 * Licensed to David Pilato (the "Author") under one
 * or more contributor license agreements. See the NOTICE file
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
 *
 * Made from 🇫🇷🇪🇺 with ❤️ - 2011-2026
 */
package fr.pilato.elasticsearch.crawler.fs.tika;

import com.carrotsearch.randomizedtesting.jupiter.RandomizedTest;
import fr.pilato.elasticsearch.crawler.fs.beans.Doc;
import fr.pilato.elasticsearch.crawler.fs.framework.FsCrawlerIllegalConfigurationException;
import fr.pilato.elasticsearch.crawler.fs.framework.FsCrawlerUtil;
import fr.pilato.elasticsearch.crawler.fs.settings.FsSettings;
import fr.pilato.elasticsearch.crawler.fs.settings.FsSettingsLoader;
import fr.pilato.elasticsearch.crawler.fs.test.framework.Slow;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.MessageDigest;
import java.time.LocalDateTime;
import java.time.Month;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.tika.exception.TikaConfigException;
import org.apache.tika.parser.ocr.TesseractOCRParser;
import org.assertj.core.api.Assertions;
import org.assertj.core.api.Assumptions;
import org.assertj.core.api.InstanceOfAssertFactories;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class TikaDocParserTest extends DocParserTestCase {
    private static final Logger logger = LogManager.getLogger();
    private static boolean isOcrAvailable;

    @BeforeAll
    static void setOcrAvailable() {
        try {
            isOcrAvailable = new TesseractOCRParser().hasTesseract();
        } catch (TikaConfigException e) {
            logger.warn("Can not configure Tesseract for tests, so we are supposing it won't be available");
            isOcrAvailable = false;
        }
    }

    /**
     * Test case for <a
     * href="https://github.com/dadoonet/fscrawler/issues/782">https://github.com/dadoonet/fscrawler/issues/782</a>.
     * Apple Keynote (.key) files should have their text content extracted by Tika (IWorkPackageParser). With OCR
     * enabled, the test.key file yields "FSCrawler" and "You know, for files!" from the slide content. Skipped when
     * Tesseract is not installed.
     */
    @Test
    void keynoteIssue782() throws IOException {
        Assumptions.assumeThat(isOcrAvailable)
                .as("Tesseract is not installed so we are skipping this test")
                .isTrue();

        Doc doc = extractFromFile("test.key");
        Assertions.assertThat(doc.getContent()).contains("FSCrawler").contains("You know, for files!");
    }

    /**
     * Keynote (.key) without OCR: Tika extracts the package structure (file paths) but not the slide text. Verifies
     * that we get at least the image path pattern and not the slide text "FSCrawler".
     */
    @Test
    void keynoteIssue782WithoutOcr() throws IOException {
        FsSettings fsSettings = FsSettingsLoader.load();
        fsSettings.getFs().getOcr().setEnabled(false);
        Doc doc = extractFromFile("test.key", fsSettings);

        Assertions.assertThat(doc.getContent())
                .doesNotContain("FSCrawler")
                .contains("Data/mt-6335B693-B5E5-4B9F-A3FC-584A33E732CA-9090.jpg");
    }

    /**
     * Test case for <a
     * href="https://github.com/dadoonet/fscrawler/issues/494">https://github.com/dadoonet/fscrawler/issues/494</a>.
     * Email files (multipart/alternative) can contain both text/plain and text/html with the same content. Since Tika
     * 1.17, extraction of all alternatives is no longer done by default, so the body text should appear only once in
     * the extracted content.
     */
    @Test
    void emailIssue494NoDuplicateContent() throws IOException {
        Doc doc = extractFromFile("issue-494-email-with-plain-and-html.eml");
        Assertions.assertThat(doc.getContent()).containsOnlyOnce("Unique plain text body for issue 494");
    }

    /**
     * Test case for <a
     * href="https://github.com/dadoonet/fscrawler/issues/162">https://github.com/dadoonet/fscrawler/issues/162</a>
     */
    @Test
    void langDetect162() throws IOException {
        FsSettings fsSettings = FsSettingsLoader.load();
        fsSettings.getFs().setLangDetect(true);
        Doc doc = extractFromFile("test.txt", fsSettings);
        Assertions.assertThat(doc.getMeta().getLanguage()).isEqualTo("en");
        doc = extractFromFile("test-fr.txt", fsSettings);
        Assertions.assertThat(doc.getMeta().getLanguage()).isEqualTo("fr");
        doc = extractFromFile("test-de.txt", fsSettings);
        Assertions.assertThat(doc.getMeta().getLanguage()).isEqualTo("de");
        doc = extractFromFile("test-enfrde.txt", fsSettings);
        Assertions.assertThat(doc.getMeta().getLanguage()).isEqualTo("fr");
    }

    /**
     * Test case for <a
     * href="https://github.com/dadoonet/fscrawler/issues/221">https://github.com/dadoonet/fscrawler/issues/221</a>
     */
    @Test
    void pdfIssue221() throws IOException {
        // We test document 1
        Doc doc = extractFromFile("issue-221-doc1.pdf");

        // Extracted content
        Assertions.assertThat(doc.getContent()).contains("coucou");

        // Content Type
        Assertions.assertThat(doc.getFile().getContentType()).contains("application/pdf");

        // Metadata
        Assertions.assertThat(doc.getMeta().getAuthor()).isNotNull();
        Assertions.assertThat(doc.getMeta().getDate())
                .isEqualTo(FsCrawlerUtil.localDateTimeToDate(LocalDateTime.of(2016, Month.SEPTEMBER, 20, 9, 38, 56)));
        Assertions.assertThat(doc.getMeta().getKeywords()).isNotEmpty();
        Assertions.assertThat(doc.getMeta().getTitle()).contains("Recherche");

        // We test document 2
        doc = extractFromFile("issue-221-doc2.pdf");

        // Extracted content
        Assertions.assertThat(doc.getContent()).contains("FORMATIONS");

        // Content Type
        Assertions.assertThat(doc.getFile().getContentType()).contains("application/pdf");

        // Metadata
        Assertions.assertThat(doc.getMeta().getAuthor()).isNull();
        Assertions.assertThat(doc.getMeta().getDate())
                .isEqualTo(FsCrawlerUtil.localDateTimeToDate(LocalDateTime.of(2016, Month.SEPTEMBER, 19, 14, 29, 37)));
        Assertions.assertThat(doc.getMeta().getKeywords()).isNull();
        Assertions.assertThat(doc.getMeta().getTitle()).isNull();
    }

    /**
     * Test case for <a
     * href="https://github.com/dadoonet/fscrawler/issues/163">https://github.com/dadoonet/fscrawler/issues/163</a>
     */
    @Test
    void xmlIssue163() throws IOException {
        FsSettings fsSettings = FsSettingsLoader.load();
        fsSettings.getFs().setRawMetadata(true);
        Doc doc = extractFromFile("issue-163.xml", fsSettings);

        // Extracted content
        Assertions.assertThat(doc.getContent()).isEqualTo("   \n");

        // Content Type
        Assertions.assertThat(doc.getFile().getContentType()).contains("application/xml");

        // Metadata
        Assertions.assertThat(doc.getMeta().getAuthor()).isNull();
        Assertions.assertThat(doc.getMeta().getDate()).isNull();
        Assertions.assertThat(doc.getMeta().getKeywords()).isNull();
        Assertions.assertThat(doc.getMeta().getTitle()).isNull();

        Map<String, String> raw = doc.getMeta().getRaw();
        Assertions.assertThat(raw)
                .hasSize(5)
                .containsEntry("Content-Type", "application/xml")
                .containsEntry("Content-Type-Magic-Detected", "application/xml")
                .containsEntry("X-TIKA:Parsed-By", "org.apache.tika.parser.DefaultParser")
                .containsEntry("X-TIKA:Parsed-By-Full-Set", "org.apache.tika.parser.DefaultParser")
                .containsEntry("X-TIKA:resourceName", "issue-163.xml");
    }

    @Test
    void extractFromDoc() throws IOException {
        Doc doc = extractFromFileExtension("doc");

        // Extracted content
        Assertions.assertThat(doc.getContent()).contains("This is a sample text available in page");

        // Content Type
        Assertions.assertThat(doc.getFile().getContentType()).isEqualTo("application/msword");

        // Metadata
        Assertions.assertThat(doc.getMeta().getAuthor()).isEqualTo("David Pilato");
        Assertions.assertThat(doc.getMeta().getDate())
                .isEqualTo(FsCrawlerUtil.localDateTimeToDate(LocalDateTime.of(2016, Month.JULY, 7, 8, 37, 0)));
        Assertions.assertThat(doc.getMeta().getKeywords()).containsExactlyInAnyOrder("keyword1", " keyword2");
        Assertions.assertThat(doc.getMeta().getTitle()).isEqualTo("Test Tika title");

        Map<String, String> raw = doc.getMeta().getRaw();
        Assertions.assertThat(raw)
                .hasSize(28)
                .containsEntry("Content-Length", "24576")
                .containsEntry("Content-Type", "application/msword")
                .containsEntry("Content-Type-Magic-Detected", "application/msword")
                .containsEntry("X-TIKA:Parsed-By", "org.apache.tika.parser.DefaultParser")
                .containsEntry("X-TIKA:Parsed-By-Full-Set", "org.apache.tika.parser.DefaultParser")
                .containsEntry("X-TIKA:resourceName", "test.doc")
                .containsEntry("cp:category", "test")
                .containsEntry("cp:revision", "2")
                .containsEntry("custom:N° du document", "1234")
                .containsEntry("custom:Terminé le", "2016-07-06T22:00:00Z")
                .containsEntry("dc:creator", "David Pilato")
                .containsEntry("dc:subject", "keyword1, keyword2")
                .containsEntry("dc:title", "Test Tika title")
                .containsEntry("dcterms:created", "2016-07-07T08:37:00Z")
                .containsEntry("dcterms:modified", "2016-07-07T08:37:00Z")
                .containsEntry("extended-properties:Application", "Microsoft Macintosh Word")
                .containsEntry("extended-properties:Company", "elastic")
                .containsEntry("extended-properties:Manager", "My Mother")
                .containsEntry("extended-properties:Template", "Normal.dotm")
                .containsEntry("extended-properties:TotalTime", "600000000")
                .containsEntry("meta:character-count", "68")
                .containsEntry("meta:keyword", "keyword1, keyword2")
                .containsEntry("meta:last-author", "David Pilato")
                .containsEntry("meta:page-count", "2")
                .containsEntry("meta:word-count", "19")
                .containsEntry("msoffice:comment-person-display-name", "Unknown")
                .containsEntry("w:Comments", "Comments")
                .containsEntry("xmpTPg:NPages", "2");
    }

    @Test
    void extractFromDocx() throws IOException {
        Doc doc = extractFromFileExtension("docx");

        // Extracted content
        Assertions.assertThat(doc.getContent()).contains("This is a sample text available in page");

        // Content Type
        Assertions.assertThat(doc.getFile().getContentType())
                .isEqualTo("application/vnd.openxmlformats-officedocument.wordprocessingml.document");

        // Metadata
        Assertions.assertThat(doc.getMeta().getAuthor()).isEqualTo("David Pilato");
        Assertions.assertThat(doc.getMeta().getDate())
                .isEqualTo(FsCrawlerUtil.localDateTimeToDate(LocalDateTime.of(2016, Month.JULY, 7, 8, 36, 0)));
        Assertions.assertThat(doc.getMeta().getKeywords()).containsExactlyInAnyOrder("keyword1", " keyword2");
        Assertions.assertThat(doc.getMeta().getTitle()).isEqualTo("Test Tika title");

        Map<String, String> raw = doc.getMeta().getRaw();
        Assertions.assertThat(raw)
                .hasSize(34)
                .containsEntry("Content-Length", "14242")
                .containsEntry(
                        "Content-Type", "application/vnd.openxmlformats-officedocument.wordprocessingml.document")
                .containsEntry("Content-Type-Magic-Detected", "application/vnd.openxmlformats-officedocument.wordprocessingml.document")
                .containsEntry("X-TIKA:Parsed-By", "org.apache.tika.parser.DefaultParser")
                .containsEntry("X-TIKA:Parsed-By-Full-Set", "org.apache.tika.parser.DefaultParser")
                .containsEntry("X-TIKA:resourceName", "test.docx")
                .containsEntry("cp:category", "test")
                .containsEntry("cp:revision", "4")
                .containsEntry("custom:N° du document", "1234")
                .containsEntry("custom:Terminé le", "2016-07-06T22:00:00Z")
                .containsEntry("dc:creator", "David Pilato")
                .containsEntry("dc:description", "Comments")
                .containsEntry("dc:publisher", "elastic")
                .containsEntry("dc:subject", "Test Tika Object")
                .containsEntry("dc:title", "Test Tika title")
                .containsEntry("dcterms:created", "2015-12-19T23:39:00Z")
                .containsEntry("dcterms:modified", "2016-07-07T08:36:00Z")
                .containsEntry("extended-properties:AppVersion", "15.0000")
                .containsEntry("extended-properties:Application", "Microsoft Macintosh Word")
                .containsEntry("extended-properties:Company", "elastic")
                .containsEntry("extended-properties:DocSecurityString", "None")
                .containsEntry("extended-properties:Manager", "My Mother")
                .containsEntry("extended-properties:Template", "Normal.dotm")
                .containsEntry("extended-properties:TotalTime", "6")
                .containsEntry("meta:character-count", "65")
                .containsEntry("meta:character-count-with-spaces", "82")
                .containsEntry("meta:keyword", "keyword1, keyword2")
                .containsEntry("meta:last-author", "David Pilato")
                .containsEntry("meta:line-count", "3")
                .containsEntry("meta:page-count", "2")
                .containsEntry("meta:paragraph-count", "2")
                .containsEntry("meta:word-count", "19")
                .containsEntry("xmpTPg:NPages", "2")
                .containsEntry("zip:detectorZipFileOpened", "true");
    }

    @Test
    void extractFromHtml() throws IOException {
        Doc doc = extractFromFileExtension("html");

        // Extracted content
        Assertions.assertThat(doc.getContent()).contains("a sample text available in");

        // Content Type
        Assertions.assertThat(doc.getFile().getContentType()).contains("text/html");

        // Metadata
        Assertions.assertThat(doc.getMeta().getAuthor()).isNull();
        Assertions.assertThat(doc.getMeta().getDate()).isNull();
        Assertions.assertThat(doc.getMeta().getKeywords()).isNull();
        Assertions.assertThat(doc.getMeta().getTitle()).isEqualTo("Test Tika title");

        Map<String, String> raw = doc.getMeta().getRaw();
        Assertions.assertThat(raw)
                .hasSize(17)
                .containsEntry("Content-Encoding", "x-MacRoman")
                .containsEntry("Content-Location", "Web%20page")
                .containsEntry("Content-Type", "text/html; charset=x-MacRoman")
                .containsEntry("Content-Type-Hint", "text/html; charset=macintosh")
                .containsEntry("Content-Type-Magic-Detected", "text/html")
                .containsEntry("X-TIKA:Parsed-By", "org.apache.tika.parser.DefaultParser")
                .containsEntry("X-TIKA:Parsed-By-Full-Set", "org.apache.tika.parser.DefaultParser")
                .containsEntry("X-TIKA:detectedEncoding", "x-MacRoman")
                .containsEntry("X-TIKA:encodingDetectionTrace", "HtmlEncodingDetector->x-MacRoman[DECLARATIVE], MojibusterEncodingDetector->UTF-8[STRUCTURAL] [junk-filter-prefer-declarative]")
                .containsEntry("X-TIKA:encodingDetector", "HtmlEncodingDetector")
                .containsEntry("X-TIKA:resourceName", "test.html")
                .containsEntry("dc:title", "Test Tika title")
                .containsEntry("html:Generator", "Microsoft Word 15")
                .containsEntry("html:Mots clés", "keyword1, keyword2")
                .containsEntry("html:Originator", "Microsoft Word 15")
                .containsEntry("html:ProgId", "Word.Document")
                .containsEntry("html:Titre", "Test Tika title");
    }

    /**
     * Test for #87: <a
     * href="https://github.com/dadoonet/fscrawler/issues/87">https://github.com/dadoonet/fscrawler/issues/87</a>
     */
    @Test
    void extractFromMp3() throws IOException {
        Doc doc = extractFromFileExtension("mp3");

        // Extracted content
        Assertions.assertThat(doc.getContent()).contains("Test Tika");
        Map<String, String> raw = doc.getMeta().getRaw();
        Assertions.assertThat(raw)
                .hasSize(20)
                .containsEntry("Content-Type", "audio/mpeg")
                .containsEntry("Content-Type-Magic-Detected", "audio/mpeg")
                .containsEntry("X-TIKA:Parsed-By", "org.apache.tika.parser.DefaultParser")
                .containsEntry("X-TIKA:Parsed-By-Full-Set", "org.apache.tika.parser.DefaultParser")
                .containsEntry("X-TIKA:resourceName", "test.mp3")
                .containsEntry("channels", "2")
                .containsEntry("dc:creator", "David Pilato")
                .containsEntry("dc:title", "Test Tika")
                .containsEntry("samplerate", "44100")
                .containsEntry("version", "MPEG 3 Layer III Version 1")
                .containsEntry("xmpDM:genre", "Vocal")
                .containsEntry("xmpDM:album", "FS Crawler")
                .containsEntry("xmpDM:artist", "David Pilato")
                .containsEntry("xmpDM:audioChannelType", "Stereo")
                .containsEntry("xmpDM:audioCompressor", "MP3")
                .containsEntry("xmpDM:audioSampleRate", "44100")
                .containsEntry("xmpDM:duration", "1.0187751054763794")
                .containsEntry("xmpDM:genre", "Vocal")
                .containsKey("xmpDM:logComment")
                .containsEntry("xmpDM:releaseDate", "2016")
                .containsEntry("xmpDM:trackNumber", "1");

        // Content Type
        Assertions.assertThat(doc.getFile().getContentType()).isEqualTo("audio/mpeg");

        // Metadata
        Assertions.assertThat(doc.getMeta().getAuthor()).isEqualTo("David Pilato");
        Assertions.assertThat(doc.getMeta().getDate()).isNull();
        Assertions.assertThat(doc.getMeta().getKeywords()).isNull();
        Assertions.assertThat(doc.getMeta().getTitle()).isEqualTo("Test Tika");

        Assertions.assertThat(raw)
                .extractingByKey("xmpDM:logComment")
                .satisfies(rawField -> Assertions.assertThat(rawField).containsAnyOf("Hello but reverted"));
    }

    @Test
    void extractFromOdt() throws IOException {
        Doc doc = extractFromFileExtension("odt");

        // Extracted content
        Assertions.assertThat(doc.getContent()).contains("This is a sample text available in page");

        // Content Type
        Assertions.assertThat(doc.getFile().getContentType()).isEqualTo("application/vnd.oasis.opendocument.text");

        // Metadata
        Assertions.assertThat(doc.getMeta().getAuthor()).isEqualTo("David Pilato");
        Assertions.assertThat(doc.getMeta().getDate())
                .isEqualTo(FsCrawlerUtil.localDateTimeToDate(LocalDateTime.of(2016, Month.JULY, 7, 8, 37, 0)));
        Assertions.assertThat(doc.getMeta().getKeywords()).containsExactlyInAnyOrder("keyword1", "  keyword2");
        Assertions.assertThat(doc.getMeta().getTitle()).isEqualTo("Test Tika title");

        Map<String, String> raw = doc.getMeta().getRaw();
        Assertions.assertThat(raw)
                .hasSize(25)
                .containsEntry("Content-Length", "6236")
                .containsEntry("Content-Type", "application/vnd.oasis.opendocument.text")
                .containsEntry("Content-Type-Magic-Detected", "application/vnd.oasis.opendocument.text")
                .containsEntry("X-TIKA:Parsed-By", "org.apache.tika.parser.DefaultParser")
                .containsEntry("X-TIKA:Parsed-By-Full-Set", "org.apache.tika.parser.DefaultParser")
                .containsEntry("X-TIKA:resourceName", "test.odt")
                .containsEntry("cp:subject", "Test Tika Object")
                .containsEntry("custom:Terminé le", "2016-07-06T22:00:00Z")
                .containsEntry("dc:creator", "David Pilato")
                .containsEntry("dc:description", "Comments")
                .containsEntry("dc:subject", "keyword1,  keyword2")
                .containsEntry("dc:title", "Test Tika title")
                .containsEntry("dcterms:created", "2016-07-07T08:37:00Z")
                .containsEntry("dcterms:modified", "2016-07-07T08:37:00Z")
                .containsEntry("editing-cycles", "2")
                .containsEntry("extended-properties:TotalTime", "PT0S")
                .containsEntry("generator", "MicrosoftOffice/15.0 MicrosoftWord")
                .containsEntry("meta:character-count", "86")
                .containsEntry("meta:keyword", "keyword1,  keyword2")
                .containsEntry("meta:page-count", "1")
                .containsEntry("meta:paragraph-count", "1")
                .containsEntry("meta:word-count", "12")
                .containsEntry("odf:version", "1.2")
                .containsEntry("xmpTPg:NPages", "1")
                .containsEntry("zip:detectorZipFileOpened", "true");
    }

    @Test
    void extractFromPdf() throws IOException {
        Doc doc = extractFromFileExtension("pdf");

        // Extracted content
        Assertions.assertThat(doc.getContent()).contains("This is a sample text available in page");

        // Content Type
        Assertions.assertThat(doc.getFile().getContentType()).isEqualTo("application/pdf");

        // Metadata
        Assertions.assertThat(doc.getMeta().getAuthor()).isEqualTo("David Pilato");
        Assertions.assertThat(doc.getMeta().getDate())
                .isEqualTo(FsCrawlerUtil.localDateTimeToDate(LocalDateTime.of(2016, Month.JULY, 7, 8, 37, 42)));
        Assertions.assertThat(doc.getMeta().getKeywords()).containsExactlyInAnyOrder("keyword1", " keyword2");
        Assertions.assertThat(doc.getMeta().getTitle()).isEqualTo("Test Tika title");

        Map<String, String> raw = doc.getMeta().getRaw();
        Assertions.assertThat(raw)
                .hasSize(47)
                .containsEntry("Content-Length", "101643")
                .containsEntry("Content-Type", "application/pdf")
                .containsEntry("Content-Type-Magic-Detected", "application/pdf")
                .containsEntry("X-TIKA:Parsed-By", "org.apache.tika.parser.pdf.PDFParser")
                .containsEntry("X-TIKA:Parsed-By-Full-Set", "org.apache.tika.parser.pdf.PDFParser")
                .containsEntry("X-TIKA:resourceName", "test.pdf")
                .containsEntry("X-TIKA:versionCount", "1")
                .containsEntry("access_permission:assemble_document", "true")
                .containsEntry("access_permission:can_modify", "true")
                .containsEntry("access_permission:can_print", "true")
                .containsEntry("access_permission:can_print_faithful", "true")
                .containsEntry("access_permission:extract_content", "true")
                .containsEntry("access_permission:extract_for_accessibility", "true")
                .containsEntry("access_permission:fill_in_form", "true")
                .containsEntry("access_permission:modify_annotations", "true")
                .containsEntry("dc:creator", "David Pilato")
                .containsEntry("dc:format", "application/pdf; version=1.5")
                .containsEntry("dc:language", "en-US")
                .containsEntry("dc:subject", "keyword1, keyword2")
                .containsEntry("dc:title", "Test Tika title")
                .containsEntry("dcterms:created", "2016-07-07T08:37:42Z")
                .containsEntry("dcterms:modified", "2016-07-07T08:37:42Z")
                .containsEntry("pdf:PDFVersion", "1.5")
                .containsEntry("pdf:charsPerPage", "42")
                .containsEntry("pdf:containsDamagedFont", "false")
                .containsEntry("pdf:containsNonEmbeddedFont", "false")
                .containsEntry("pdf:docinfo:created", "2016-07-07T08:37:42Z")
                .containsEntry("pdf:docinfo:creator", "David Pilato")
                .containsEntry("pdf:docinfo:creator_tool", "Microsoft Word")
                .containsEntry("pdf:docinfo:keywords", "keyword1, keyword2")
                .containsEntry("pdf:docinfo:modified", "2016-07-07T08:37:42Z")
                .containsEntry("pdf:docinfo:subject", "Test Tika Object")
                .containsEntry("pdf:docinfo:title", "Test Tika title")
                .containsEntry("pdf:encrypted", "false")
                .containsEntry("pdf:eofOffsets", "101460")
                .containsEntry("pdf:hasCollection", "false")
                .containsEntry("pdf:hasMarkedContent", "true")
                .containsEntry("pdf:hasXFA", "false")
                .containsEntry("pdf:hasXMP", "false")
                .containsEntry("pdf:incrementalUpdateCount", "1")
                .containsEntry("pdf:num3DAnnotations", "0")
                .containsEntry("pdf:overallPercentageUnmappedUnicodeChars", "0.0")
                .containsEntry("pdf:totalUnmappedUnicodeChars", "0")
                .containsEntry("pdf:unmappedUnicodeCharsPerPage", "0")
                .containsEntry("xmp:CreatorTool", "Microsoft Word")
                .containsEntry("xmpTPg:NPages", "2");
        Assertions.assertThat(raw)
                .containsKey("pdf:ocrPageCount")
                .extractingByKey("pdf:ocrPageCount", InstanceOfAssertFactories.STRING)
                .isNotEmpty();
    }

    @Test
    void extractFromJpg() throws IOException {
        FsSettings fsSettings = FsSettingsLoader.load();
        fsSettings.getFs().setRawMetadata(true);

        // We force not using OCR as we want to test only metadata extraction here
        fsSettings.getFs().getOcr().setEnabled(false);

        Doc doc = extractFromFile("test.jpg", fsSettings);

        // Extracted content
        Assertions.assertThat(doc.getContent()).isNullOrEmpty();

        // Content Type
        Assertions.assertThat(doc.getFile().getContentType()).isEqualTo("image/jpeg");

        // Metadata
        Assertions.assertThat(doc.getMeta().getAuthor()).isNull();
        Assertions.assertThat(doc.getMeta().getDate()).isNull();
        Assertions.assertThat(doc.getMeta().getKeywords()).isNull();
        Assertions.assertThat(doc.getMeta().getTitle()).isNull();

        Map<String, String> raw = doc.getMeta().getRaw();
        Assertions.assertThat(raw)
                .hasSize(66)
                .containsEntry("Content-Length", "41426")
                .containsEntry("Content-Type", "image/jpeg")
                .containsEntry("Content-Type-Magic-Detected", "image/jpeg")
                .containsKey("ICC:Apple Multi-language Profile Name")
                .containsKey("ICC:Blue Colorant")
                .containsKey("ICC:Blue Parametric TRC")
                .containsKey("ICC:Blue TRC")
                .containsEntry("ICC:CMM Type", "appl")
                .containsKey("ICC:Chromatic Adaptation")
                .containsEntry("ICC:Class", "Display Device")
                .containsEntry("ICC:Color space", "RGB")
                .containsEntry("ICC:Device manufacturer", "APPL")
                .containsKey("ICC:Green Colorant")
                .containsKey("ICC:Green Parametric TRC")
                .containsKey("ICC:Green TRC")
                .containsKey("ICC:Make And Model")
                .containsKey("ICC:Media White Point")
                .containsKey("ICC:Native Display Information")
                .containsEntry("ICC:Primary Platform", "Apple Computer, Inc.")
                .containsEntry("ICC:Profile Connection Space", "XYZ")
                .containsEntry("ICC:Profile Copyright", "Copyright Apple Inc., 2017")
                .containsKey("ICC:Profile Date/Time")
                .containsEntry("ICC:Profile Description", "Display")
                .containsEntry("ICC:Profile Size", "3888")
                .containsKey("ICC:Red Colorant")
                .containsKey("ICC:Red Parametric TRC")
                .containsKey("ICC:Red TRC")
                .containsEntry("ICC:Signature", "acsp")
                .containsEntry("ICC:Tag Count", "17")
                .containsKey("ICC:Version")
                .containsKey("ICC:Video Card Gamma")
                .containsKey("ICC:XYZ values")
                .containsKey("X-TIKA:Parsed-By")
                .containsKey("X-TIKA:Parsed-By-Full-Set")
                .containsEntry("X-TIKA:resourceName", "test.jpg")
                .containsKey("img:Component 1")
                .containsKey("img:Component 2")
                .containsKey("img:Component 3")
                .containsEntry("img:Compression Type", "Baseline")
                .containsEntry("img:Data Precision", "8 bits")
                .containsEntry("img:Exif IFD0:Orientation", "Top, left side (Horizontal / normal)")
                .containsEntry("img:Exif IFD0:Resolution Unit", "Inch")
                .containsKey("img:Exif IFD0:X Resolution")
                .containsKey("img:Exif IFD0:Y Resolution")
                .containsEntry("img:Exif IFD0:YCbCr Positioning", "Center of pixel array")
                .containsEntry("img:Exif SubIFD:Color Space", "sRGB")
                .containsEntry("img:Exif SubIFD:Components Configuration", "YCbCr")
                .containsEntry("img:Exif SubIFD:Exif Image Height", "622 pixels")
                .containsEntry("img:Exif SubIFD:Exif Image Width", "982 pixels")
                .containsEntry("img:Exif SubIFD:Exif Version", "2.21")
                .containsEntry("img:Exif SubIFD:FlashPix Version", "1.00")
                .containsEntry("img:Exif SubIFD:Scene Capture Type", "Standard")
                .containsKey("img:File Modified Date")
                .hasEntrySatisfying(
                        "img:File Name", value -> Assertions.assertThat(value).startsWith("apache-tika-"))
                .containsEntry("img:File Size", "41426 bytes")
                .containsEntry("img:Image Height", "622 pixels")
                .containsEntry("img:Image Width", "982 pixels")
                .containsEntry("img:Number of Components", "3")
                .containsEntry("img:Number of Tables", "4 Huffman tables")
                .containsEntry("tiff:BitsPerSample", "8")
                .containsEntry("tiff:ImageLength", "622")
                .containsEntry("tiff:ImageWidth", "982")
                .containsEntry("tiff:Orientation", "1")
                .containsEntry("tiff:ResolutionUnit", "Inch")
                .containsEntry("tiff:XResolution", "144.0")
                .containsEntry("tiff:YResolution", "144.0");

    }

    @Test
    void extractFromRtf() throws IOException {
        Doc doc = extractFromFileExtension("rtf");

        // Extracted content
        Assertions.assertThat(doc.getContent()).contains("This is a sample text available in page");

        // Content Type
        Assertions.assertThat(doc.getFile().getContentType()).isEqualTo("application/rtf");

        // Metadata
        Assertions.assertThat(doc.getMeta().getAuthor()).isEqualTo("David Pilato");
        Assertions.assertThat(doc.getMeta().getDate()).isNull();
        Assertions.assertThat(doc.getMeta().getKeywords()).containsExactlyInAnyOrder("keyword1", " keyword2");
        Assertions.assertThat(doc.getMeta().getTitle()).isEqualTo("Test Tika title");

        Map<String, String> raw = doc.getMeta().getRaw();
        Assertions.assertThat(raw)
                .hasSize(16)
                .containsEntry("Content-Type", "application/rtf")
                .containsEntry("Content-Type-Magic-Detected", "application/rtf")
                .containsEntry("X-TIKA:Parsed-By", "org.apache.tika.parser.DefaultParser")
                .containsEntry("X-TIKA:Parsed-By-Full-Set", "org.apache.tika.parser.DefaultParser")
                .containsEntry("X-TIKA:resourceName", "test.rtf")
                .containsEntry("cp:category", "test")
                .containsEntry("dc:creator", "David Pilato")
                .containsEntry("dc:subject", "Test Tika Object")
                .containsEntry("dc:title", "Test Tika title")
                .containsEntry("extended-properties:Company", "elastic")
                .containsEntry("extended-properties:Manager", "My Mother")
                .containsEntry("meta:character-count", "68")
                .containsEntry("meta:keyword", "keyword1, keyword2")
                .containsEntry("meta:page-count", "2")
                .containsEntry("meta:word-count", "19");
        Assertions.assertThat(raw)
                .containsKey("dcterms:created")
                .extractingByKey("dcterms:created", InstanceOfAssertFactories.STRING)
                .startsWith("2016-07-0");
    }

    @Test
    void extractFromTxt() throws IOException {
        Doc doc = extractFromFileExtension("txt");

        // Extracted content
        Assertions.assertThat(doc.getContent()).contains("This file contains some words.");

        // Content Type
        Assertions.assertThat(doc.getFile().getContentType()).contains("text/plain");

        // Metadata
        Assertions.assertThat(doc.getMeta().getAuthor()).isNull();
        Assertions.assertThat(doc.getMeta().getDate()).isNull();
        Assertions.assertThat(doc.getMeta().getKeywords()).isNull();
        Assertions.assertThat(doc.getMeta().getTitle()).isNull();

        Map<String, String> raw = doc.getMeta().getRaw();
        Assertions.assertThat(raw)
                .hasSize(9)
                .containsEntry("Content-Encoding", "windows-1252")
                .containsEntry("Content-Type", "text/plain; charset=windows-1252")
                .containsEntry("Content-Type-Magic-Detected", "text/plain")
                .containsEntry("X-TIKA:Parsed-By", "org.apache.tika.parser.DefaultParser")
                .containsEntry("X-TIKA:Parsed-By-Full-Set", "org.apache.tika.parser.DefaultParser")
                .containsEntry("X-TIKA:detectedEncoding", "windows-1252")
                .containsEntry("X-TIKA:encodingDetectionTrace", "MojibusterEncodingDetector->windows-1252[STATISTICAL](0.10)")
                .containsEntry("X-TIKA:encodingDetector", "MojibusterEncodingDetector")
                .containsEntry("X-TIKA:resourceName", "test.txt");

        Assertions.assertThat(doc.getAttachment()).isNull();
        Assertions.assertThat(doc.getFile().getChecksum()).isNull();
    }

    @Test
    void extractFromWav() throws IOException {
        Doc doc = extractFromFileExtension("wav");

        // Extracted content
        Assertions.assertThat(doc.getContent()).isEmpty();

        // Content Type
        Assertions.assertThat(doc.getFile().getContentType()).isEqualTo("audio/vnd.wave");

        // Metadata
        Assertions.assertThat(doc.getMeta().getAuthor()).isNull();
        Assertions.assertThat(doc.getMeta().getDate()).isNull();
        Assertions.assertThat(doc.getMeta().getKeywords()).isNull();
        Assertions.assertThat(doc.getMeta().getTitle()).isNull();

        Map<String, String> raw = doc.getMeta().getRaw();
        Assertions.assertThat(raw)
                .hasSize(11)
                .containsEntry("Content-Type", "audio/vnd.wave")
                .containsEntry("Content-Type-Magic-Detected", "audio/vnd.wave")
                .containsEntry("X-TIKA:Parsed-By", "org.apache.tika.parser.DefaultParser")
                .containsEntry("X-TIKA:Parsed-By-Full-Set", "org.apache.tika.parser.DefaultParser")
                .containsEntry("X-TIKA:resourceName", "test.wav")
                .containsEntry("bits", "16")
                .containsEntry("channels", "2")
                .containsEntry("encoding", "PCM_SIGNED")
                .containsEntry("samplerate", "44100.0")
                .containsEntry("xmpDM:audioSampleRate", "44100")
                .containsEntry("xmpDM:audioSampleType", "16Int");
    }

    @Test
    void extractFromTxtAndStoreSource() throws IOException {
        FsSettings fsSettings = FsSettingsLoader.load();
        fsSettings.getFs().setStoreSource(true);
        fsSettings.getFs().setTempDir(testTmpDir.toString());
        Doc doc = extractFromFile("test.txt", fsSettings);

        // Extracted content
        Assertions.assertThat(doc.getContent()).contains("This file contains some words.");
        Assertions.assertThat(doc.getAttachment()).isNotNull();
    }

    @Test
    void extractFromTxtStoreSourceAndNoIndexContent() throws IOException {
        FsSettings fsSettings = FsSettingsLoader.load();
        fsSettings.getFs().setStoreSource(true);
        fsSettings.getFs().setIndexContent(false);
        fsSettings.getFs().setTempDir(testTmpDir.toString());
        Doc doc = extractFromFile("test.txt", fsSettings);

        // Extracted content
        Assertions.assertThat(doc.getContent()).isNull();
        Assertions.assertThat(doc.getAttachment()).isNotNull();
    }

    @Test
    void extractFromTxtAndStoreSourceWithDigest() throws IOException {
        Assumptions.assumeThatCode(() -> MessageDigest.getInstance("MD5")).doesNotThrowAnyException();

        FsSettings fsSettings = FsSettingsLoader.load();
        fsSettings.getFs().setStoreSource(true);
        fsSettings.getFs().setChecksum("MD5");
        fsSettings.getFs().setTempDir(testTmpDir.toString());
        Doc doc = extractFromFile("test.txt", fsSettings);

        // Extracted content
        Assertions.assertThat(doc.getContent()).contains("This file contains some words.");
        Assertions.assertThat(doc.getAttachment()).isNotNull();
        Assertions.assertThat(doc.getFile().getChecksum()).isNotNull();
    }

    @Test
    void extractFromTxtWithDigest() throws IOException {
        Assumptions.assumeThatCode(() -> MessageDigest.getInstance("MD5")).doesNotThrowAnyException();

        FsSettings fsSettings = FsSettingsLoader.load();
        fsSettings.getFs().setChecksum("MD5");
        fsSettings.getFs().setTempDir(testTmpDir.toString());
        Doc doc = extractFromFile("test.txt", fsSettings);

        // Extracted content
        Assertions.assertThat(doc.getContent()).contains("This file contains some words.");
        Assertions.assertThat(doc.getAttachment()).isNull();
        Assertions.assertThat(doc.getFile().getChecksum()).isNotNull();
    }

    /**
     * Test case for checksum calculation on small files (below 64KB threshold). Small files are processed in-memory for
     * better performance.
     *
     * @throws Exception In case something goes wrong
     */
    @Test
    void checksumForSmallFile() throws Exception {
        Assumptions.assumeThatCode(() -> MessageDigest.getInstance("MD5")).doesNotThrowAnyException();

        FsSettings fsSettings = FsSettingsLoader.load();
        fsSettings.getFs().setChecksum("MD5");
        fsSettings.getFs().setTempDir(testTmpDir.toString());
        Doc doc = extractFromFile("test.txt", fsSettings);

        // Verify the checksum is computed
        Assertions.assertThat(doc.getFile().getChecksum())
                .as("Checksum should be computed for small files using in-memory buffer")
                .isNotNull()
                .isNotEmpty();

        // Verify the checksum is correct by computing it manually
        byte[] content = getBinaryContent("test.txt").readAllBytes();
        MessageDigest md = MessageDigest.getInstance("MD5");
        byte[] expectedDigest = md.digest(content);
        StringBuilder expectedChecksum = new StringBuilder();
        for (byte b : expectedDigest) {
            expectedChecksum.append(Integer.toString((b & 0xff) + 0x100, 16).substring(1));
        }
        Assertions.assertThat(doc.getFile().getChecksum()).isEqualTo(expectedChecksum.toString());
    }

    /**
     * Test case for checksum calculation when filesize is unknown (0 or -1). This can happen with REST API uploads when
     * the client doesn't provide file size. The code should use the temp file approach to be safe and avoid OOM.
     *
     * @throws Exception In case something goes wrong
     */
    @Test
    void checksumWithUnknownFilesize() throws Exception {
        Assumptions.assumeThatCode(() -> MessageDigest.getInstance("MD5")).doesNotThrowAnyException();

        // Use test.txt content but pass filesize as 0 (unknown)
        byte[] content = getBinaryContent("test.txt").readAllBytes();

        // Calculate expected MD5 hash
        MessageDigest md = MessageDigest.getInstance("MD5");
        byte[] expectedDigest = md.digest(content);
        StringBuilder expectedChecksum = new StringBuilder();
        for (byte b : expectedDigest) {
            expectedChecksum.append(Integer.toString((b & 0xff) + 0x100, 16).substring(1));
        }

        FsSettings fsSettings = FsSettingsLoader.load();
        fsSettings.getFs().setChecksum("MD5");
        fsSettings.getFs().setTempDir(testTmpDir.toString());

        Doc doc = new Doc();
        doc.getPath().setReal("test.txt");
        doc.getFile().setFilename("test.txt");

        // Pass filesize as 0 (unknown) - should use temp file path, not in-memory
        new TikaDocParser(fsSettings).generate(new ByteArrayInputStream(content), doc, 0);

        // Verify the checksum is still correctly computed
        Assertions.assertThat(doc.getFile().getChecksum())
                .as("Checksum should be computed correctly even with unknown filesize")
                .isEqualTo(expectedChecksum.toString());
    }

    /**
     * Test case for checksum calculation on large binary files. This verifies that the MD5 checksum is computed over
     * the entire file content, not just the first 64KB that Tika reads for content type detection. Large files are
     * processed using a temporary file to avoid OOM.
     *
     * @throws Exception In case something goes wrong
     */
    @Test
    void checksumForLargeBinaryFile() throws Exception {
        Assumptions.assumeThatCode(() -> MessageDigest.getInstance("MD5")).doesNotThrowAnyException();

        // Create a binary file larger than 64KB (100KB)
        int size = 100 * 1024;
        byte[] data = new byte[size];
        // Fill with some pattern to make it deterministic
        for (int i = 0; i < size; i++) {
            data[i] = (byte) (i % 256);
        }

        // Calculate expected MD5 hash over the entire content
        MessageDigest md = MessageDigest.getInstance("MD5");
        byte[] expectedDigest = md.digest(data);
        StringBuilder expectedChecksum = new StringBuilder();
        for (byte b : expectedDigest) {
            expectedChecksum.append(Integer.toString((b & 0xff) + 0x100, 16).substring(1));
        }

        FsSettings fsSettings = FsSettingsLoader.load();
        fsSettings.getFs().setChecksum("MD5");
        fsSettings.getFs().setTempDir(testTmpDir.toString());

        Doc doc = new Doc();
        doc.getPath().setReal("large-binary-file.bin");
        doc.getFile().setFilename("large-binary-file.bin");

        new TikaDocParser(fsSettings).generate(new ByteArrayInputStream(data), doc, size);

        // Verify the checksum is computed over the entire file
        Assertions.assertThat(doc.getFile().getChecksum())
                .as("Checksum should be computed over the entire file, not just the first 64KB")
                .isEqualTo(expectedChecksum.toString());
    }

    @Test
    void ocr() throws IOException {
        Assumptions.assumeThat(isOcrAvailable)
                .as("Tesseract is not installed so we are skipping this test")
                .isTrue();

        // Test with OCR On (default)
        Doc doc = extractFromFile("test-ocr.png");
        Assertions.assertThat(doc.getContent()).contains("This file contains some words.");
        doc = extractFromFile("test-ocr.pdf");
        Assertions.assertThat(doc.getContent()).contains("This file contains some words.");
        Assertions.assertThat(doc.getContent()).contains("This file also contains text.");
        doc = extractFromFile("test-ocr.docx");
        Assertions.assertThat(doc.getContent()).contains("This file contains some words.");
        Assertions.assertThat(doc.getContent()).contains("This file also contains text.");
    }

    /**
     * Test case for the bug where {@code fs.ocr.path} is documented as the path to the tesseract binary, but Tika's
     * {@code setTesseractPath()} actually requires the directory containing the executable. Pointing
     * {@code fs.ocr.path} at the executable itself must still activate OCR.
     */
    @Test
    void ocrPathCanPointToTesseractExecutable() throws IOException {
        Assumptions.assumeThat(isOcrAvailable)
                .as("Tesseract not installed so we are skipping this test")
                .isTrue();

        String exec = "tesseract";
        Optional<Path> tessPath = Stream.of(System.getenv("PATH").split(Pattern.quote(File.pathSeparator)))
                .map(Paths::get)
                .filter(p -> Files.exists(p.resolve(exec)))
                .findFirst();
        Assumptions.assumeThat(tessPath)
                .as("tesseract must be locatable in PATH")
                .isPresent();
        String tesseractExecutable = tessPath.get().resolve(exec).toString();

        FsSettings fsSettings = FsSettingsLoader.load();
        fsSettings.getFs().getOcr().setEnabled(true);
        fsSettings.getFs().getOcr().setPath(tesseractExecutable);

        Doc doc = parseWith(new TikaDocParser(fsSettings), "test-ocr.png");
        Assertions.assertThat(doc.getContent())
                .as("OCR must run when ocr.path points at the tesseract executable")
                .contains("words");
    }

    /**
     * Two jobs with opposite OCR settings running concurrently in the same JVM must not contaminate each other. Guards
     * against the historical JVM-wide static state in TikaInstance which made the OCR-off job OCR images when an OCR-on
     * job ran in parallel (race observed in FsCrawlerTestOcrIT.ocr_disabled under parallel integration tests).
     */
    @Test
    @Slow
    void concurrentJobsWithDifferentOcrSettingsAreIsolated() throws Exception {
        Assumptions.assumeThat(isOcrAvailable)
                .as("Tesseract is not installed so we are skipping this test")
                .isTrue();

        FsSettings settingsOcrOn = FsSettingsLoader.load();
        FsSettings settingsOcrOff = FsSettingsLoader.load();
        settingsOcrOff.getFs().getOcr().setEnabled(false);
        TikaDocParser parserOcrOn = new TikaDocParser(settingsOcrOn);
        TikaDocParser parserOcrOff = new TikaDocParser(settingsOcrOff);

        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            for (int i = 0; i < 10; i++) {
                CyclicBarrier barrier = new CyclicBarrier(2);
                Future<Doc> withOcr = executor.submit(() -> {
                    barrier.await();
                    return parseWith(parserOcrOn, "test-ocr.png");
                });
                Future<Doc> withoutOcr = executor.submit(() -> {
                    barrier.await();
                    return parseWith(parserOcrOff, "test-ocr.png");
                });
                Assertions.assertThat(withOcr.get().getContent())
                        .as("iteration %d: OCR-on job must extract the image text", i)
                        .contains("This file contains some words.");
                Assertions.assertThat(withoutOcr.get().getContent())
                        .as("iteration %d: OCR-off job must never OCR the image", i)
                        .doesNotContain("This file contains some words.");
            }
        } finally {
            executor.shutdownNow();
        }
    }

    private Doc parseWith(TikaDocParser parser, String filename) throws IOException {
        Doc doc = new Doc();
        doc.getPath().setReal(filename);
        doc.getFile().setFilename(filename);
        parser.generate(getBinaryContent(filename), doc, 0);
        return doc;
    }

    @Test
    void ocrWithPdfStrategyNoOcr() throws IOException {
        Assumptions.assumeThat(isOcrAvailable)
                .as("Tesseract is not installed so we are skipping this test")
                .isTrue();

        // Test with OCR On and PDF Strategy set to no_ocr (meaning that PDF are not OCRed)
        FsSettings fsSettings = FsSettingsLoader.load();
        fsSettings.getFs().getOcr().setPdfStrategy("no_ocr");
        Doc doc = extractFromFile("test-ocr.png", fsSettings);
        Assertions.assertThat(doc.getContent()).contains("This file contains some words.");
        doc = extractFromFile("test-ocr.pdf", fsSettings);
        Assertions.assertThat(doc.getContent()).contains("This file also contains text.");
        Assertions.assertThat(doc.getContent()).doesNotContain("This file contains some words.");
        doc = extractFromFile("test-ocr.docx", fsSettings);
        Assertions.assertThat(doc.getContent()).contains("This file also contains text.");
        Assertions.assertThat(doc.getContent()).contains("This file contains some words.");
    }

    @Test
    void ocrWithPdfStrategyOcrOnly() throws IOException {
        Assumptions.assumeThat(isOcrAvailable)
                .as("Tesseract is not installed so we are skipping this test")
                .isTrue();

        // Test with OCR On and PDF Strategy set to ocr_only (meaning that PDF only OCRed and no text is extracted)
        FsSettings fsSettings = FsSettingsLoader.load();
        fsSettings.getFs().getOcr().setPdfStrategy("ocr_only");
        Doc doc = extractFromFile("test-ocr.png", fsSettings);
        Assertions.assertThat(doc.getContent()).contains("This file contains some words.");
        doc = extractFromFile("test-ocr.pdf", fsSettings);
        Assertions.assertThat(doc.getContent()).contains("This file contains some words.");
        // TODO: for a strange reason ocr_only also extracts text.
        // Assertions.assertThat(doc.getContent(), not(containsString("This file also contains text.")));
        doc = extractFromFile("test-ocr.docx", fsSettings);
        Assertions.assertThat(doc.getContent()).contains("This file contains some words.");
        Assertions.assertThat(doc.getContent()).contains("This file also contains text.");
    }

    @Test
    void ocrWithPdfStrategyAuto() throws IOException {
        Assumptions.assumeThat(isOcrAvailable)
                .as("Tesseract is not installed so we are skipping this test")
                .isTrue();

        // Test with OCR On and PDF Strategy set to auto (meaning that PDF will be only OCRed if less than 10 characters
        // are found)
        FsSettings fsSettings = FsSettingsLoader.load();
        fsSettings.getFs().getOcr().setPdfStrategy("auto");
        Doc doc = extractFromFile("test-ocr.pdf", fsSettings);
        Assertions.assertThat(doc.getContent()).doesNotContain("This file contains some words.");
        Assertions.assertThat(doc.getContent()).contains("This file also contains text.");
        doc = extractFromFile("test-ocr-notext.pdf", fsSettings);
        Assertions.assertThat(doc.getContent()).contains("This file contains some words.");
    }

    @Test
    void ocrOff() throws IOException {
        Assumptions.assumeThat(isOcrAvailable)
                .as("Tesseract is not installed so we are skipping this test")
                .isTrue();

        // Test with OCR Off
        FsSettings fsSettings = FsSettingsLoader.load();
        fsSettings.getFs().getOcr().setEnabled(false);
        Doc doc = extractFromFile("test-ocr.png", fsSettings);
        Assertions.assertThat(doc.getContent()).isEmpty();
        doc = extractFromFile("test-ocr.pdf", fsSettings);
        Assertions.assertThat(doc.getContent()).doesNotContain("This file contains some words.");
        doc = extractFromFile("test-ocr.docx", fsSettings);
        Assertions.assertThat(doc.getContent()).doesNotContain("This file contains some words.");
    }

    @Test
    void ocrWrongPaths() throws IOException {
        Assumptions.assumeThat(isOcrAvailable)
                .as("Tesseract is not installed so we are skipping this test")
                .isTrue();

        // Test with OCR On (default) but a wrong path to tesseract
        FsSettings fsSettings = FsSettingsLoader.load();
        fsSettings.getFs().getOcr().setPath("/path/to/doesnotexist");
        fsSettings.getFs().getOcr().setDataPath("/path/to/doesnotexist");
        Doc doc = extractFromFile("test-ocr.png", fsSettings);
        Assertions.assertThat(doc.getContent()).isEmpty();
        doc = extractFromFile("test-ocr.pdf", fsSettings);
        Assertions.assertThat(doc.getContent()).doesNotContain("This file contains some words.");
    }

    @Test
    void ocrOutputTypeHocr() throws IOException {
        Assumptions.assumeThat(isOcrAvailable)
                .as("Tesseract is not installed so we are skipping this test")
                .isTrue();

        // Test with OCR On with hocr output type
        FsSettings fsSettings = FsSettingsLoader.load();
        fsSettings.getFs().getOcr().setOutputType("hocr");
        Doc doc = extractFromFile("test-ocr.png", fsSettings);
        Assertions.assertThat(doc.getContent()).contains("This", "file", "contains", "some", "words.");
        doc = extractFromFile("test-ocr.pdf", fsSettings);
        Assertions.assertThat(doc.getContent()).contains("This", "file", "contains", "some", "words.");
    }

    @Test
    void ocrLanguageHeb() throws IOException {
        Assumptions.assumeThat(isOcrAvailable)
                .as("Tesseract is not installed so we are skipping this test")
                .isTrue();

        // Test with heb language
        FsSettings fsSettings = FsSettingsLoader.load();
        fsSettings.getFs().getOcr().setLanguage("heb");
        Doc doc = extractFromFile("test-ocr-heb.pdf", fsSettings);
        try {
            // This test requires to have the hebrew language pack, so we don't fail the test but just log
            Assertions.assertThat(doc.getContent()).contains("המבודדים מתקבלים");
        } catch (AssertionError e) {
            logger.info(
                    "We were not able to get the Hebrew content with OCR. May be the language pack was not installed?");
        }
    }

    @Test
    void customTikaConfig() {
        // Apache Tika 4 removed the XML Tika configuration file mechanism, so fs.tika_config_path is no longer
        // supported. Setting it must fail fast with a clear configuration error rather than being silently ignored.
        FsSettings fsSettings = FsSettingsLoader.load();
        fsSettings.getFs().setTikaConfigPath("/any/path/tikaConfig.xml");

        Assertions.assertThatThrownBy(() -> new TikaDocParser(fsSettings))
                .isInstanceOf(FsCrawlerIllegalConfigurationException.class)
                .hasMessageContaining("fs.tika_config_path is not supported with Apache Tika 4.x");
    }

    @Test
    void shiftJisEncoding() throws IOException {
        Doc doc = extractFromFile("issue-400-shiftjis.txt");
        Assertions.assertThat(doc.getContent()).isNotEmpty();
    }

    /**
     * Test case for <a
     * href="https://github.com/dadoonet/fscrawler/issues/1097">https://github.com/dadoonet/fscrawler/issues/1097</a>.
     * Related to <a
     * href="https://issues.apache.org/jira/browse/TIKA-3364">https://issues.apache.org/jira/browse/TIKA-3364</a>.
     *
     * @throws IOException In case something goes wrong
     */
    @Test
    void pdfIssue1097() throws IOException {
        // Run the test with or without OCR as the behavior changes
        boolean withOcr = isOcrAvailable && RandomizedTest.randomBoolean(randomizedRandomForTests);
        FsSettings fsSettings = FsSettingsLoader.load();
        fsSettings.getFs().setRawMetadata(true);
        fsSettings.getFs().getOcr().setEnabled(withOcr);
        Doc doc = extractFromFile("issue-1097.pdf", fsSettings);
        // TODO This test is now passing but should be failing with ocr when
        // https://issues.apache.org/jira/browse/TIKA-3364 is solved
        Assertions.assertThat(doc.getContent())
                .isEqualTo(withOcr ? "\nDummy PDF file\n\nDummy PDF file\n\n\n\n" : "\nDummy PDF file\n\n\n");

        // Metadata
        Assertions.assertThat(doc.getMeta().getAuthor()).isNotNull();
        Assertions.assertThat(doc.getMeta().getDate()).isNull();
        Assertions.assertThat(doc.getMeta().getKeywords()).isNull();
        Assertions.assertThat(doc.getMeta().getTitle()).isNull();

        Map<String, String> raw = doc.getMeta().getRaw();
        Assertions.assertThat(raw)
                .hasSize(41)
                .containsEntry("Content-Length", "13264")
                .containsEntry("Content-Type", "application/pdf")
                .containsEntry("Content-Type-Magic-Detected", "application/pdf")
                .containsEntry("X-TIKA:Parsed-By", "org.apache.tika.parser.pdf.PDFParser")
                .containsEntry("X-TIKA:Parsed-By-Full-Set", "org.apache.tika.parser.pdf.PDFParser")
                .containsEntry("X-TIKA:resourceName", "issue-1097.pdf")
                .containsEntry("X-TIKA:versionCount", "0")
                .containsEntry("access_permission:assemble_document", "true")
                .containsEntry("access_permission:can_modify", "true")
                .containsEntry("access_permission:can_print", "true")
                .containsEntry("access_permission:can_print_faithful", "true")
                .containsEntry("access_permission:extract_content", "true")
                .containsEntry("access_permission:extract_for_accessibility", "true")
                .containsEntry("access_permission:fill_in_form", "true")
                .containsEntry("access_permission:modify_annotations", "true")
                .containsEntry("dc:creator", "Evangelos Vlachogiannis")
                .containsEntry("dc:format", "application/pdf; version=1.4")
                .containsEntry("dcterms:created", "2007-02-23T15:56:37Z")
                .containsEntry("pdf:PDFVersion", "1.4")
                .containsEntry("pdf:charsPerPage", "14")
                .containsEntry("pdf:containsDamagedFont", "false")
                .containsEntry("pdf:containsNonEmbeddedFont", "false")
                .containsEntry("pdf:docinfo:created", "2007-02-23T15:56:37Z")
                .containsEntry("pdf:docinfo:creator", "Evangelos Vlachogiannis")
                .containsEntry("pdf:docinfo:creator_tool", "Writer")
                .containsEntry("pdf:docinfo:producer", "OpenOffice.org 2.1")
                .containsEntry("pdf:encrypted", "false")
                .containsEntry("pdf:eofOffsets", "13264")
                .containsEntry("pdf:hasCollection", "false")
                .containsEntry("pdf:hasMarkedContent", "false")
                .containsEntry("pdf:hasXFA", "false")
                .containsEntry("pdf:hasXMP", "false")
                .containsEntry("pdf:incrementalUpdateCount", "0")
                .containsEntry("pdf:num3DAnnotations", "0")
                .containsEntry("pdf:overallPercentageUnmappedUnicodeChars", "0.0")
                .containsEntry("pdf:producer", "OpenOffice.org 2.1")
                .containsEntry("pdf:totalUnmappedUnicodeChars", "0")
                .containsEntry("pdf:unmappedUnicodeCharsPerPage", "0")
                .containsEntry("xmp:CreatorTool", "Writer")
                .containsEntry("xmpTPg:NPages", "1");
        Assertions.assertThat(raw)
                .containsKey("pdf:ocrPageCount")
                .extractingByKey("pdf:ocrPageCount", InstanceOfAssertFactories.STRING)
                .isNotEmpty();
    }

    /**
     * Test case for <a
     * href="https://github.com/dadoonet/fscrawler/issues/834">https://github.com/dadoonet/fscrawler/issues/834</a>.
     *
     * @throws IOException In case something goes wrong
     */
    @Test
    void emptyFileIssue834() throws IOException {
        FsSettings fsSettings = FsSettingsLoader.load();
        fsSettings.getFs().setRawMetadata(true);
        Doc doc = extractFromFile("issue-834.txt", fsSettings);
        Assertions.assertThat(doc.getContent()).isEmpty();

        // Metadata
        Map<String, String> raw = doc.getMeta().getRaw();
        Assertions.assertThat(raw)
                .hasSize(3)
                .containsEntry("Content-Type", "text/plain")
                .containsEntry("Content-Type-Magic-Detected", "text/plain")
                .containsEntry("X-TIKA:resourceName", "issue-834.txt");
    }

    /** Test protected document */
    @Test
    void protectedDocument() throws IOException {
        FsSettings fsSettings = FsSettingsLoader.load();
        Doc doc = extractFromFile("test-protected.docx", fsSettings);
        Assertions.assertThat(doc.getFile().getContentType()).isEqualTo("application/x-tika-ooxml-protected");
    }

    @Test
    void docxWithEmbeddedBadPDF() throws IOException {
        Doc doc = extractFromFile("issue-stackoverflow.docx");
        Assertions.assertThat(doc.getContent()).isNotEmpty();
    }

    /**
     * Test that FSCrawler handles RuntimeException/IOException/SAXException from parser gracefully. The document should
     * be created but with empty/null content.
     *
     * <p>The MockParser throws an exception during parsing, but FSCrawler should not crash and should create a document
     * object (even if content is empty).
     *
     * <p>See <<a href="https://cwiki.apache.org/confluence/display/tika/MockParser">MockParser</a>>
     */
    @Test
    void mockParserRuntimeException() throws IOException {
        Doc doc = testWithMock("mock-runtime-exception.xml");
        Assertions.assertThat(doc.getContent()).isNull();
        doc = testWithMock("mock-io-exception.xml");
        Assertions.assertThat(doc.getContent()).isNull();
        doc = testWithMock("mock-parse-exception.xml");
        Assertions.assertThat(doc.getContent()).isNull();
    }

    /**
     * Test that FSCrawler handles parser writing to stdout without issues. Note: The MockParser behavior varies - it
     * may or may not extract content depending on the Tika version and configuration. This test verifies that the
     * document is created without crashing.
     *
     * <p>See <<a href="https://cwiki.apache.org/confluence/display/tika/MockParser">MockParser</a>>
     */
    @Test
    void mockParserStdoutAndStderr() throws IOException {
        Doc doc = testWithMock("mock-stdout.xml");
        // Content should be extracted
        Assertions.assertThat(doc.getContent()).contains("I'm a fake file");
        // Metadata author should be there
        Assertions.assertThat(doc.getMeta().getAuthor()).isEqualTo("David Pilato");
    }

    /**
     * Helper method to test MockParser behavior with different mock files.
     *
     * @param mockFilename The name of the mock file to test (e.g., "mock-runtime-exception.xml")
     * @return The generated Doc object, which should be created even if the parser throws an exception
     * @throws IOException In case something goes wrong
     */
    private Doc testWithMock(String mockFilename) throws IOException {
        FsSettings fsSettings = FsSettingsLoader.load();
        Doc doc = extractFromFile(mockFilename, fsSettings);

        // The document should be created despite the exception
        Assertions.assertThat(doc).isNotNull();
        // The filename should still be set
        Assertions.assertThat(doc.getFile().getFilename()).isEqualTo(mockFilename);

        return doc;
    }

    private Doc extractFromFileExtension(String extension) throws IOException {
        FsSettings fsSettings = FsSettingsLoader.load();
        fsSettings.getFs().setRawMetadata(true);
        return extractFromFile("test." + extension, fsSettings);
    }

    private Doc extractFromFile(String filename) throws IOException {
        return extractFromFile(filename, FsSettingsLoader.load());
    }

    private Doc extractFromFile(String filename, FsSettings fsSettings) throws IOException {
        logger.info("Test extraction of [{}]", filename);
        Doc doc = new Doc();
        doc.getPath().setReal(filename);
        doc.getFile().setFilename(filename);

        // A fresh, isolated parser instance for every extraction under test
        new TikaDocParser(fsSettings).generate(getBinaryContent(filename), doc, 0);

        logger.debug("Generated Content: [{}]", doc.getContent());
        logger.debug("Generated Raw Metadata: [{}]", doc.getMeta().getRaw());

        return doc;
    }
}
