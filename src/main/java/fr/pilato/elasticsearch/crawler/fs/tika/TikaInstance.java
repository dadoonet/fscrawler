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


import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.tika.Tika;
import org.apache.tika.config.ServiceLoader;
import org.apache.tika.language.detect.LanguageDetector;
import org.apache.tika.mime.MediaTypeRegistry;
import org.apache.tika.parser.AutoDetectParser;
import org.apache.tika.parser.DefaultParser;
import org.apache.tika.parser.Parser;
import org.apache.tika.parser.external.ExternalParser;
import org.apache.tika.parser.ocr.TesseractOCRParser;
import org.apache.tika.parser.pdf.PDFParser;

import java.io.IOException;
import java.util.Collections;

import static org.apache.tika.langdetect.OptimaizeLangDetector.getDefaultLanguageDetector;

/**
 *
 */
public class TikaInstance {

    private static final Logger logger = LogManager.getLogger(TikaInstance.class);

    private static Tika tika;
    private static LanguageDetector detector;

    /* For tests only */
    public static void reloadTika() {
        tika = null;
    }

    public static Tika tika(boolean ocr) {
        if (tika == null) {
            PDFParser pdfParser = new PDFParser();
            DefaultParser defaultParser;

            if (ocr) {
                logger.debug("OCR is activated for PDF documents");
                if (ExternalParser.check("tesseract")) {
                    pdfParser.setOcrStrategy("ocr_and_text");
                } else {
                    logger.debug("But Tesseract is not installed so we won't run OCR.");
                }
                defaultParser = new DefaultParser();
            } else {
                logger.debug("OCR is disabled. Even though it's detected, it must be disabled explicitly");
                defaultParser = new DefaultParser(
                        MediaTypeRegistry.getDefaultRegistry(),
                        new ServiceLoader(),
                        Collections.singletonList(TesseractOCRParser.class));
            }

            Parser PARSERS[] = new Parser[2];
            PARSERS[0] = defaultParser;
            PARSERS[1] = pdfParser;

            AutoDetectParser parser;
            parser = new AutoDetectParser(PARSERS);

            tika = new Tika(parser.getDetector(), parser);
        }

        return tika;
    }

    public static LanguageDetector langDetector() throws IOException {
        if (detector == null) {
             detector = getDefaultLanguageDetector().loadModels();
        }
        return detector;
    }
}
