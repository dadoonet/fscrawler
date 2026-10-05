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

import fr.pilato.elasticsearch.crawler.fs.test.framework.AbstractFSCrawlerTestCase;
import org.apache.tika.parser.pages.TextPolicy;
import org.apache.tika.parser.pdf.PDFParser;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for {@link TikaInstance#mapPdfOcrStrategy(String)} and {@link TikaInstance#setPdfOcrStrategy(PDFParser,
 * String)}. These run without Tesseract installed, unlike the behavioural OCR tests in {@link TikaDocParserTest}.
 */
class TikaInstanceTest extends AbstractFSCrawlerTestCase {

    @Test
    void mapPdfOcrStrategyDefaultsToAutoWhenNull() {
        Assertions.assertThat(TikaInstance.mapPdfOcrStrategy(null)).isEqualTo(TextPolicy.AUTO);
    }

    @Test
    void mapPdfOcrStrategyNoOcrExtractsTextOnly() {
        Assertions.assertThat(TikaInstance.mapPdfOcrStrategy("no_ocr")).isEqualTo(TextPolicy.EXTRACT);
    }

    @Test
    void mapPdfOcrStrategyAuto() {
        Assertions.assertThat(TikaInstance.mapPdfOcrStrategy("auto")).isEqualTo(TextPolicy.AUTO);
    }

    @Test
    void mapPdfOcrStrategyOcrOnly() {
        Assertions.assertThat(TikaInstance.mapPdfOcrStrategy("ocr_only")).isEqualTo(TextPolicy.OCR);
    }

    @Test
    void mapPdfOcrStrategyOcrAndText() {
        Assertions.assertThat(TikaInstance.mapPdfOcrStrategy("ocr_and_text")).isEqualTo(TextPolicy.EXTRACT_AND_OCR);
        Assertions.assertThat(TikaInstance.mapPdfOcrStrategy("ocr_and_text_extraction"))
                .isEqualTo(TextPolicy.EXTRACT_AND_OCR);
    }

    @Test
    void mapPdfOcrStrategyUnknownValueFallsBackToAuto() {
        Assertions.assertThat(TikaInstance.mapPdfOcrStrategy("not-a-strategy")).isEqualTo(TextPolicy.AUTO);
    }

    @Test
    void setPdfOcrStrategyAppliesTextPolicyToPdfParserConfig() {
        PDFParser pdfParser = new PDFParser();
        TikaInstance.setPdfOcrStrategy(pdfParser, "ocr_only");
        Assertions.assertThat(pdfParser.getPDFParserConfig().getPages().getText())
                .isEqualTo(TextPolicy.OCR);
    }
}
