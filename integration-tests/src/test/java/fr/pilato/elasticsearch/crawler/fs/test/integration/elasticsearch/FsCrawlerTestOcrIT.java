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

package fr.pilato.elasticsearch.crawler.fs.test.integration.elasticsearch;

import com.jayway.jsonpath.JsonPath;
import com.jayway.jsonpath.PathNotFoundException;
import fr.pilato.elasticsearch.crawler.fs.client.ESSearchRequest;
import fr.pilato.elasticsearch.crawler.fs.client.ESSearchResponse;
import fr.pilato.elasticsearch.crawler.fs.settings.FsSettings;
import fr.pilato.elasticsearch.crawler.fs.test.integration.AbstractFsCrawlerITCase;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.junit.Test;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static fr.pilato.elasticsearch.crawler.fs.framework.FsCrawlerUtil.INDEX_SUFFIX_DOCS;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assumptions.assumeThat;

/**
 * Tests with OCR configuration
 * See <a href="https://github.com/dadoonet/fscrawler/issues/1988">#1988</a>
 */
public class FsCrawlerTestOcrIT extends AbstractFsCrawlerITCase {
    private static final Logger logger = LogManager.getLogger();

    @Test
    public void ocr() throws Exception {
        String exec = "tesseract";
        Optional<Path> tessPath = Stream.of(System.getenv("PATH").split(Pattern.quote(File.pathSeparator)))
                .map(Paths::get)
                .filter(path -> Files.exists(path.resolve(exec)))
                .findFirst();
        assumeThat(tessPath.isPresent())
                .as("tesseract executable [%s] should be present in PATH [%s]", exec, System.getenv("PATH"))
                .isTrue();
        Path tessDirPath = tessPath.get();
        Path tesseract = tessDirPath.resolve(exec);
        logger.info("Tesseract is installed at [{}]", tesseract);

        // Default behaviour
        {
            crawler = startCrawler();

            // We expect to have one file
            ESSearchResponse searchResponse = countTestHelper(new ESSearchRequest().withIndex(getCrawlerName() + INDEX_SUFFIX_DOCS), 3L, null);

            // Check that we extracted the content
            assertThat(searchResponse.getHits())
                    .isNotEmpty()
                    .allSatisfy(hit ->
                            assertThat((String) JsonPath.read(hit.getSource(), "$.content"))
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
            ESSearchResponse searchResponse = countTestHelper(new ESSearchRequest().withIndex(getCrawlerName() + INDEX_SUFFIX_DOCS), 3L, null);

            // Check that we extracted the content
            assertThat(searchResponse.getHits())
                    .isNotEmpty()
                    .allSatisfy(hit ->
                            assertThat((String) JsonPath.read(hit.getSource(), "$.content"))
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
            ESSearchResponse searchResponse = countTestHelper(new ESSearchRequest().withIndex(getCrawlerName() + INDEX_SUFFIX_DOCS), 3L, null);

            // Check that we extracted the content
            assertThat(searchResponse.getHits())
                    .isNotEmpty()
                    .allSatisfy(hit ->
                            assertThat((String) JsonPath.read(hit.getSource(), "$.content"))
                                    .contains("words"));
        }
    }

    @Test
    public void ocr_disabled() throws Exception {
        FsSettings fsSettings = createTestSettings();
        fsSettings.getFs().setRawMetadata(true);
        fsSettings.getFs().getOcr().setEnabled(false);
        crawler = startCrawler(fsSettings);

        // We expect to have one file
        ESSearchResponse searchResponse = countTestHelper(new ESSearchRequest().withIndex(getCrawlerName() + INDEX_SUFFIX_DOCS), 3L, null);

        // Check that we extracted the content
        assertThat(searchResponse.getHits())
                .isNotEmpty()
                .satisfiesExactlyInAnyOrder(hit -> {
                    assertThat((String) JsonPath.read(hit.getSource(), "$.file.filename")).isEqualTo("test-ocr.jpg");
                    assertThatThrownBy(() -> JsonPath.read(hit.getSource(), "$.content")).isInstanceOf(PathNotFoundException.class);
                    Map<String, String> raw = JsonPath.read(hit.getSource(), "$.meta.raw");
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
                            .containsEntry("Exif IFD0:X Resolution", "144 dots per inch")
                            .containsEntry("tiff:ResolutionUnit", "Inch")
                            .containsEntry("ICC:Signature", "acsp")
                            .containsKey("ICC:Green TRC")
                            .containsKey("ICC:Media White Point")
                            .containsEntry("ICC:CMM Type", "appl")
                            .containsKey("Component 3")
                            .containsEntry("Exif SubIFD:Components Configuration", "YCbCr")
                            .containsEntry("tiff:BitsPerSample", "8")
                            .containsEntry("Exif IFD0:YCbCr Positioning", "Center of pixel array")
                            .containsEntry("resourceName", "test-ocr.jpg")
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
                            .containsEntry("Exif IFD0:Y Resolution", "144 dots per inch");
                }, hit -> {
                    assertThat((String) JsonPath.read(hit.getSource(), "$.file.filename")).isEqualTo("test-ocr.png");
                    assertThatThrownBy(() -> JsonPath.read(hit.getSource(), "$.content")).isInstanceOf(PathNotFoundException.class);
                }, hit -> {
                    assertThat((String) JsonPath.read(hit.getSource(), "$.file.filename")).isEqualTo("test-ocr.pdf");
                    assertThat((String) JsonPath.read(hit.getSource(), "$.content"))
                            .contains("This file also contains text.")
                            .doesNotContain("words");
                });
    }
}
