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
package fr.pilato.elasticsearch.crawler.fs.framework;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.Objects;
import org.apache.commons.lang3.StringUtils;

/** Builds and prints the FSCrawler welcome banner. */
public final class Banner {

    private static final int WIDTH = 100;

    /**
     * This is coming from: <a
     * href="https://patorjk.com/software/taag/#p=display&f=3D%20Diagonal&t=FSCrawler">https://patorjk.com/software/taag/#p=display&f=3D%20Diagonal&t=FSCrawler</a>
     */
    private static final String ART = loadArt();

    private Banner() {}

    /** Renders the full banner. The version is injected so the output is deterministic and testable. */
    static String render(String version) {
        return separatorLine(",", ".")
                + centerAsciiArt(version)
                + separatorLine("+", "+")
                + bannerLine("You know, for Files!")
                + bannerLine("Made from France with Love")
                + bannerLine("Source: https://github.com/dadoonet/fscrawler/")
                + bannerLine("Documentation: https://fscrawler.readthedocs.io/")
                + separatorLine("`", "'");
    }

    /** Prints the banner to the console, using the current FSCrawler version. */
    public static void print() {
        FSCrawlerLogger.console(render(Version.getVersion()));
    }

    private static String centerAsciiArt(String version) {
        String[] lines = StringUtils.split(ART, '\n');

        // Edit line 0 as we want to add the version
        String firstLine = StringUtils.stripEnd(StringUtils.center(lines[0], WIDTH), null);
        String pad = StringUtils.rightPad(firstLine, WIDTH - version.length() - 1) + version;
        lines[0] = pad;

        StringBuilder content = new StringBuilder();
        for (String line : lines) {
            content.append(bannerLine(line));
        }

        return content.toString();
    }

    private static String bannerLine(String text) {
        return "|" + StringUtils.center(text, WIDTH) + "|\n";
    }

    private static String separatorLine(String first, String last) {
        return first + StringUtils.center("", WIDTH, "-") + last + "\n";
    }

    private static String loadArt() {
        try (InputStream is = Banner.class.getResourceAsStream("/banner.txt")) {
            Objects.requireNonNull(is, "The banner.txt resource is missing from the classpath");
            return new String(is.readAllBytes(), StandardCharsets.UTF_8).replace("\r\n", "\n");
        } catch (IOException e) {
            throw new UncheckedIOException("Unable to load the FSCrawler banner", e);
        }
    }
}
