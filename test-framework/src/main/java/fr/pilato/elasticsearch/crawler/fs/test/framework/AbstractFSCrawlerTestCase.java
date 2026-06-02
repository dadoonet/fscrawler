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
package fr.pilato.elasticsearch.crawler.fs.test.framework;

import com.carrotsearch.randomizedtesting.jupiter.DetectThreadLeaks;
import com.carrotsearch.randomizedtesting.jupiter.Randomized;
import com.carrotsearch.randomizedtesting.jupiter.RandomizedTest;
import java.io.File;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Locale;
import java.util.Random;
import java.util.TimeZone;
import org.apache.commons.lang3.StringUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.TestInfo;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;

@Randomized
@ExtendWith(FsCrawlerReproduceInfoExtension.class)
@DetectThreadLeaks(scope = DetectThreadLeaks.Scope.SUITE)
@DetectThreadLeaks.LingerTime(millis = 5000) // 5 sec lingering
@DetectThreadLeaks.ExcludeThreads({
    WindowsSpecificThreadFilter.class,
    TestContainerThreadFilter.class,
    JNACleanerThreadFilter.class,
    IntelliJThreadsFilter.class,
    JUnitThreadsFilter.class,
    KeepAliveTimerThreadFilter.class,
    Java2DThreadFilter.class
})
@Fast
public abstract class AbstractFSCrawlerTestCase {

    private static final Logger logger = LogManager.getLogger();
    private static final String RANDOM = "random";
    private static final Locale savedLocale = Locale.getDefault();
    private static final TimeZone savedTimeZone = TimeZone.getDefault();
    protected static final String indexPrefix = getSystemProperty("tests.index.prefix", "");

    /** For tests only: maximum time to wait for a search when we want to be sure that something is in the index. */
    public static final Duration MAX_WAIT_FOR_SEARCH = Duration.ofMinutes(5);

    /**
     * For tests only: maximum time to wait for a search when we want to be sure that something is in the index, but we
     * are running long tests (like with Tika OCR for instance).
     */
    public static final Duration MAX_WAIT_FOR_SEARCH_LONG_TESTS = Duration.ofMinutes(10);

    @TempDir
    protected static Path rootTmpDir;

    protected String jobName;
    protected Random TEST_RANDOM;

    @BeforeAll
    static void setLocale(Random rnd) {
        String testLocale = getSystemProperty("tests.locale", RANDOM);
        Locale locale = testLocale.equals(RANDOM)
                ? RandomizedTest.randomLocale(rnd)
                : new Locale.Builder().setLanguageTag(testLocale).build();
        logger.debug("Running test suite with Locale [{}]", locale);
        Locale.setDefault(locale);
    }

    @AfterAll
    static void resetLocale() {
        Locale.setDefault(savedLocale);
    }

    @BeforeAll
    static void setTimeZone(Random rnd) {
        String testTimeZone = getSystemProperty("tests.timezone", RANDOM);
        TimeZone timeZone =
                testTimeZone.equals(RANDOM) ? RandomizedTest.randomTimeZone(rnd) : TimeZone.getTimeZone(testTimeZone);
        logger.debug("Running test suite with TimeZone [{}]/[{}]", timeZone.getID(), timeZone.getDisplayName());
        TimeZone.setDefault(timeZone);
    }

    @AfterAll
    static void resetTimeZone() {
        TimeZone.setDefault(savedTimeZone);
    }

    /**
     * We are computing automatically the job name from the current method test name.
     *
     * @param testInfo The current test
     */
    @BeforeEach
    void setJobName(TestInfo testInfo) {
        jobName = toUnderscoreCase(testInfo.getTestMethod().orElseThrow().getName());
    }

    /**
     * We store the test Random so we can reuse it later in tests as TEST_RANDOM
     *
     * @param rnd The Random provided by the @Randomized annotation
     */
    @BeforeEach
    void storeRandom(Random rnd) {
        TEST_RANDOM = rnd;
    }

    /**
     * Get the crawler name which is also used as the index name. This is a combination of the index prefix, the current
     * class name and the current test name. Note that the index prefix might be empty. It's normally the pull request
     * number if set with -Dtests.index.prefix
     *
     * @return the crawler name to use
     */
    protected String getCrawlerName() {
        return getCrawlerName(this.getClass(), jobName);
    }

    /**
     * Get the crawler name from a class and a method name.
     *
     * @param clazz the class to use
     * @param methodName the method name
     * @return the crawler name to use
     */
    protected static String getCrawlerName(Class<?> clazz, String methodName) {
        String testName;
        if (indexPrefix.isEmpty()) {
            testName = "fscrawler_"
                    .concat(toUnderscoreCase(clazz.getSimpleName()))
                    .concat("_")
                    .concat(methodName);
        } else {
            testName = "fscrawler_"
                    .concat(indexPrefix)
                    .concat("_")
                    .concat(toUnderscoreCase(clazz.getSimpleName()))
                    .concat("_")
                    .concat(methodName);
        }
        return testName.contains(" ") ? StringUtils.split(testName, " ")[0] : testName;
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
                    if (i != 0) {
                        sb.append('_');
                    }
                } else {
                    sb.append('_');
                }
                sb.append(Character.toLowerCase(c));
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
        } catch (URISyntaxException e) {
            return new File(url.getPath());
        }
    }

    /**
     * Get a System Property. If it does not exist or if it's empty, the fallback value will be returned.
     *
     * @param envName The system property name
     * @param defaultValue The fallback value
     * @return The property value or its default value
     */
    protected static String getSystemProperty(String envName, String defaultValue) {
        String property = System.getProperty(envName);
        if (property == null || property.isBlank()) {
            return defaultValue;
        } else {
            return property;
        }
    }

    /**
     * Get a System Property. If it does not exist or if it's empty, the fallback value will be returned.
     *
     * @param envName The system property name
     * @param defaultValue The fallback value
     * @return The property value or its default value
     */
    protected static int getSystemProperty(String envName, int defaultValue) {
        String property = System.getProperty(envName);
        if (property == null || property.isBlank()) {
            return defaultValue;
        } else {
            return Integer.parseInt(property);
        }
    }

    /**
     * Get a System Property. If it does not exist or if it's empty, the fallback value will be returned.
     *
     * @param envName The system property name
     * @param defaultValue The fallback value
     * @return The property value or its default value
     */
    protected static boolean getSystemProperty(String envName, boolean defaultValue) {
        String property = System.getProperty(envName);
        if (property == null || property.isBlank()) {
            return defaultValue;
        } else {
            return Boolean.parseBoolean(property);
        }
    }
}
