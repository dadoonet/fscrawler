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
import fr.pilato.elasticsearch.crawler.fs.settings.FsSettings;
import fr.pilato.elasticsearch.crawler.fs.settings.FsSettingsLoader;

import java.nio.file.Files;
import java.nio.file.Path;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.tika.exception.TikaConfigException;
import org.apache.tika.parser.ocr.TesseractOCRParser;
import org.assertj.core.api.InstanceOfAssertFactories;
import org.junit.BeforeClass;
import org.junit.Test;

import java.io.IOException;
import java.io.InputStream;
import java.security.MessageDigest;
import java.time.LocalDateTime;
import java.util.Map;

import static com.carrotsearch.randomizedtesting.RandomizedTest.randomBoolean;
import static fr.pilato.elasticsearch.crawler.fs.framework.FsCrawlerUtil.localDateTimeToDate;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assumptions.assumeThat;
import static org.assertj.core.api.Assumptions.assumeThatCode;

public class TikaDocParserTest extends DocParserTestCase {
    private static final Logger logger = LogManager.getLogger();
    private static boolean isOcrAvailable;

    @BeforeClass
    public static void setOcrAvailable() {
        try {
            isOcrAvailable = new TesseractOCRParser().hasTesseract();
        } catch (TikaConfigException e) {
            logger.warn("Can not configure Tesseract for tests, so we are supposing it won't be available");
            isOcrAvailable = false;
        }
    }

    /**
     * Test case for <a href="https://github.com/dadoonet/fscrawler/issues/162">https://github.com/dadoonet/fscrawler/issues/162</a>
     */
    @Test
    public void langDetect162() throws IOException {
        FsSettings fsSettings = FsSettingsLoader.load();
        fsSettings.getFs().setLangDetect(true);
        Doc doc = extractFromFile("test.txt", fsSettings);
        assertThat(doc.getMeta().getLanguage()).isEqualTo("en");
        doc = extractFromFile("test-fr.txt", fsSettings);
        assertThat(doc.getMeta().getLanguage()).isEqualTo("fr");
        doc = extractFromFile("test-de.txt", fsSettings);
        assertThat(doc.getMeta().getLanguage()).isEqualTo("de");
        doc = extractFromFile("test-enfrde.txt", fsSettings);
        assertThat(doc.getMeta().getLanguage()).isEqualTo("fr");
    }

    /**
     * Test case for <a href="https://github.com/dadoonet/fscrawler/issues/221">https://github.com/dadoonet/fscrawler/issues/221</a>
     */
    @Test
    public void pdfIssue221() throws IOException {
        // We test document 1
        Doc doc = extractFromFile("issue-221-doc1.pdf");

        // Extracted content
        assertThat(doc.getContent()).contains("coucou");

        // Content Type
        assertThat(doc.getFile().getContentType()).contains("application/pdf");

        // Meta data
        assertThat(doc.getMeta().getAuthor()).isNotNull();
        assertThat(doc.getMeta().getDate()).isEqualTo(localDateTimeToDate(LocalDateTime.of(2016, 9, 20, 9, 38, 56)));
        assertThat(doc.getMeta().getKeywords()).isNotEmpty();
        assertThat(doc.getMeta().getTitle()).contains("Recherche");

        // We test document 2
        doc = extractFromFile("issue-221-doc2.pdf");

        // Extracted content
        assertThat(doc.getContent()).contains("FORMATIONS");

        // Content Type
        assertThat(doc.getFile().getContentType()).contains("application/pdf");

        // Meta data
        assertThat(doc.getMeta().getAuthor()).isNull();
        assertThat(doc.getMeta().getDate()).isEqualTo(localDateTimeToDate(LocalDateTime.of(2016, 9, 19, 14, 29, 37)));
        assertThat(doc.getMeta().getKeywords()).isNull();
        assertThat(doc.getMeta().getTitle()).isNull();
    }

    /**
     * Test case for <a href="https://github.com/dadoonet/fscrawler/issues/163">https://github.com/dadoonet/fscrawler/issues/163</a>
     */
    @Test
    public void xmlIssue163() throws IOException {
        FsSettings fsSettings = FsSettingsLoader.load();
        fsSettings.getFs().setRawMetadata(true);
        Doc doc = extractFromFile("issue-163.xml", fsSettings);

        // Extracted content
        assertThat(doc.getContent()).isEqualTo("   \n");

        // Content Type
        assertThat(doc.getFile().getContentType()).contains("application/xml");

        // Meta data
        assertThat(doc.getMeta().getAuthor()).isNull();
        assertThat(doc.getMeta().getDate()).isNull();
        assertThat(doc.getMeta().getKeywords()).isNull();
        assertThat(doc.getMeta().getTitle()).isNull();

        Map<String, String> raw = doc.getMeta().getRaw();
        assertThat(raw)
                .hasSize(4)
                .containsEntry("X-TIKA:Parsed-By", "org.apache.tika.parser.DefaultParser")
                .containsEntry("X-TIKA:Parsed-By-Full-Set", "org.apache.tika.parser.DefaultParser")
                .containsEntry("resourceName", "issue-163.xml")
                .containsEntry("Content-Type", "application/xml");
    }

    @Test
    public void extractFromDoc() throws IOException {
        Doc doc = extractFromFileExtension("doc");

        // Extracted content
        assertThat(doc.getContent()).contains("This is a sample text available in page");

        // Content Type
        assertThat(doc.getFile().getContentType()).isEqualTo("application/msword");

        // Meta data
        assertThat(doc.getMeta().getAuthor()).isEqualTo("David Pilato");
        assertThat(doc.getMeta().getDate()).isEqualTo(localDateTimeToDate(LocalDateTime.of(2016, 7, 7, 8, 37, 0)));
        assertThat(doc.getMeta().getKeywords()).containsExactlyInAnyOrder("keyword1", " keyword2");
        assertThat(doc.getMeta().getTitle()).isEqualTo("Test Tika title");

        Map<String, String> raw = doc.getMeta().getRaw();
        assertThat(raw)
                .hasSize(25)
                .containsEntry("cp:revision", "2")
                .containsEntry("meta:word-count", "19")
                .containsEntry("X-TIKA:Parsed-By-Full-Set", "org.apache.tika.parser.DefaultParser")
                .containsEntry("dc:creator", "David Pilato")
                .containsEntry("extended-properties:Company", "elastic")
                .containsEntry("dcterms:created", "2016-07-07T08:37:00Z")
                .containsEntry("dcterms:modified", "2016-07-07T08:37:00Z")
                .containsEntry("meta:character-count", "68")
                .containsEntry("custom:Terminé le", "2016-07-06T22:00:00Z")
                .containsEntry("dc:title", "Test Tika title")
                .containsEntry("extended-properties:TotalTime", "600000000")
                .containsEntry("extended-properties:Manager", "My Mother")
                .containsEntry("custom:N° du document", "1234")
                .containsEntry("Content-Type", "application/msword")
                .containsEntry("w:Comments", "Comments")
                .containsEntry("dc:subject", "keyword1, keyword2")
                .containsEntry("extended-properties:Application", "Microsoft Macintosh Word")
                .containsEntry("meta:last-author", "David Pilato")
                .containsEntry("xmpTPg:NPages", "2")
                .containsEntry("resourceName", "test.doc")
                .containsEntry("extended-properties:Template", "Normal.dotm")
                .containsEntry("X-TIKA:Parsed-By", "org.apache.tika.parser.DefaultParser")
                .containsEntry("meta:keyword", "keyword1, keyword2")
                .containsEntry("meta:page-count", "2")
                .containsEntry("cp:category", "test");
    }

    @Test
    public void extractFromDocx() throws IOException {
        Doc doc = extractFromFileExtension("docx");

        // Extracted content
        assertThat(doc.getContent()).contains("This is a sample text available in page");

        // Content Type
        assertThat(doc.getFile().getContentType()).isEqualTo("application/vnd.openxmlformats-officedocument.wordprocessingml.document");

        // Meta data
        assertThat(doc.getMeta().getAuthor()).isEqualTo("David Pilato");
        assertThat(doc.getMeta().getDate()).isEqualTo(localDateTimeToDate(LocalDateTime.of(2016, 7, 7, 8, 36, 0)));
        assertThat(doc.getMeta().getKeywords()).containsExactlyInAnyOrder("keyword1", " keyword2");
        assertThat(doc.getMeta().getTitle()).isEqualTo("Test Tika title");

        Map<String, String> raw = doc.getMeta().getRaw();
        assertThat(raw)
                .hasSize(31)
                .containsEntry("cp:revision", "4")
                .containsEntry("dc:description", "Comments")
                .containsEntry("extended-properties:AppVersion", "15.0000")
                .containsEntry("meta:paragraph-count", "2")
                .containsEntry("meta:word-count", "19")
                .containsEntry("X-TIKA:Parsed-By-Full-Set", "org.apache.tika.parser.DefaultParser")
                .containsEntry("dc:creator", "David Pilato")
                .containsEntry("extended-properties:Company", "elastic")
                .containsEntry("dcterms:created", "2015-12-19T23:39:00Z")
                .containsEntry("meta:line-count", "3")
                .containsEntry("dcterms:modified", "2016-07-07T08:36:00Z")
                .containsEntry("meta:character-count", "65")
                .containsEntry("custom:Terminé le", "2016-07-06T22:00:00Z")
                .containsEntry("meta:character-count-with-spaces", "82")
                .containsEntry("dc:title", "Test Tika title")
                .containsEntry("extended-properties:TotalTime", "6")
                .containsEntry("extended-properties:Manager", "My Mother")
                .containsEntry("custom:N° du document", "1234")
                .containsEntry("Content-Type", "application/vnd.openxmlformats-officedocument.wordprocessingml.document")
                .containsEntry("dc:subject", "Test Tika Object")
                .containsEntry("extended-properties:Application", "Microsoft Macintosh Word")
                .containsEntry("meta:last-author", "David Pilato")
                .containsEntry("xmpTPg:NPages", "2")
                .containsEntry("resourceName", "test.docx")
                .containsEntry("extended-properties:Template", "Normal.dotm")
                .containsEntry("X-TIKA:Parsed-By", "org.apache.tika.parser.DefaultParser")
                .containsEntry("extended-properties:DocSecurityString", "None")
                .containsEntry("meta:keyword", "keyword1, keyword2")
                .containsEntry("cp:category", "test")
                .containsEntry("meta:page-count", "2")
                .containsEntry("dc:publisher", "elastic");
    }

    @Test
    public void extractFromHtml() throws IOException {
        Doc doc = extractFromFileExtension("html");

        // Extracted content
        assertThat(doc.getContent()).contains("a sample text available in");

        // Content Type
        assertThat(doc.getFile().getContentType()).contains("text/html");

        // Meta data
        assertThat(doc.getMeta().getAuthor()).isNull();
        assertThat(doc.getMeta().getDate()).isNull();
        assertThat(doc.getMeta().getKeywords()).isNull();
        assertThat(doc.getMeta().getTitle()).isEqualTo("Test Tika title");

        Map<String, String> raw = doc.getMeta().getRaw();
        assertThat(raw)
            .hasSize(15)
            .containsEntry("Titre", "Test Tika title")
            .containsEntry("Content-Location", "Web%20page")
            .containsEntry("X-TIKA:Parsed-By-Full-Set", "org.apache.tika.parser.DefaultParser")
            .containsEntry("resourceName", "test.html")
            .containsEntry("Mots clés", "keyword1, keyword2")
            .containsEntry("ProgId", "Word.Document")
            .containsEntry("X-TIKA:encodingDetector", "UniversalEncodingDetector")
            .containsEntry("Originator", "Microsoft Word 15")
            .containsEntry("X-TIKA:Parsed-By", "org.apache.tika.parser.DefaultParser")
            .containsEntry("dc:title", "Test Tika title")
            .containsEntry("Content-Encoding", "UTF-8")
            .containsEntry("Content-Type-Hint", "text/html; charset=macintosh")
            .containsEntry("X-TIKA:detectedEncoding", "UTF-8")
            .containsEntry("Content-Type", "text/html; charset=UTF-8")
            .containsEntry("Generator", "Microsoft Word 15");
    }

    /**
     * Test for #87: <a href="https://github.com/dadoonet/fscrawler/issues/87">https://github.com/dadoonet/fscrawler/issues/87</a>
     */
    @Test
    public void extractFromMp3() throws IOException {
        Doc doc = extractFromFileExtension("mp3");

        // Extracted content
        assertThat(doc.getContent()).contains("Test Tika");

        // Content Type
        assertThat(doc.getFile().getContentType()).isEqualTo("audio/mpeg");

        // Meta data
        assertThat(doc.getMeta().getAuthor()).isEqualTo("David Pilato");
        assertThat(doc.getMeta().getDate()).isNull();
        assertThat(doc.getMeta().getKeywords()).isNull();
        assertThat(doc.getMeta().getTitle()).isEqualTo("Test Tika");

        Map<String, String> raw = doc.getMeta().getRaw();
        assertThat(raw)
            .hasSize(19)
            .containsEntry("xmpDM:genre", "Vocal")
            .containsEntry("xmpDM:album", "FS Crawler")
            .containsEntry("xmpDM:trackNumber", "1")
            .containsEntry("xmpDM:releaseDate", "2016")
            .containsEntry("xmpDM:artist", "David Pilato")
            .containsEntry("X-TIKA:Parsed-By-Full-Set", "org.apache.tika.parser.DefaultParser")
            .containsEntry("dc:creator", "David Pilato")
            .containsEntry("xmpDM:audioCompressor", "MP3")
            .containsEntry("resourceName", "test.mp3")
            .containsEntry("xmpDM:audioChannelType", "Stereo")
            .containsEntry("version", "MPEG 3 Layer III Version 1")
            .containsEntry("xmpDM:audioSampleRate", "44100")
            .containsEntry("channels", "2")
            .containsEntry("X-TIKA:Parsed-By", "org.apache.tika.parser.DefaultParser")
            .containsEntry("dc:title", "Test Tika")
            .containsEntry("xmpDM:duration", "1.0187751054763794")
            .containsEntry("Content-Type", "audio/mpeg")
            .containsEntry("samplerate", "44100");
        assertThat(raw)
                .extractingByKey("xmpDM:logComment")
                .satisfies(rawField -> assertThat(rawField).containsAnyOf("Hello but reverted"));
    }

    @Test
    public void extractFromOdt() throws IOException {
        Doc doc = extractFromFileExtension("odt");

        // Extracted content
        assertThat(doc.getContent()).contains("This is a sample text available in page");

        // Content Type
        assertThat(doc.getFile().getContentType()).isEqualTo("application/vnd.oasis.opendocument.text");

        // Meta data
        assertThat(doc.getMeta().getAuthor()).isEqualTo("David Pilato");
        assertThat(doc.getMeta().getDate()).isEqualTo(localDateTimeToDate(LocalDateTime.of(2016, 7, 7, 8, 37, 0)));
        assertThat(doc.getMeta().getKeywords()).containsExactlyInAnyOrder("keyword1", "  keyword2");
        assertThat(doc.getMeta().getTitle()).isEqualTo("Test Tika title");

        Map<String, String> raw = doc.getMeta().getRaw();
        assertThat(raw)
            .hasSize(22)
            .containsEntry("dc:description", "Comments")
            .containsEntry("meta:paragraph-count", "1")
            .containsEntry("meta:word-count", "12")
            .containsEntry("dc:subject", "keyword1,  keyword2")
            .containsEntry("X-TIKA:Parsed-By-Full-Set", "org.apache.tika.parser.DefaultParser")
            .containsEntry("dc:creator", "David Pilato")
            .containsEntry("generator", "MicrosoftOffice/15.0 MicrosoftWord")
            .containsEntry("xmpTPg:NPages", "1")
            .containsEntry("resourceName", "test.odt")
            .containsEntry("dcterms:created", "2016-07-07T08:37:00Z")
            .containsEntry("dcterms:modified", "2016-07-07T08:37:00Z")
            .containsEntry("editing-cycles", "2")
            .containsEntry("meta:character-count", "86")
            .containsEntry("custom:Terminé le", "2016-07-06T22:00:00Z")
            .containsEntry("X-TIKA:Parsed-By", "org.apache.tika.parser.DefaultParser")
            .containsEntry("dc:title", "Test Tika title")
            .containsEntry("odf:version", "1.2")
            .containsEntry("meta:keyword", "keyword1,  keyword2")
            .containsEntry("extended-properties:TotalTime", "PT0S")
            .containsEntry("cp:subject", "Test Tika Object")
            .containsEntry("meta:page-count", "1")
            .containsEntry("Content-Type", "application/vnd.oasis.opendocument.text");
    }

    @Test
    public void extractFromPdf() throws IOException {
        Doc doc = extractFromFileExtension("pdf");

        // Extracted content
        assertThat(doc.getContent()).contains("This is a sample text available in page");

        // Content Type
        assertThat(doc.getFile().getContentType()).isEqualTo("application/pdf");

        // Meta data
        assertThat(doc.getMeta().getAuthor()).isEqualTo("David Pilato");
        assertThat(doc.getMeta().getDate()).isEqualTo(localDateTimeToDate(LocalDateTime.of(2016, 7, 7, 8, 37, 42)));
        assertThat(doc.getMeta().getKeywords()).containsExactlyInAnyOrder("keyword1", " keyword2");
        assertThat(doc.getMeta().getTitle()).isEqualTo("Test Tika title");

        Map<String, String> raw = doc.getMeta().getRaw();
        assertThat(raw)
            .hasSize(42)
            .containsEntry("pdf:unmappedUnicodeCharsPerPage", "0")
            .containsEntry("pdf:PDFVersion", "1.5")
            .containsEntry("pdf:docinfo:title", "Test Tika title")
            .containsEntry("xmp:CreatorTool", "Microsoft Word")
            .containsEntry("pdf:hasXFA", "false")
            .containsEntry("access_permission:modify_annotations", "true")
            .containsEntry("X-TIKA:Parsed-By-Full-Set", "org.apache.tika.parser.pdf.PDFParser")
            .containsEntry("dc:creator", "David Pilato")
            .containsEntry("pdf:num3DAnnotations", "0")
            .containsEntry("dcterms:created", "2016-07-07T08:37:42Z")
            .containsEntry("dcterms:modified", "2016-07-07T08:37:42Z")
            .containsEntry("dc:format", "application/pdf; version=1.5")
            .containsEntry("pdf:docinfo:creator_tool", "Microsoft Word")
            .containsEntry("pdf:overallPercentageUnmappedUnicodeChars", "0.0")
            .containsEntry("access_permission:fill_in_form", "true")
            .containsEntry("pdf:docinfo:keywords", "keyword1, keyword2")
            .containsEntry("pdf:docinfo:modified", "2016-07-07T08:37:42Z")
            .containsEntry("pdf:hasCollection", "false")
            .containsEntry("pdf:encrypted", "false")
            .containsEntry("dc:title", "Test Tika title")
            .containsEntry("pdf:containsNonEmbeddedFont", "false")
            .containsEntry("pdf:docinfo:subject", "Test Tika Object")
            .containsEntry("pdf:hasMarkedContent", "true")
            .containsEntry("Content-Type", "application/pdf")
            .containsEntry("access_permission:can_print_faithful", "true")
            .containsEntry("pdf:docinfo:creator", "David Pilato")
            .containsEntry("dc:language", "en-US")
            .containsEntry("dc:subject", "keyword1, keyword2")
            .containsEntry("pdf:totalUnmappedUnicodeChars", "0")
            .containsEntry("access_permission:extract_for_accessibility", "true")
            .containsEntry("access_permission:assemble_document", "true")
            .containsEntry("xmpTPg:NPages", "2")
            .containsEntry("resourceName", "test.pdf")
            .containsEntry("pdf:hasXMP", "false")
            .containsEntry("pdf:charsPerPage", "42")
            .containsEntry("access_permission:extract_content", "true")
            .containsEntry("access_permission:can_print", "true")
            .containsEntry("X-TIKA:Parsed-By", "org.apache.tika.parser.pdf.PDFParser")
            .containsEntry("access_permission:can_modify", "true")
            .containsEntry("pdf:docinfo:created", "2016-07-07T08:37:42Z")
            .containsEntry("pdf:containsDamagedFont", "false");
        assertThat(raw).containsKey("pdf:ocrPageCount")
                .extractingByKey("pdf:ocrPageCount", InstanceOfAssertFactories.STRING)
                .isNotEmpty();
    }

    @Test
    public void extractFromJpg() throws IOException {
        FsSettings fsSettings = FsSettingsLoader.load();
        fsSettings.getFs().setRawMetadata(true);

        // We force not using OCR as we want to test only metadata extraction here
        fsSettings.getFs().getOcr().setEnabled(false);

        Doc doc = extractFromFile("test.jpg", fsSettings);

        // Extracted content
        assertThat(doc.getContent()).isNullOrEmpty();

        // Content Type
        assertThat(doc.getFile().getContentType()).isEqualTo("image/jpeg");

        // Meta data
        assertThat(doc.getMeta().getAuthor()).isNull();
        assertThat(doc.getMeta().getDate()).isNull();
        assertThat(doc.getMeta().getKeywords()).isNull();
        assertThat(doc.getMeta().getTitle()).isNull();

        Map<String, String> raw = doc.getMeta().getRaw();
        assertThat(raw)
                .hasSize(64)
                .containsEntry("ICC:Profile Connection Space", "XYZ")
                .containsEntry("Number of Tables", "4 Huffman tables")
                .containsEntry("Compression Type", "Baseline")
                .containsEntry("ICC:Profile Copyright", "Copyright Apple Inc., 2017")
                .containsKey("ICC:Apple Multi-language Profile Name")
                .containsKey("X-TIKA:Parsed-By-Full-Set")
                .containsEntry("ICC:Class", "Display Device")
                .containsKey("ICC:Green Colorant")
                .containsKey("ICC:Video Card Gamma")
                .containsEntry("Number of Components", "3")
                .containsKey("Component 2")
                .containsKey("Component 1")
                .containsEntry("Exif SubIFD:Exif Image Width", "982 pixels")
                .containsEntry("ICC:Device manufacturer", "APPL")
                .containsKey("Exif IFD0:X Resolution")
                .containsEntry("tiff:ResolutionUnit", "Inch")
                .containsEntry("ICC:Signature", "acsp")
                .containsKey("ICC:Green TRC")
                .containsKey("ICC:Media White Point")
                .containsEntry("ICC:CMM Type", "appl")
                .containsKey("Component 3")
                .containsEntry("Exif SubIFD:Components Configuration", "YCbCr")
                .containsEntry("tiff:BitsPerSample", "8")
                .containsEntry("Exif IFD0:YCbCr Positioning", "Center of pixel array")
                .containsEntry("resourceName", "test.jpg")
                .containsEntry("Exif IFD0:Orientation", "Top, left side (Horizontal / normal)")
                .containsEntry("tiff:Orientation", "1")
                .containsKey("ICC:Version")
                .containsEntry("ICC:Profile Size", "3888")
                .containsKey("X-TIKA:Parsed-By")
                .containsKey("ICC:Blue Colorant")
                .containsEntry("ICC:Tag Count", "17")
                .containsEntry("tiff:YResolution", "144.0")
                .containsEntry("Exif SubIFD:Scene Capture Type", "Standard")
                .containsKey("ICC:Red TRC")
                .containsEntry("Data Precision", "8 bits")
                .containsEntry("tiff:ImageLength", "622")
                .containsKey("ICC:Profile Date/Time")
                .containsKey("ICC:Blue Parametric TRC")
                .containsEntry("Exif SubIFD:Color Space", "sRGB")
                .containsEntry("ICC:Profile Description", "Display")
                .containsEntry("File Size", "41426 bytes")
                .containsEntry("Exif SubIFD:Exif Version", "2.21")
                .containsKey("ICC:Red Parametric TRC")
                .hasEntrySatisfying("File Name", value -> assertThat(value).startsWith("apache-tika-"))
                .containsEntry("Exif IFD0:Resolution Unit", "Inch")
                .containsEntry("ICC:Color space", "RGB")
                .containsKey("ICC:Green Parametric TRC")
                .containsEntry("Content-Type", "image/jpeg")
                .containsKey("ICC:Blue TRC")
                .containsKey("ICC:XYZ values")
                .containsKey("ICC:Native Display Information")
                .containsKey("File Modified Date")
                .containsEntry("tiff:XResolution", "144.0")
                .containsEntry("Image Height", "622 pixels")
                .containsEntry("Exif SubIFD:FlashPix Version", "1.00")
                .containsKey("ICC:Make And Model")
                .containsEntry("Exif SubIFD:Exif Image Height", "622 pixels")
                .containsEntry("Image Width", "982 pixels")
                .containsEntry("ICC:Primary Platform", "Apple Computer, Inc.")
                .containsKey("ICC:Chromatic Adaptation")
                .containsKey("ICC:Red Colorant")
                .containsEntry("tiff:ImageWidth", "982")
                .containsKey("Exif IFD0:Y Resolution");
    }

    @Test
    public void extractFromRtf() throws IOException {
        Doc doc = extractFromFileExtension("rtf");

        // Extracted content
        assertThat(doc.getContent()).contains("This is a sample text available in page");

        // Content Type
        assertThat(doc.getFile().getContentType()).isEqualTo("application/rtf");

        // Meta data
        assertThat(doc.getMeta().getAuthor()).isEqualTo("David Pilato");
        assertThat(doc.getMeta().getDate()).isNull();
        assertThat(doc.getMeta().getKeywords()).containsExactlyInAnyOrder("keyword1", " keyword2");
        assertThat(doc.getMeta().getTitle()).isEqualTo("Test Tika title");

        Map<String, String> raw = doc.getMeta().getRaw();
        assertThat(raw)
            .hasSize(15)
            .containsEntry("meta:word-count", "19")
            .containsEntry("dc:subject", "Test Tika Object")
            .containsEntry("X-TIKA:Parsed-By-Full-Set", "org.apache.tika.parser.DefaultParser")
            .containsEntry("dc:creator", "David Pilato")
            .containsEntry("extended-properties:Company", "elastic")
            .containsEntry("resourceName", "test.rtf")
            .containsEntry("meta:character-count", "68")
            .containsEntry("X-TIKA:Parsed-By", "org.apache.tika.parser.DefaultParser")
            .containsEntry("dc:title", "Test Tika title")
            .containsEntry("meta:keyword", "keyword1, keyword2")
            .containsEntry("extended-properties:Manager", "My Mother")
            .containsEntry("meta:page-count", "2")
            .containsEntry("cp:category", "test")
            .containsEntry("Content-Type", "application/rtf");
        assertThat(raw).containsKey("dcterms:created")
                .extractingByKey("dcterms:created", InstanceOfAssertFactories.STRING)
                .startsWith("2016-07-0");
    }

    @Test
    public void extractFromTxt() throws IOException {
        Doc doc = extractFromFileExtension("txt");

        // Extracted content
        assertThat(doc.getContent()).contains("This file contains some words.");

        // Content Type
        assertThat(doc.getFile().getContentType()).contains("text/plain");

        // Meta data
        assertThat(doc.getMeta().getAuthor()).isNull();
        assertThat(doc.getMeta().getDate()).isNull();
        assertThat(doc.getMeta().getKeywords()).isNull();
        assertThat(doc.getMeta().getTitle()).isNull();

        Map<String, String> raw = doc.getMeta().getRaw();
        assertThat(raw)
            .hasSize(7)
            .containsEntry("X-TIKA:Parsed-By", "org.apache.tika.parser.DefaultParser")
            .containsEntry("X-TIKA:Parsed-By-Full-Set", "org.apache.tika.parser.DefaultParser")
            .containsEntry("Content-Encoding", "ISO-8859-1")
            .containsEntry("resourceName", "test.txt")
            .containsEntry("X-TIKA:detectedEncoding", "ISO-8859-1")
            .containsEntry("X-TIKA:encodingDetector", "UniversalEncodingDetector")
            .containsEntry("Content-Type", "text/plain; charset=ISO-8859-1");

        assertThat(doc.getAttachment()).isNull();
        assertThat(doc.getFile().getChecksum()).isNull();
    }

    @Test
    public void extractFromWav() throws IOException {
        Doc doc = extractFromFileExtension("wav");

        // Extracted content
        assertThat(doc.getContent()).isEmpty();

        // Content Type
        assertThat(doc.getFile().getContentType()).isEqualTo("audio/vnd.wave");

        // Meta data
        assertThat(doc.getMeta().getAuthor()).isNull();
        assertThat(doc.getMeta().getDate()).isNull();
        assertThat(doc.getMeta().getKeywords()).isNull();
        assertThat(doc.getMeta().getTitle()).isNull();

        Map<String, String> raw = doc.getMeta().getRaw();
        assertThat(raw)
            .hasSize(10)
            .containsEntry("xmpDM:audioSampleRate", "44100")
            .containsEntry("channels", "2")
            .containsEntry("X-TIKA:Parsed-By", "org.apache.tika.parser.DefaultParser")
            .containsEntry("X-TIKA:Parsed-By-Full-Set", "org.apache.tika.parser.DefaultParser")
            .containsEntry("bits", "16")
            .containsEntry("resourceName", "test.wav")
            .containsEntry("encoding", "PCM_SIGNED")
            .containsEntry("xmpDM:audioSampleType", "16Int")
            .containsEntry("Content-Type", "audio/vnd.wave")
            .containsEntry("samplerate", "44100.0");
    }

    @Test
    public void extractFromTxtAndStoreSource() throws IOException {
        FsSettings fsSettings = FsSettingsLoader.load();
        fsSettings.getFs().setStoreSource(true);
        Doc doc = extractFromFile("test.txt", fsSettings);

        // Extracted content
        assertThat(doc.getContent()).contains("This file contains some words.");
        assertThat(doc.getAttachment()).isNotNull();
    }

    @Test
    public void extractFromTxtStoreSourceAndNoIndexContent() throws IOException {
        FsSettings fsSettings = FsSettingsLoader.load();
        fsSettings.getFs().setStoreSource(true);
        fsSettings.getFs().setIndexContent(false);
        Doc doc = extractFromFile("test.txt", fsSettings);

        // Extracted content
        assertThat(doc.getContent()).isNull();
        assertThat(doc.getAttachment()).isNotNull();
    }

    @Test
    public void extractFromTxtAndStoreSourceWithDigest() throws IOException {
        assumeThatCode(() -> MessageDigest.getInstance("MD5")).doesNotThrowAnyException();

        FsSettings fsSettings = FsSettingsLoader.load();
        fsSettings.getFs().setStoreSource(true);
        fsSettings.getFs().setChecksum("MD5");
        Doc doc = extractFromFile("test.txt", fsSettings);

        // Extracted content
        assertThat(doc.getContent()).contains("This file contains some words.");
        assertThat(doc.getAttachment()).isNotNull();
        assertThat(doc.getFile().getChecksum()).isNotNull();
    }

    @Test
    public void extractFromTxtWithDigest() throws IOException {
        assumeThatCode(() -> MessageDigest.getInstance("MD5")).doesNotThrowAnyException();

        FsSettings fsSettings = FsSettingsLoader.load();
        fsSettings.getFs().setChecksum("MD5");
        Doc doc = extractFromFile("test.txt", fsSettings);

        // Extracted content
        assertThat(doc.getContent()).contains("This file contains some words.");
        assertThat(doc.getAttachment()).isNull();
        assertThat(doc.getFile().getChecksum()).isNotNull();
    }

    @Test
    public void ocr() throws IOException {
        assumeThat(isOcrAvailable)
                .as("Tesseract is not installed so we are skipping this test")
                .isTrue();

        // Test with OCR On (default)
        Doc doc = extractFromFile("test-ocr.png");
        assertThat(doc.getContent()).contains("This file contains some words.");
        doc = extractFromFile("test-ocr.pdf");
        assertThat(doc.getContent()).contains("This file contains some words.");
        assertThat(doc.getContent()).contains("This file also contains text.");
        doc = extractFromFile("test-ocr.docx");
        assertThat(doc.getContent()).contains("This file contains some words.");
        assertThat(doc.getContent()).contains("This file also contains text.");
    }

    @Test
    public void ocrWithPdfStrategyNoOcr() throws IOException {
        assumeThat(isOcrAvailable)
                .as("Tesseract is not installed so we are skipping this test")
                .isTrue();

        // Test with OCR On and PDF Strategy set to no_ocr (meaning that PDF are not OCRed)
        FsSettings fsSettings = FsSettingsLoader.load();
        fsSettings.getFs().getOcr().setPdfStrategy("no_ocr");
        Doc doc = extractFromFile("test-ocr.png", fsSettings);
        assertThat(doc.getContent()).contains("This file contains some words.");
        doc = extractFromFile("test-ocr.pdf", fsSettings);
        assertThat(doc.getContent()).contains("This file also contains text.");
        assertThat(doc.getContent()).doesNotContain("This file contains some words.");
        doc = extractFromFile("test-ocr.docx", fsSettings);
        assertThat(doc.getContent()).contains("This file also contains text.");
        assertThat(doc.getContent()).contains("This file contains some words.");
    }

    @Test
    public void ocrWithPdfStrategyOcrOnly() throws IOException {
        assumeThat(isOcrAvailable)
                .as("Tesseract is not installed so we are skipping this test")
                .isTrue();

        // Test with OCR On and PDF Strategy set to ocr_only (meaning that PDF only OCRed and no text is extracted)
        FsSettings fsSettings = FsSettingsLoader.load();
        fsSettings.getFs().getOcr().setPdfStrategy("ocr_only");
        Doc doc = extractFromFile("test-ocr.png", fsSettings);
        assertThat(doc.getContent()).contains("This file contains some words.");
        doc = extractFromFile("test-ocr.pdf", fsSettings);
        assertThat(doc.getContent()).contains("This file contains some words.");
        // TODO: for a strange reason ocr_only also extracts text.
        // assertThat(doc.getContent(), not(containsString("This file also contains text.")));
        doc = extractFromFile("test-ocr.docx", fsSettings);
        assertThat(doc.getContent()).contains("This file contains some words.");
        assertThat(doc.getContent()).contains("This file also contains text.");
    }


    @Test
    public void ocrWithPdfStrategyAuto() throws IOException {
        assumeThat(isOcrAvailable)
                .as("Tesseract is not installed so we are skipping this test")
                .isTrue();

        // Test with OCR On and PDF Strategy set to auto (meaning that PDF will be only OCRed if less than 10 characters are found)
        FsSettings fsSettings = FsSettingsLoader.load();
        fsSettings.getFs().getOcr().setPdfStrategy("auto");
        Doc doc = extractFromFile("test-ocr.pdf", fsSettings);
        assertThat(doc.getContent()).doesNotContain("This file contains some words.");
        assertThat(doc.getContent()).contains("This file also contains text.");
        doc = extractFromFile("test-ocr-notext.pdf", fsSettings);
        assertThat(doc.getContent()).contains("This file contains some words.");
    }


    @Test
    public void ocrOff() throws IOException {
        assumeThat(isOcrAvailable)
                .as("Tesseract is not installed so we are skipping this test")
                .isTrue();

        // Test with OCR Off
        FsSettings fsSettings = FsSettingsLoader.load();
        fsSettings.getFs().getOcr().setEnabled(false);
        Doc doc = extractFromFile("test-ocr.png", fsSettings);
        assertThat(doc.getContent()).isEmpty();
        doc = extractFromFile("test-ocr.pdf", fsSettings);
        assertThat(doc.getContent()).doesNotContain("This file contains some words.");
        doc = extractFromFile("test-ocr.docx", fsSettings);
        assertThat(doc.getContent()).doesNotContain("This file contains some words.");
    }


    @Test
    public void ocrWrongPaths() throws IOException {
        assumeThat(isOcrAvailable)
                .as("Tesseract is not installed so we are skipping this test")
                .isTrue();

        // Test with OCR On (default) but a wrong path to tesseract
        FsSettings fsSettings = FsSettingsLoader.load();
        fsSettings.getFs().getOcr().setPath("/path/to/doesnotexist");
        fsSettings.getFs().getOcr().setDataPath("/path/to/doesnotexist");
        Doc doc = extractFromFile("test-ocr.png", fsSettings);
        assertThat(doc.getContent()).isEmpty();
        doc = extractFromFile("test-ocr.pdf", fsSettings);
        assertThat(doc.getContent()).doesNotContain("This file contains some words.");
    }

    @Test
    public void ocrOutputTypeHocr() throws IOException {
        assumeThat(isOcrAvailable)
                .as("Tesseract is not installed so we are skipping this test")
                .isTrue();

        // Test with OCR On with hocr output type
        FsSettings fsSettings = FsSettingsLoader.load();
        fsSettings.getFs().getOcr().setOutputType("hocr");
        Doc doc = extractFromFile("test-ocr.png", fsSettings);
        assertThat(doc.getContent()).contains("This", "file", "contains", "some", "words.");
        doc = extractFromFile("test-ocr.pdf", fsSettings);
        assertThat(doc.getContent()).contains("This", "file", "contains", "some", "words.");
    }

    @Test
    public void ocrLanguageHeb() throws IOException {
        assumeThat(isOcrAvailable)
                .as("Tesseract is not installed so we are skipping this test")
                .isTrue();

        // Test with heb language
        FsSettings fsSettings = FsSettingsLoader.load();
        fsSettings.getFs().getOcr().setLanguage("heb");
        Doc doc = extractFromFile("test-ocr-heb.pdf", fsSettings);
        try {
            // This test requires to have the hebrew language pack, so we don't fail the test but just log
            assertThat(doc.getContent()).contains("המבודדים מתקבלים");
        } catch (AssertionError e) {
            logger.info("We were not able to get the Hebrew content with OCR. May be the language pack was not installed?");
        }
    }

    @Test
    public void customTikaConfig() throws IOException {
        InputStream tikaConfigIS = getClass().getResourceAsStream("/config/tikaConfig.xml");
        Path testTikaConfig = rootTmpDir.resolve("tika-config");
        if (Files.notExists(testTikaConfig)) {
            Files.createDirectory(testTikaConfig);
        }
        Files.copy(tikaConfigIS, testTikaConfig.resolve("tikaConfig.xml"));

        FsSettings fsSettings = FsSettingsLoader.load();
        fsSettings.getFs().setTikaConfigPath(testTikaConfig.resolve("tikaConfig.xml").toString());

        // Test that default parser for HTML is HTML parser
        Doc doc = extractFromFile("test.html");
        assertThat(doc.getContent()).doesNotContain("Test Tika title");
        assertThat(doc.getContent()).contains("This second part of the text is in Page 2");

        // Test HTML parser is never used, TXT parser used instead
        doc = extractFromFile("test.html", fsSettings);
        assertThat(doc.getContent()).contains("<title>Test Tika title</title>");

        // Test that default parser for XHTML is HTML parser
        doc = extractFromFile("test.xhtml");
        assertThat(doc.getContent()).doesNotContain("Test Tika title");
        assertThat(doc.getContent()).contains("This is an example of XHTML");

        // Test XML parser is used to parse XHTML
        doc = extractFromFile("test.xhtml", fsSettings);
        assertThat(doc.getContent()).contains("Test Tika title");
        assertThat(doc.getContent()).doesNotContain("<title>Test Tika title</title>");
    }

    @Test
    public void shiftJisEncoding() throws IOException {
        Doc doc = extractFromFile("issue-400-shiftjis.txt");
        assertThat(doc.getContent()).isNotEmpty();
    }

    /**
     * Test case for <a href="https://github.com/dadoonet/fscrawler/issues/1097">https://github.com/dadoonet/fscrawler/issues/1097</a>.
     * Related to <a href="https://issues.apache.org/jira/browse/TIKA-3364">https://issues.apache.org/jira/browse/TIKA-3364</a>.
     * @throws IOException In case something goes wrong
     */
    @Test
    public void pdfIssue1097() throws IOException {
        // Run the test with or without OCR as the behavior changes
        boolean withOcr = isOcrAvailable && randomBoolean();
        FsSettings fsSettings = FsSettingsLoader.load();
        fsSettings.getFs().setRawMetadata(true);
        fsSettings.getFs().getOcr().setEnabled(withOcr);
        Doc doc = extractFromFile("issue-1097.pdf", fsSettings);
        // TODO This test is now passing but should be failing with ocr when
        // https://issues.apache.org/jira/browse/TIKA-3364 is solved
        assertThat(doc.getContent()).isEqualTo(withOcr ?
                "\nDummy PDF file\n\nDummy PDF file\n\n\n\n" :
                "\nDummy PDF file\n\n\n");

        // Meta data
        assertThat(doc.getMeta().getAuthor()).isNotNull();
        assertThat(doc.getMeta().getDate()).isNull();
        assertThat(doc.getMeta().getKeywords()).isNull();
        assertThat(doc.getMeta().getTitle()).isNull();

        Map<String, String> raw = doc.getMeta().getRaw();
        assertThat(raw)
            .hasSize(36)
            .containsEntry("pdf:unmappedUnicodeCharsPerPage", "0")
            .containsEntry("pdf:PDFVersion", "1.4")
            .containsEntry("xmp:CreatorTool", "Writer")
            .containsEntry("pdf:hasXFA", "false")
            .containsEntry("access_permission:modify_annotations", "true")
            .containsEntry("X-TIKA:Parsed-By-Full-Set", "org.apache.tika.parser.pdf.PDFParser")
            .containsEntry("dc:creator", "Evangelos Vlachogiannis")
            .containsEntry("pdf:num3DAnnotations", "0")
            .containsEntry("dcterms:created", "2007-02-23T15:56:37Z")
            .containsEntry("dc:format", "application/pdf; version=1.4")
            .containsEntry("pdf:docinfo:creator_tool", "Writer")
            .containsEntry("pdf:overallPercentageUnmappedUnicodeChars", "0.0")
            .containsEntry("access_permission:fill_in_form", "true")
            .containsEntry("pdf:hasCollection", "false")
            .containsEntry("pdf:encrypted", "false")
            .containsEntry("pdf:containsNonEmbeddedFont", "false")
            .containsEntry("pdf:hasMarkedContent", "false")
            .containsEntry("Content-Type", "application/pdf")
            .containsEntry("access_permission:can_print_faithful", "true")
            .containsEntry("pdf:docinfo:creator", "Evangelos Vlachogiannis")
            .containsEntry("pdf:producer", "OpenOffice.org 2.1")
            .containsEntry("pdf:totalUnmappedUnicodeChars", "0")
            .containsEntry("access_permission:extract_for_accessibility", "true")
            .containsEntry("access_permission:assemble_document", "true")
            .containsEntry("xmpTPg:NPages", "1")
            .containsEntry("resourceName", "issue-1097.pdf")
            .containsEntry("pdf:hasXMP", "false")
            .containsEntry("pdf:charsPerPage", "14")
            .containsEntry("access_permission:extract_content", "true")
            .containsEntry("access_permission:can_print", "true")
            .containsEntry("X-TIKA:Parsed-By", "org.apache.tika.parser.pdf.PDFParser")
            .containsEntry("access_permission:can_modify", "true")
            .containsEntry("pdf:docinfo:producer", "OpenOffice.org 2.1")
            .containsEntry("pdf:docinfo:created", "2007-02-23T15:56:37Z")
            .containsEntry("pdf:containsDamagedFont", "false");
        assertThat(raw).containsKey("pdf:ocrPageCount")
                .extractingByKey("pdf:ocrPageCount", InstanceOfAssertFactories.STRING)
                .isNotEmpty();
    }

    /**
     * Test case for <a href="https://github.com/dadoonet/fscrawler/issues/834">https://github.com/dadoonet/fscrawler/issues/834</a>.
     * @throws IOException In case something goes wrong
     */
    @Test
    public void emptyFileIssue834() throws IOException {
        FsSettings fsSettings = FsSettingsLoader.load();
        fsSettings.getFs().setRawMetadata(true);
        Doc doc = extractFromFile("issue-834.txt", fsSettings);
        assertThat(doc.getContent()).isEmpty();

        // Meta data
        Map<String, String> raw = doc.getMeta().getRaw();
        assertThat(raw)
            .hasSize(2)
        .containsEntry("Content-Type", "text/plain")
        .containsEntry("resourceName", "issue-834.txt");
    }

    /**
     * Test protected document
     */
    @Test
    public void protectedDocument() throws IOException {
        FsSettings fsSettings = FsSettingsLoader.load();
        Doc doc = extractFromFile("test-protected.docx", fsSettings);
        assertThat(doc.getFile().getContentType()).isEqualTo("application/x-tika-ooxml-protected");
    }

    @Test
    public void docxWithEmbeddedBadPDF() throws IOException {
        Doc doc = extractFromFile("issue-stackoverflow.docx");
        assertThat(doc.getContent()).isNotEmpty();
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

        // We make sure we reload a new Tika instance any time we test
        TikaInstance.reloadTika();
        TikaDocParser.generate(fsSettings, getBinaryContent(filename), doc, 0);

        logger.debug("Generated Content: [{}]", doc.getContent());
        logger.debug("Generated Raw Metadata: [{}]", doc.getMeta().getRaw());

        return doc;
    }
}
