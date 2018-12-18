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


import fr.pilato.elasticsearch.crawler.fs.framework.FsCrawlerUtil;
import fr.pilato.elasticsearch.crawler.fs.settings.Fs;
import fr.pilato.elasticsearch.crawler.fs.settings.FsSettings;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.tika.config.ServiceLoader;
import org.apache.tika.exception.TikaException;
import org.apache.tika.language.detect.LanguageDetector;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.mime.MediaTypeRegistry;
import org.apache.tika.parser.AutoDetectParser;
import org.apache.tika.parser.DefaultParser;
import org.apache.tika.parser.ParseContext;
import org.apache.tika.parser.Parser;
import org.apache.tika.parser.external.ExternalParser;
import org.apache.tika.parser.ocr.TesseractOCRConfig;
import org.apache.tika.parser.ocr.TesseractOCRParser;
import org.apache.tika.parser.pdf.PDFParser;
import org.apache.tika.sax.BodyContentHandler;
import org.apache.tika.sax.WriteOutContentHandler;
import org.xml.sax.SAXException;

import java.io.IOException;
import java.io.InputStream;
import java.util.Collections;

import static org.apache.tika.langdetect.OptimaizeLangDetector.getDefaultLanguageDetector;

/**
 *
 */
public class TikaInstance {

    private static final Logger logger = LogManager.getLogger(TikaInstance.class);

    private static Parser parserWithOcr;
    private static ParseContext contextWithOcr;
    private static Parser parserWithoutOcr;
    private static ParseContext contextWithoutOcr;
    private static LanguageDetector detector;

    /* For tests only */
    static void reloadTika() {
        parserWithOcr = null;
        contextWithOcr = null;
        parserWithoutOcr = null;
        contextWithoutOcr = null;
    }

    /**
     * This initialize if needed a parser and a parse context for tika
     * and another one which will never run OCR
     * @param fs fs settings
     */
    private static void initTika(Fs fs) {
        if (fs.isPdfOcr() && parserWithOcr == null) {
            parserWithOcr = initParser(fs);
            contextWithOcr = initContextWithOcr(fs, parserWithOcr);
        }
        if (parserWithoutOcr == null) {
            parserWithoutOcr = initParserWithoutOcr();
            contextWithoutOcr = initContextWithoutOcr(parserWithoutOcr);
        }
    }

    private static Parser initParser(Fs fs) {
        if (fs.isPdfOcr()) {
            Parser[] PARSERS = new Parser[2];
            PARSERS[0] = new DefaultParser();
            PDFParser pdfParser = new PDFParser();
            logger.debug("OCR is activated for PDF documents");
            if (ExternalParser.check("tesseract")) {
                pdfParser.setOcrStrategy("ocr_and_text");
            } else {
                logger.debug("But Tesseract is not installed so we won't run OCR.");
            }
            PARSERS[1] = pdfParser;

            return new AutoDetectParser(PARSERS);
        }

        logger.debug("OCR is disabled. Even though it's detected, it must be disabled explicitly");
        return initParserWithoutOcr();
    }

    private static Parser initParserWithoutOcr() {
        PDFParser pdfParser = new PDFParser();
        DefaultParser defaultParser;

        logger.debug("Starting a text only parser.");
        defaultParser = new DefaultParser(
                MediaTypeRegistry.getDefaultRegistry(),
                new ServiceLoader(),
                Collections.singletonList(TesseractOCRParser.class));

        Parser[] PARSERS = new Parser[2];
        PARSERS[0] = defaultParser;
        PARSERS[1] = pdfParser;

        return new AutoDetectParser(PARSERS);
    }

    private static ParseContext initContextWithOcr(Fs fs, Parser parser) {
        ParseContext context = initContextWithoutOcr(parser);
        if (fs.isPdfOcr()) {
            logger.debug("OCR is activated");
            TesseractOCRConfig config = new TesseractOCRConfig();
            if (fs.getOcr().getPath() != null) {
                config.setTesseractPath(fs.getOcr().getPath());
            }
            if (fs.getOcr().getDataPath() != null) {
                config.setTessdataPath(fs.getOcr().getDataPath());
            }
            config.setLanguage(fs.getOcr().getLanguage());
            if (fs.getOcr().getOutputType() != null) {
                config.setOutputType(fs.getOcr().getOutputType());
            }
            context.set(TesseractOCRConfig.class, config);
        }
        return context;
    }

    private static ParseContext initContextWithoutOcr(Parser parser) {
        ParseContext context = new ParseContext();
        context.set(Parser.class, parser);
        return context;
    }

    static String extractText(FsSettings fsSettings, int indexedChars, InputStream stream, Metadata metadata) throws IOException,
            TikaException {
        initTika(fsSettings.getFs());
        WriteOutContentHandler handler = new WriteOutContentHandler(indexedChars);
        try {
            if (parserWithOcr != null) {
                if (stream.markSupported()) {
                    logger.debug("Trying both implementations OCR and TXT.");
                    // We first try pure text extraction as this is faster than OCR
                    parserWithoutOcr.parse(stream, new BodyContentHandler(handler), metadata, contextWithoutOcr);
                    if (FsCrawlerUtil.isNullOrEmpty(handler.toString())) {
                        logger.debug("Found no text so trying now OCR.");
                        // If we did not get any text, we try again with OCR
                        stream.reset();
                        handler = new WriteOutContentHandler(indexedChars);
                        parserWithOcr.parse(stream, new BodyContentHandler(handler), metadata, contextWithOcr);
                    }
                } else {
                    logger.debug("Trying OCR implementation only as we can't rewind the stream.");
                    // We try only the OCR implementation
                    parserWithOcr.parse(stream, new BodyContentHandler(handler), metadata, contextWithOcr);
                }
            } else {
                logger.debug("Trying TXT only implementation as OCR is disabled.");
                // We try the text only implementation
                parserWithoutOcr.parse(stream, new BodyContentHandler(handler), metadata, contextWithoutOcr);
            }
        } catch (SAXException e) {
            if (!handler.isWriteLimitReached(e)) {
                // This should never happen with BodyContentHandler...
                throw new TikaException("Unexpected SAX processing failure", e);
            }
        } finally {
            stream.close();
        }
        return handler.toString();
    }

    static LanguageDetector langDetector() {
        if (detector == null) {
            try {
                detector = getDefaultLanguageDetector();
                detector.loadModels();
            } catch (IOException e) {
                logger.warn("Can not load lang detector models", e);
            }
        }
        return detector;
    }
}
