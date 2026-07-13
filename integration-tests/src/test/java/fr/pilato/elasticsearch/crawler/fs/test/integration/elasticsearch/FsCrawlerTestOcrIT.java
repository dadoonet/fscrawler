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
import fr.pilato.elasticsearch.crawler.fs.test.framework.Slow;
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
@Slow
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

        // Default behaviour
        {
            crawler = startCrawler();

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
                .hasSize(64)
                .containsKey("Component 1")
                .containsKey("Component 2")
                .containsKey("Component 3")
                .containsEntry("Compression Type", "Baseline")
                .containsEntry("Content-Type", "image/jpeg")
                .containsEntry("Data Precision", "8 bits")
                .containsEntry("Exif IFD0:Orientation", "Top, left side (Horizontal / normal)")
                .containsEntry("Exif IFD0:Resolution Unit", "Inch")
                .containsKey("Exif IFD0:X Resolution")
                .containsKey("Exif IFD0:Y Resolution")
                .containsEntry("Exif IFD0:YCbCr Positioning", "Center of pixel array")
                .containsEntry("Exif SubIFD:Color Space", "sRGB")
                .containsEntry("Exif SubIFD:Components Configuration", "YCbCr")
                .containsEntry("Exif SubIFD:Exif Image Height", "622 pixels")
                .containsEntry("Exif SubIFD:Exif Image Width", "982 pixels")
                .containsEntry("Exif SubIFD:Exif Version", "2.21")
                .containsEntry("Exif SubIFD:FlashPix Version", "1.00")
                .containsEntry("Exif SubIFD:Scene Capture Type", "Standard")
                .containsKey("File Modified Date")
                .hasEntrySatisfying(
                        "File Name", value -> Assertions.assertThat(value).startsWith("apache-tika-"))
                .containsEntry("File Size", "41426 bytes")
                .containsKey("ICC:Apple Multi-language Profile Name")
                .containsKey("ICC:Blue Colorant")
                .containsKey("ICC:Blue Parametric TRC")
                .containsKey("ICC:Blue TRC")
                .containsKey("ICC:Chromatic Adaptation")
                .containsEntry("ICC:Class", "Display Device")
                .containsEntry("ICC:CMM Type", "appl")
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
                .containsEntry("Image Height", "622 pixels")
                .containsEntry("Image Width", "982 pixels")
                .containsEntry("Number of Components", "3")
                .containsEntry("Number of Tables", "4 Huffman tables")
                .containsEntry("resourceName", "test-ocr.jpg")
                .containsEntry("tiff:BitsPerSample", "8")
                .containsEntry("tiff:ImageLength", "622")
                .containsEntry("tiff:ImageWidth", "982")
                .containsEntry("tiff:Orientation", "1")
                .containsEntry("tiff:ResolutionUnit", "Inch")
                .containsEntry("tiff:XResolution", "144.0")
                .containsEntry("tiff:YResolution", "144.0")
                .containsKey("X-TIKA:Parsed-By")
                .containsKey("X-TIKA:Parsed-By-Full-Set");

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
