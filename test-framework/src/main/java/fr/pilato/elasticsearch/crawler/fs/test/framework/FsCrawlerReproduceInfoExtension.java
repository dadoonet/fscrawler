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

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.TimeZone;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.junit.jupiter.api.extension.AfterTestExecutionCallback;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.junit.jupiter.api.extension.LifecycleMethodExecutionExceptionHandler;
import org.opentest4j.TestAbortedException;

/**
 * JUnit 6 extension that prints an exact reproduction command line when a test fails, adapted to the original build
 * tool (Maven or Gradle).
 */
public class FsCrawlerReproduceInfoExtension
        implements AfterTestExecutionCallback, LifecycleMethodExecutionExceptionHandler {

    private static final Logger logger = LogManager.getLogger();

    /**
     * Root seed of randomizedtesting for the current JVM fork, formatted for {@code -Dtests.seed}. It is JVM-wide (one
     * value per surefire/failsafe fork) and captured once by {@link AbstractFSCrawlerTestCase}. Remains {@code null}
     * until known (e.g. a {@code @BeforeAll} fails before the base class had a chance to record it).
     */
    private static volatile String rootSeed;

    /**
     * Records the randomizedtesting root seed of the current JVM fork so it can be added to the reproduction command
     * line. Called once per fork by {@link AbstractFSCrawlerTestCase}.
     *
     * @param seed the root seed formatted for {@code -Dtests.seed}, or {@code null} if unknown
     */
    public static void rememberRootSeed(String seed) {
        rootSeed = seed;
    }

    /** @return the root seed currently recorded for this JVM fork, or {@code null} if none is known yet */
    public static String getRootSeed() {
        return rootSeed;
    }

    /** Annotation to put on test classes to inject additional system properties into the reproduction command. */
    @Retention(RetentionPolicy.RUNTIME)
    @Target(ElementType.TYPE)
    public @interface Properties {
        String[] value();
    }

    @Override
    public void afterTestExecution(ExtensionContext context) throws Exception {
        // 1. We only handle failures (successes and ignored assumptions are skipped)
        if (context.getExecutionException().isPresent()) {
            Throwable exception = context.getExecutionException().get();
            if (!(exception instanceof TestAbortedException)) {
                logger.fatal(buildReproduceCommand(
                        context, context.getRequiredTestMethod().getName()));
            }
        }
    }

    @Override
    public void handleBeforeAllMethodExecutionException(ExtensionContext context, Throwable throwable)
            throws Throwable {
        logLifecycleFailure(context, throwable);
        throw throwable;
    }

    @Override
    public void handleBeforeEachMethodExecutionException(ExtensionContext context, Throwable throwable)
            throws Throwable {
        logLifecycleFailure(context, throwable);
        throw throwable;
    }

    @Override
    public void handleAfterEachMethodExecutionException(ExtensionContext context, Throwable throwable)
            throws Throwable {
        logLifecycleFailure(context, throwable);
        throw throwable;
    }

    @Override
    public void handleAfterAllMethodExecutionException(ExtensionContext context, Throwable throwable) throws Throwable {
        logLifecycleFailure(context, throwable);
        throw throwable;
    }

    private void logLifecycleFailure(ExtensionContext context, Throwable throwable) {
        if (throwable instanceof TestAbortedException) {
            return;
        }
        String methodName = context.getTestMethod().map(Method::getName).orElse(null);
        logger.fatal(buildReproduceCommand(context, methodName));
    }

    String buildReproduceCommand(ExtensionContext context, String methodName) {
        StringBuilder commandBuilder = new StringBuilder();
        commandBuilder.append("\n🐛 REPRODUCE WITH:\n");

        Class<?> testClass = context.getRequiredTestClass();
        String className = testClass.getName();
        String simpleName = testClass.getSimpleName();

        boolean isIntegrationTest = simpleName.endsWith("IT");
        commandBuilder.append("mvn verify");

        if (isIntegrationTest) {
            commandBuilder.append(" -DskipUnitTests");
            commandBuilder.append(" -Dit.test=").append(className);
        } else {
            commandBuilder.append(" -DskipIntegTests");
            commandBuilder.append(" -Dtest=").append(className);
        }

        if (methodName != null) {
            commandBuilder.append("#").append(methodName);
        }

        appendOpt(commandBuilder, "tests.seed", rootSeed);
        appendOpt(commandBuilder, "tests.locale", Locale.getDefault().toLanguageTag());
        appendOpt(commandBuilder, "tests.timezone", TimeZone.getDefault().getID());

        List<String> extraProperties = new ArrayList<>();
        scanProperties(testClass, extraProperties);
        for (String sysProp : extraProperties) {
            String val = System.getProperty(sysProp);
            if (val != null && !val.trim().isEmpty()) {
                appendOpt(commandBuilder, sysProp, val);
            }
        }

        return commandBuilder.toString();
    }

    /** Safely append a -D option to the StringBuilder, with space handling. */
    private void appendOpt(StringBuilder sb, String key, String value) {
        if (value != null && !value.trim().isEmpty()) {
            sb.append(" ");
            sb.append("-D").append(key).append("=");
            if (value.indexOf(' ') >= 0 && !value.startsWith("\"")) {
                sb.append('"').append(value).append('"');
            } else {
                sb.append(value);
            }
        }
    }

    /** Walk through the class and its superclasses to collect @Properties annotations. */
    private void scanProperties(Class<?> c, List<String> properties) {
        if (c == null || Object.class.equals(c)) {
            return;
        }
        scanProperties(c.getSuperclass(), properties);

        Properties annotation = c.getAnnotation(Properties.class);
        if (annotation != null) {
            Collections.addAll(properties, annotation.value());
        }
    }
}
