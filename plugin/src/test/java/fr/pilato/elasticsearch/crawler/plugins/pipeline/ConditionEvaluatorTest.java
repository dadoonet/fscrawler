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

package fr.pilato.elasticsearch.crawler.plugins.pipeline;

import org.junit.After;
import org.junit.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Tests for ConditionEvaluator using MVEL expressions.
 */
public class ConditionEvaluatorTest {

    @After
    public void clearCache() {
        ConditionEvaluator.clearCache();
    }

    @Test
    public void testNullOrEmptyExpressionReturnsTrue() {
        PipelineContext ctx = new PipelineContext();
        
        assertThat(ConditionEvaluator.evaluate(null, ctx)).isTrue();
        assertThat(ConditionEvaluator.evaluate("", ctx)).isTrue();
        assertThat(ConditionEvaluator.evaluate("   ", ctx)).isTrue();
    }

    @Test
    public void testTrueAndFalseLiterals() {
        PipelineContext ctx = new PipelineContext();
        
        assertThat(ConditionEvaluator.evaluate("true", ctx)).isTrue();
        assertThat(ConditionEvaluator.evaluate("TRUE", ctx)).isTrue();
        assertThat(ConditionEvaluator.evaluate("True", ctx)).isTrue();
        assertThat(ConditionEvaluator.evaluate("false", ctx)).isFalse();
        assertThat(ConditionEvaluator.evaluate("FALSE", ctx)).isFalse();
        assertThat(ConditionEvaluator.evaluate("False", ctx)).isFalse();
    }

    @Test
    public void testExtensionEquals() {
        PipelineContext ctx = new PipelineContext().withFilename("document.pdf");
        
        assertThat(ConditionEvaluator.evaluate("extension == 'pdf'", ctx)).isTrue();
        assertThat(ConditionEvaluator.evaluate("extension == 'docx'", ctx)).isFalse();
    }

    @Test
    public void testExtensionNotEquals() {
        PipelineContext ctx = new PipelineContext().withFilename("document.pdf");
        
        assertThat(ConditionEvaluator.evaluate("extension != 'docx'", ctx)).isTrue();
        assertThat(ConditionEvaluator.evaluate("extension != 'pdf'", ctx)).isFalse();
    }

    @Test
    public void testFilenameStartsWith() {
        PipelineContext ctx = new PipelineContext().withFilename("report_2024.pdf");
        
        assertThat(ConditionEvaluator.evaluate("filename.startsWith('report_')", ctx)).isTrue();
        assertThat(ConditionEvaluator.evaluate("filename.startsWith('invoice_')", ctx)).isFalse();
    }

    @Test
    public void testFilenameEndsWith() {
        PipelineContext ctx = new PipelineContext().withFilename("report_2024.pdf");
        
        assertThat(ConditionEvaluator.evaluate("filename.endsWith('.pdf')", ctx)).isTrue();
        assertThat(ConditionEvaluator.evaluate("filename.endsWith('.docx')", ctx)).isFalse();
    }

    @Test
    public void testFilenameContains() {
        PipelineContext ctx = new PipelineContext().withFilename("report_2024.pdf");
        
        assertThat(ConditionEvaluator.evaluate("filename.contains('2024')", ctx)).isTrue();
        assertThat(ConditionEvaluator.evaluate("filename.contains('2023')", ctx)).isFalse();
    }

    @Test
    public void testSizeComparison() {
        PipelineContext ctx = new PipelineContext().withSize(1024 * 1024); // 1 MB
        
        assertThat(ConditionEvaluator.evaluate("size > 500000", ctx)).isTrue();
        assertThat(ConditionEvaluator.evaluate("size < 2000000", ctx)).isTrue();
        assertThat(ConditionEvaluator.evaluate("size == 1048576", ctx)).isTrue();
        assertThat(ConditionEvaluator.evaluate("size > 2000000", ctx)).isFalse();
    }

    @Test
    public void testMimeTypeEquals() {
        PipelineContext ctx = new PipelineContext().withMimeType("application/pdf");
        
        assertThat(ConditionEvaluator.evaluate("mimeType == 'application/pdf'", ctx)).isTrue();
        assertThat(ConditionEvaluator.evaluate("mimeType == 'text/plain'", ctx)).isFalse();
    }

    @Test
    public void testMimeTypeStartsWith() {
        PipelineContext ctx = new PipelineContext().withMimeType("application/pdf");
        
        assertThat(ConditionEvaluator.evaluate("mimeType.startsWith('application/')", ctx)).isTrue();
        assertThat(ConditionEvaluator.evaluate("mimeType.startsWith('text/')", ctx)).isFalse();
    }

    @Test
    public void testTagsContains() {
        PipelineContext ctx = new PipelineContext()
                .withTags(List.of("important", "finance", "2024"));
        
        assertThat(ConditionEvaluator.evaluate("tags.contains('important')", ctx)).isTrue();
        assertThat(ConditionEvaluator.evaluate("tags.contains('archive')", ctx)).isFalse();
    }

    @Test
    public void testTagsIsEmpty() {
        PipelineContext ctx = new PipelineContext();
        
        assertThat(ConditionEvaluator.evaluate("tags.isEmpty()", ctx)).isTrue();
        
        ctx.withTag("tag1");
        assertThat(ConditionEvaluator.evaluate("tags.isEmpty()", ctx)).isFalse();
    }

    @Test
    public void testMetadataAccess() {
        PipelineContext ctx = new PipelineContext()
                .withMetadata("author", "John Doe")
                .withMetadata("department", "Finance");
        
        assertThat(ConditionEvaluator.evaluate("metadata['author'] == 'John Doe'", ctx)).isTrue();
        assertThat(ConditionEvaluator.evaluate("metadata['department'] == 'HR'", ctx)).isFalse();
    }

    @Test
    public void testMetadataContainsKey() {
        PipelineContext ctx = new PipelineContext().withMetadata("author", "John Doe");
        
        assertThat(ConditionEvaluator.evaluate("metadata.containsKey('author')", ctx)).isTrue();
        assertThat(ConditionEvaluator.evaluate("metadata.containsKey('title')", ctx)).isFalse();
    }

    @Test
    public void testInputIdEquals() {
        PipelineContext ctx = new PipelineContext().withInputId("local_input");
        
        assertThat(ConditionEvaluator.evaluate("inputId == 'local_input'", ctx)).isTrue();
        assertThat(ConditionEvaluator.evaluate("inputId == 'ssh_input'", ctx)).isFalse();
    }

    @Test
    public void testPathContains() {
        PipelineContext ctx = new PipelineContext().withPath("/documents/finance/reports/q1.pdf");
        
        assertThat(ConditionEvaluator.evaluate("path.contains('finance')", ctx)).isTrue();
        assertThat(ConditionEvaluator.evaluate("path.contains('hr')", ctx)).isFalse();
    }

    @Test
    public void testLogicalAnd() {
        PipelineContext ctx = new PipelineContext()
                .withFilename("document.pdf")
                .withSize(1024 * 1024);
        
        assertThat(ConditionEvaluator.evaluate("extension == 'pdf' && size > 500000", ctx)).isTrue();
        assertThat(ConditionEvaluator.evaluate("extension == 'pdf' && size > 2000000", ctx)).isFalse();
        assertThat(ConditionEvaluator.evaluate("extension == 'docx' && size > 500000", ctx)).isFalse();
    }

    @Test
    public void testLogicalOr() {
        PipelineContext ctx = new PipelineContext()
                .withFilename("document.pdf")
                .withMimeType("application/pdf");
        
        assertThat(ConditionEvaluator.evaluate("extension == 'pdf' || extension == 'docx'", ctx)).isTrue();
        assertThat(ConditionEvaluator.evaluate("extension == 'docx' || extension == 'xlsx'", ctx)).isFalse();
    }

    @Test
    public void testLogicalNot() {
        PipelineContext ctx = new PipelineContext().withFilename("document.pdf");
        
        assertThat(ConditionEvaluator.evaluate("!(extension == 'docx')", ctx)).isTrue();
        assertThat(ConditionEvaluator.evaluate("!(extension == 'pdf')", ctx)).isFalse();
    }

    @Test
    public void testComplexExpression() {
        PipelineContext ctx = new PipelineContext()
                .withFilename("document.pdf")
                .withSize(1024 * 1024)
                .withTags(List.of("important", "finance"))
                .withInputId("local_input");
        
        String expr = "(extension == 'pdf' || extension == 'docx') && " +
                      "size > 100000 && " +
                      "tags.contains('important') && " +
                      "inputId == 'local_input'";
        
        assertThat(ConditionEvaluator.evaluate(expr, ctx)).isTrue();
    }

    @Test
    public void testInvalidExpressionThrowsException() {
        PipelineContext ctx = new PipelineContext();
        
        assertThatThrownBy(() -> ConditionEvaluator.evaluate("invalid syntax {{{", ctx))
                .isInstanceOf(ConditionEvaluator.ConditionEvaluationException.class);
    }

    @Test
    public void testIsValidExpression() {
        assertThat(ConditionEvaluator.isValidExpression("extension == 'pdf'")).isTrue();
        assertThat(ConditionEvaluator.isValidExpression("size > 1000")).isTrue();
        assertThat(ConditionEvaluator.isValidExpression("tags.contains('x')")).isTrue();
        assertThat(ConditionEvaluator.isValidExpression(null)).isTrue();
        assertThat(ConditionEvaluator.isValidExpression("")).isTrue();
        
        assertThat(ConditionEvaluator.isValidExpression("invalid syntax {{{")).isFalse();
        assertThat(ConditionEvaluator.isValidExpression("foo(bar baz")).isFalse();
    }

    @Test
    public void testNullValuesInContext() {
        // Ensure that null values in context don't cause NPEs
        PipelineContext ctx = new PipelineContext();
        // All fields are null/empty by default
        
        // Should not throw, should evaluate to appropriate values
        assertThat(ConditionEvaluator.evaluate("filename == ''", ctx)).isTrue();
        assertThat(ConditionEvaluator.evaluate("extension == ''", ctx)).isTrue();
        assertThat(ConditionEvaluator.evaluate("size == 0", ctx)).isTrue();
        assertThat(ConditionEvaluator.evaluate("tags.isEmpty()", ctx)).isTrue();
        assertThat(ConditionEvaluator.evaluate("metadata.isEmpty()", ctx)).isTrue();
    }

    @Test
    public void testRegularExpressionMatching() {
        PipelineContext ctx = new PipelineContext().withFilename("report_2024_q1.pdf");
        
        // MVEL supports regex matching with ~= operator
        assertThat(ConditionEvaluator.evaluate("filename ~= '.*_2024_.*'", ctx)).isTrue();
        assertThat(ConditionEvaluator.evaluate("filename ~= '.*_2023_.*'", ctx)).isFalse();
    }

    @Test
    public void testCaching() {
        PipelineContext ctx = new PipelineContext().withFilename("document.pdf");
        
        // First evaluation compiles the expression
        ConditionEvaluator.evaluate("extension == 'pdf'", ctx);
        
        // Second evaluation should use cached expression (no way to verify directly, but should work)
        assertThat(ConditionEvaluator.evaluate("extension == 'pdf'", ctx)).isTrue();
        
        // Clear cache and verify it still works
        ConditionEvaluator.clearCache();
        assertThat(ConditionEvaluator.evaluate("extension == 'pdf'", ctx)).isTrue();
    }
}
