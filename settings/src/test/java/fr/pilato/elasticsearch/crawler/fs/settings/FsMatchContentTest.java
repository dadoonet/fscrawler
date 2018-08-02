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

package fr.pilato.elasticsearch.crawler.fs.settings;

import fr.pilato.elasticsearch.crawler.fs.test.framework.AbstractFSCrawlerTestCase;
import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static fr.pilato.elasticsearch.crawler.fs.framework.FsCrawlerUtil.isIndexable;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;

public class FsMatchContentTest extends AbstractFSCrawlerTestCase {

    @Test
    public void testIncludeAndExcludeTextPattern() {
        // Test with null or empty text
        regexTester(null, null, true);
        regexTester(null, new ArrayList<>(), true);
        regexTester(null, Collections.singletonList("foo"), true);
        regexTester("", null, true);
        regexTester("", new ArrayList<>(), true);
        regexTester("", Collections.singletonList("foo"), true);

        // Test with text
        regexTester("foo bar", null, true);
        regexTester("foo bar", new ArrayList<>(), true);
        regexTester("foo bar", Collections.singletonList("^foo$"), false);
        regexTester("foo bar", Collections.singletonList(".*foo.*"), true);
        regexTester("foo bar", Collections.singletonList("^bar$"), false);
        regexTester("foo bar", Collections.singletonList(".*bar.*"), true);
        regexTester("foo bar", Arrays.asList(".*foo.*", ".*bar.*"), true);
        regexTester("foo bar", Arrays.asList(".*foo.*", "^bar$"), false);

        regexTester("baz", Collections.singletonList("^foo$"), false);
        regexTester("baz", Collections.singletonList(".*foo.*"), false);
        regexTester("baz", Collections.singletonList("^bar$"), false);
        regexTester("baz", Collections.singletonList(".*bar.*"), false);
        regexTester("baz", Arrays.asList(".*foo.*", ".*bar.*"), false);
        regexTester("baz", Arrays.asList(".*foo.*", "^bar$"), false);

        // Test with multi line text
        String text = "This is containing foo as one of the words.\n" +
                "Another line which contains bar also.\n";
        regexTester(text, Collections.singletonList("^foo$"), false);
        regexTester(text, Collections.singletonList(".*foo.*"), true);
        regexTester(text, Collections.singletonList("^bar$"), false);
        regexTester(text, Collections.singletonList(".*bar.*"), true);
        regexTester(text, Arrays.asList(".*foo.*", ".*bar.*"), true);
        regexTester(text, Arrays.asList(".*foo.*", "^bar$"), false);

        // Test a Visa Credit Card pattern
        regexTester("4012888888881881", Collections.singletonList("^4\\d{3}([\\ \\-]?)\\d{4}\\1\\d{4}\\1\\d{4}$"), true);
        regexTester("4012 8888 8888 1881", Collections.singletonList("^4\\d{3}([\\ \\-]?)\\d{4}\\1\\d{4}\\1\\d{4}$"), true);
        regexTester("4012-8888-8888-1881", Collections.singletonList("^4\\d{3}([\\ \\-]?)\\d{4}\\1\\d{4}\\1\\d{4}$"), true);
    }
    
    private void regexTester(String input, List<String> regexes, boolean expected) {
        assertThat(regexes + " should " + (expected ? "" : "not ") + "match " + input,
                isIndexable(input, regexes), is(expected));
    }
}
