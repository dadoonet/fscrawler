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
package fr.pilato.elasticsearch.crawler.fs.test.integration.elasticsearch;

import com.jayway.jsonpath.JsonPath;
import com.jayway.jsonpath.PathNotFoundException;
import fr.pilato.elasticsearch.crawler.fs.client.ESSearchRequest;
import fr.pilato.elasticsearch.crawler.fs.client.ESSearchResponse;
import fr.pilato.elasticsearch.crawler.fs.framework.FsCrawlerUtil;
import fr.pilato.elasticsearch.crawler.fs.settings.FsSettings;
import fr.pilato.elasticsearch.crawler.fs.test.integration.AbstractFsCrawlerITCase;
import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.assertj.core.api.Assertions;
import org.assertj.core.api.Assumptions;
import org.junit.jupiter.api.Test;

/** Tests with OCR configuration See <a href="https://github.com/dadoonet/fscrawler/issues/1988">#1988</a> */
class FsCrawlerTestOcrIT extends AbstractFsCrawlerITCase {
    private static final Logger logger = LogManager.getLogger();

    @Test
    void ocr() throws Exception {
        String exec = "tesseract";
        Optional<Path> tessPath = Stream.of(System.getenv("PATH").split(Pattern.quote(File.pathSeparator)))
                .map(Paths::get)
                .filter(path -> Files.exists(path.resolve(exec)))
                .findFirst();
        Assumptions.assumeThat(tessPath.isPresent())
                .as("tesseract executable [%s] should be present in PATH [%s]", exec, System.getenv("PATH"))
                .isTrue();
        Path tessDirPath = tessPath.get();
        Path tesseract = tessDirPath.resolve(exec);
        logger.info("Tesseract is installed at [{}]", tesseract);

        // Default behaviour (pdf_strategy=auto)
        {
            crawler = startCrawler();

            ESSearchResponse searchResponse = countTestHelper(
                    new ESSearchRequest().withIndex(getCrawlerName() + FsCrawlerUtil.INDEX_SUFFIX_DOCS), 3L, null);

            Assertions.assertThat(searchResponse.getHits()).as("total hits").hasSize(3);
            Assertions.assertThat((String) JsonPath.read(
                            findHitByFilename(searchResponse, "test-ocr.jpg").getSource(), "$.content"))
                    .as("test-ocr.jpg: image OCR")
                    .contains("words");
            Assertions.assertThat((String) JsonPath.read(
                            findHitByFilename(searchResponse, "test-ocr.png").getSource(), "$.content"))
                    .as("test-ocr.png: image OCR")
                    .contains("words");
            Assertions.assertThat((String) JsonPath.read(
                            findHitByFilename(searchResponse, "test-ocr.pdf").getSource(), "$.content"))
                    .as("test-ocr.pdf: auto skips OCR when the text layer has more than 10 characters")
                    .contains("This file also contains text.")
                    .doesNotContain("This file contains some words.");

            crawler.close();
            crawler = null;
        }

        {
            FsSettings fsSettings = createTestSettings();
            fsSettings.getFs().getOcr().setEnabled(true);
            // We try to set the path to tesseract executable
            fsSettings.getFs().getOcr().setPath(tesseract.toString());
            fsSettings.getFs().getOcr().setPdfStrategy("ocr_and_text");
            fsSettings.getFs().getOcr().setLanguage("vie+eng");
            fsSettings.getFs().getOcr().setOutputType("txt");

            crawler = startCrawler(fsSettings);

            // We expect to have one file
            ESSearchResponse searchResponse = countTestHelper(
                    new ESSearchRequest().withIndex(getCrawlerName() + FsCrawlerUtil.INDEX_SUFFIX_DOCS), 3L, null);

            // Check that we extracted the content
            Assertions.assertThat(searchResponse.getHits())
                    .isNotEmpty()
                    .allSatisfy(hit -> Assertions.assertThat((String) JsonPath.read(hit.getSource(), "$.content"))
                            .contains("words"));

            crawler.close();
            crawler = null;
        }

        {
            FsSettings fsSettings = createTestSettings();
            fsSettings.getFs().getOcr().setEnabled(true);
            // We try to set the path to the dir where tesseract is installed
            fsSettings.getFs().getOcr().setPath(tessDirPath.toString());
            fsSettings.getFs().getOcr().setPdfStrategy("ocr_and_text");
            fsSettings.getFs().getOcr().setLanguage("vie+eng");
            fsSettings.getFs().getOcr().setOutputType("txt");

            crawler = startCrawler(fsSettings);

            // We expect to have one file
            ESSearchResponse searchResponse = countTestHelper(
                    new ESSearchRequest().withIndex(getCrawlerName() + FsCrawlerUtil.INDEX_SUFFIX_DOCS), 3L, null);

            // Check that we extracted the content
            Assertions.assertThat(searchResponse.getHits())
                    .isNotEmpty()
                    .allSatisfy(hit -> Assertions.assertThat((String) JsonPath.read(hit.getSource(), "$.content"))
                            .contains("words"));
        }
    }

    @Test
    void ocr_disabled() throws Exception {
        FsSettings fsSettings = createTestSettings();
        fsSettings.getFs().setRawMetadata(true);
        fsSettings.getFs().getOcr().setEnabled(false);
        crawler = startCrawler(fsSettings);

        // We expect to have one file
        ESSearchResponse searchResponse = countTestHelper(
                new ESSearchRequest().withIndex(getCrawlerName() + FsCrawlerUtil.INDEX_SUFFIX_DOCS), 3L, null);

        // Check that we extracted the content
        Assertions.assertThat(searchResponse.getHits()).as("total hits").hasSize(3);

        // test-ocr.jpg: OCR disabled → no content, raw metadata present
        String jpgHitSource = findHitByFilename(searchResponse, "test-ocr.jpg").getSource();
        Assertions.assertThatThrownBy(() -> JsonPath.read(jpgHitSource, "$.content"))
                .as("test-ocr.jpg: content should be absent when OCR is disabled")
                .isInstanceOf(PathNotFoundException.class);
        Map<String, String> raw = JsonPath.read(jpgHitSource, "$.meta.raw");
        Assertions.assertThat(raw)
                .as("test-ocr.jpg: raw metadata")
                .hasSize(66)
                .containsEntry("Content-Type", "image/jpeg")
                .containsEntry("tk:content-type-magic-detected", "image/jpeg")
                .containsKey("icc:Apple Multi-language Profile Name")
                .containsKey("icc:Blue Colorant")
                .containsKey("icc:Blue Parametric TRC")
                .containsKey("icc:Blue TRC")
                .containsKey("icc:Chromatic Adaptation")
                .containsEntry("icc:Class", "Display Device")
                .containsEntry("icc:CMM Type", "appl")
                .containsEntry("icc:Color space", "RGB")
                .containsEntry("icc:Device manufacturer", "APPL")
                .containsKey("icc:Green Colorant")
                .containsKey("icc:Green Parametric TRC")
                .containsKey("icc:Green TRC")
                .containsKey("icc:Make And Model")
                .containsKey("icc:Media White Point")
                .containsKey("icc:Native Display Information")
                .containsEntry("icc:Primary Platform", "Apple Computer, Inc.")
                .containsEntry("icc:Profile Connection Space", "XYZ")
                .containsEntry("icc:Profile Copyright", "Copyright Apple Inc., 2017")
                .containsKey("icc:Profile Date/Time")
                .containsEntry("icc:Profile Description", "Display")
                .containsEntry("icc:Profile Size", "3888")
                .containsKey("icc:Red Colorant")
                .containsKey("icc:Red Parametric TRC")
                .containsKey("icc:Red TRC")
                .containsEntry("icc:Signature", "acsp")
                .containsEntry("icc:Tag Count", "17")
                .containsKey("icc:Version")
                .containsKey("icc:Video Card Gamma")
                .containsKey("icc:XYZ values")
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
                .containsEntry("tiff:YResolution", "144.0")
                .containsKey("tk:parsed-by")
                .containsKey("tk:parsed-by-full-set")
                .containsEntry("tk:resource-name", "test-ocr.jpg");

        // test-ocr.png: OCR disabled → no content
        String pngHitSource = findHitByFilename(searchResponse, "test-ocr.png").getSource();
        Assertions.assertThatThrownBy(() -> JsonPath.read(pngHitSource, "$.content"))
                .as("test-ocr.png: content should be absent when OCR is disabled")
                .isInstanceOf(PathNotFoundException.class);

        // test-ocr.pdf: text layer always present regardless of OCR setting
        String pdfHitSource = findHitByFilename(searchResponse, "test-ocr.pdf").getSource();
        Assertions.assertThat((String) JsonPath.read(pdfHitSource, "$.content"))
                .as("test-ocr.pdf: text layer content")
                .contains("This file also contains text.")
                .doesNotContain("words");
    }
}
