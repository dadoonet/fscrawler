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

package fr.pilato.elasticsearch.crawler.fs.test;

import com.carrotsearch.randomizedtesting.RandomizedRunner;
import com.carrotsearch.randomizedtesting.annotations.Listeners;
import com.carrotsearch.randomizedtesting.annotations.TimeoutSuite;
import com.carrotsearch.randomizedtesting.generators.RandomInts;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.lucene.util.TimeUnits;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.rules.TemporaryFolder;
import org.junit.rules.TestName;
import org.junit.runner.RunWith;

import java.io.File;
import java.io.IOException;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Locale;
import java.util.TimeZone;
import java.util.concurrent.TimeUnit;
import java.util.function.BooleanSupplier;

import static com.carrotsearch.randomizedtesting.RandomizedTest.randomLocale;
import static com.carrotsearch.randomizedtesting.RandomizedTest.randomTimeZone;
import static fr.pilato.elasticsearch.crawler.fs.util.FsCrawlerUtil.copyDefaultResources;
import static org.apache.lucene.util.LuceneTestCase.random;

@RunWith(RandomizedRunner.class)
@Listeners({FSCrawlerReproduceInfoPrinter.class})
@TimeoutSuite(millis = 5 * TimeUnits.MINUTE)
public abstract class AbstractFSCrawlerTestCase {

    protected static final Logger staticLogger = LogManager.getLogger(AbstractFSCrawlerTestCase.class);

    @Rule
    public TestName name = new TestName();

    @ClassRule
    public static TemporaryFolder folder = new TemporaryFolder();
    protected static Path rootTmpDir;
    protected static Path metadataDir;

    @BeforeClass
    public static void createTmpDir() throws IOException, URISyntaxException {
        folder.create();
        rootTmpDir = Paths.get(folder.getRoot().toURI());
        // We also need to create default mapping files
        metadataDir = rootTmpDir.resolve(".fscrawler");
        if (Files.notExists(metadataDir)) {
            Files.createDirectory(metadataDir);
        }
        copyDefaultResources(metadataDir);
        staticLogger.debug("  --> Test metadata dir ready in [{}]", metadataDir);
    }

    private static final Locale savedLocale = Locale.getDefault();
    private static final TimeZone savedTimeZone = TimeZone.getDefault();

    @BeforeClass
    public static void setLocale() {
        String testLocale = System.getProperty("tests.locale", "random");
        Locale locale = testLocale.equals("random") ? randomLocale() : new Locale.Builder().setLanguageTag(testLocale).build();
        staticLogger.debug("Running test suite with Locale [{}]", locale);
        Locale.setDefault(locale);
    }

    @AfterClass
    public static void resetLocale() {
        Locale.setDefault(savedLocale);
    }

    @BeforeClass
    public static void setTimeZone() {
        String testTimeZone = System.getProperty("tests.timezone", "random");
        TimeZone timeZone = testTimeZone.equals("random") ? randomTimeZone() : TimeZone.getTimeZone(testTimeZone);
        staticLogger.debug("Running test suite with TimeZone [{}]/[{}]", timeZone.getID(), timeZone.getDisplayName());
        TimeZone.setDefault(timeZone);
    }

    @AfterClass
    public static void resetTimeZone() {
        TimeZone.setDefault(savedTimeZone);
    }

    @AfterClass
    public static void printMetadataDirContent() throws IOException {
        staticLogger.debug("ls -l {}", metadataDir);
        Files.list(metadataDir).forEach(path -> staticLogger.debug("{}", path));
    }

    protected final Logger logger = LogManager.getLogger(this.getClass());

    protected String getCurrentTestName() {
        return toUnderscoreCase(name.getMethodName());
    }

    public static int between(int min, int max) {
        return RandomInts.randomIntBetween(random(), min, max);
    }

    public static boolean awaitBusy(BooleanSupplier breakSupplier) throws InterruptedException {
        return awaitBusy(breakSupplier, 10, TimeUnit.SECONDS);
    }

    // After 1s, we stop growing the sleep interval exponentially and just sleep 1s until maxWaitTime
    private static final long AWAIT_BUSY_THRESHOLD = 1000L;

    public static boolean awaitBusy(BooleanSupplier breakSupplier, long maxWaitTime, TimeUnit unit) throws InterruptedException {
        long maxTimeInMillis = TimeUnit.MILLISECONDS.convert(maxWaitTime, unit);
        long timeInMillis = 1;
        long sum = 0;
        while (sum + timeInMillis < maxTimeInMillis) {
            if (breakSupplier.getAsBoolean()) {
                return true;
            }
            Thread.sleep(timeInMillis);
            sum += timeInMillis;
            timeInMillis = Math.min(AWAIT_BUSY_THRESHOLD, timeInMillis * 2);
        }
        timeInMillis = maxTimeInMillis - sum;
        Thread.sleep(Math.max(timeInMillis, 0));
        return breakSupplier.getAsBoolean();
    }

    public static String toUnderscoreCase(String value) {
        StringBuilder sb = new StringBuilder();
        boolean changed = false;
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            if (Character.isUpperCase(c)) {
                if (!changed) {
                    sb.setLength(0);
                    // copy it over here
                    for (int j = 0; j < i; j++) {
                        sb.append(value.charAt(j));
                    }
                    changed = true;
                    if (i == 0) {
                        sb.append(Character.toLowerCase(c));
                    } else {
                        sb.append('_');
                        sb.append(Character.toLowerCase(c));
                    }
                } else {
                    sb.append('_');
                    sb.append(Character.toLowerCase(c));
                }
            } else {
                if (changed) {
                    sb.append(c);
                }
            }
        }
        if (!changed) {
            return value;
        }
        return sb.toString();
    }

    public static File URLtoFile(URL url) {
        try {
            return new File(url.toURI());
        } catch(URISyntaxException e) {
            return new File(url.getPath());
        }
    }

    public static String getUrl(String... subdirs) {
        URL resource = AbstractFSCrawlerTestCase.class.getResource("/job-sample.json");
        File dir = URLtoFile(resource).getParentFile();

        for (String subdir : subdirs) {
            dir = new File(dir, subdir);
        }

        return dir.getAbsoluteFile().getAbsolutePath();
    }

}
