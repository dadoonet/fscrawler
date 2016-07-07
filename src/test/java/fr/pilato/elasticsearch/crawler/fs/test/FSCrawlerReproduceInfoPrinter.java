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

import com.carrotsearch.randomizedtesting.ReproduceErrorMessageBuilder;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.util.Strings;
import org.junit.AssumptionViolatedException;
import org.junit.runner.Description;
import org.junit.runner.notification.Failure;
import org.junit.runner.notification.RunListener;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.TimeZone;

import static com.carrotsearch.randomizedtesting.SysGlobals.SYSPROP_ITERATIONS;
import static com.carrotsearch.randomizedtesting.SysGlobals.SYSPROP_PREFIX;
import static com.carrotsearch.randomizedtesting.SysGlobals.SYSPROP_TESTMETHOD;

public class FSCrawlerReproduceInfoPrinter extends RunListener {

    protected static final Logger logger = LogManager.getLogger();

    @Override
    public void testStarted(Description description) throws Exception {
        logger.trace("Test {} started", description.getDisplayName());
    }

    @Override
    public void testFinished(Description description) throws Exception {
        logger.trace("Test {} finished", description.getDisplayName());
    }

    @Override
    public void testFailure(Failure failure) throws Exception {
        // Ignore assumptions.
        if (failure.getException() instanceof AssumptionViolatedException) {
            return;
        }

        final StringBuilder b = new StringBuilder();
        b.append("REPRODUCE WITH:\n");
        b.append("mvn test");

        MavenMessageBuilder mavenMessageBuilder = new MavenMessageBuilder(b);
        mavenMessageBuilder.appendAllOpts(failure.getDescription());

        System.err.println(b.toString());
    }

    /**
     * Declared on test classes to add extra properties to the reproduction
     * info. Note that this is scanned from all superclasses.
     */
    @Retention(RetentionPolicy.RUNTIME)
    @Target(ElementType.TYPE)
    public @interface Properties {
        String[] value();
    }

    protected static class MavenMessageBuilder extends ReproduceErrorMessageBuilder {

        public MavenMessageBuilder(StringBuilder b) {
            super(b);
        }

        @Override
        public ReproduceErrorMessageBuilder appendAllOpts(Description description) {
            super.appendAllOpts(description);

            if (description.getMethodName() != null) {
                //prints out the raw method description instead of methodName(description) which filters out the parameters
                super.appendOpt(SYSPROP_TESTMETHOD(), "\"" + description.getMethodName() + "\"");
            }

            List<String> properties = new ArrayList<>();
            scanProperties(description.getTestClass(), properties);
            appendProperties(properties.toArray(new String[properties.size()]));
            return appendESProperties();
        }

        /**
         * Scans c and its superclasses for the {@linkplain Properties}
         * annotations, copying all the listed properties in order from
         * superclass to subclass.
         */
        private void scanProperties(Class<?> c, List<String> properties) {
            if (Object.class.equals(c) == false) {
                scanProperties(c.getSuperclass(), properties);
            }
            Properties extraParameterAnnocation = c.getAnnotation(Properties.class);
            if (extraParameterAnnocation != null) {
                for (String property : extraParameterAnnocation.value()) {
                    properties.add(property);
                }
            }
        }

        @Override
        public ReproduceErrorMessageBuilder appendEnvironmentSettings() {
            // we handle our own environment settings
            return this;
        }

        /**
         * Append a single VM option.
         */
        @Override
        public ReproduceErrorMessageBuilder appendOpt(String sysPropName, String value) {
            if (sysPropName.equals(SYSPROP_ITERATIONS())) { // we don't want the iters to be in there!
                return this;
            }
            if (sysPropName.equals(SYSPROP_TESTMETHOD())) {
                //don't print out the test method, we print it ourselves in appendAllOpts
                //without filtering out the parameters (needed for REST tests)
                return this;
            }
            if (sysPropName.equals(SYSPROP_PREFIX())) {
                // we always use the default prefix
                return this;
            }
            if (!Strings.isBlank(value)) {
                if (value.indexOf(' ') >= 0) {
                    return super.appendOpt(sysPropName, '"' + value + '"');
                }
                return super.appendOpt(sysPropName, value);
            }
            return this;
        }

        public ReproduceErrorMessageBuilder appendESProperties() {
            appendOpt("tests.locale", Locale.getDefault().toLanguageTag());
            appendOpt("tests.timezone", TimeZone.getDefault().getID());
            return this;
        }

        protected ReproduceErrorMessageBuilder appendProperties(String... properties) {
            for (String sysPropName : properties) {
                if (!Strings.isBlank(System.getProperty(sysPropName))) {
                    appendOpt(sysPropName, System.getProperty(sysPropName));
                }
            }
            return this;
        }

    }
}
