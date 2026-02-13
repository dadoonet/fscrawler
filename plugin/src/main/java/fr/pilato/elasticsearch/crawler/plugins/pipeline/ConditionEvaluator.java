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

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.mvel2.MVEL;
import org.mvel2.ParserContext;
import org.mvel2.compiler.CompiledExpression;

import java.io.Serializable;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Evaluates conditional expressions using MVEL.
 * 
 * <p>The evaluator supports expressions that reference PipelineContext properties:
 * <ul>
 *   <li>filename - The file name</li>
 *   <li>extension - The file extension</li>
 *   <li>path - The full path</li>
 *   <li>size - The file size in bytes</li>
 *   <li>inputId - The ID of the input that produced this document</li>
 *   <li>mimeType - The detected MIME type</li>
 *   <li>index - The target index</li>
 *   <li>tags - List of tags</li>
 *   <li>metadata - Map of metadata key-value pairs</li>
 * </ul>
 * 
 * <p>Example expressions:
 * <ul>
 *   <li>{@code extension == 'pdf'}</li>
 *   <li>{@code size > 1024000}</li>
 *   <li>{@code tags.contains('important')}</li>
 *   <li>{@code filename.startsWith('report_')}</li>
 *   <li>{@code mimeType == 'application/json' || extension == 'json'}</li>
 * </ul>
 */
public class ConditionEvaluator {

    private static final Logger logger = LogManager.getLogger(ConditionEvaluator.class);

    // Cache compiled expressions for performance
    private static final Map<String, Serializable> compiledExpressions = new ConcurrentHashMap<>();

    /**
     * Evaluates a condition expression against the given context.
     * 
     * @param expression The MVEL expression to evaluate
     * @param context The pipeline context providing variables
     * @return true if the condition matches, false otherwise
     * @throws ConditionEvaluationException if the expression is invalid or evaluation fails
     */
    public static boolean evaluate(String expression, PipelineContext context) {
        if (expression == null || expression.isBlank()) {
            return true; // No condition means always match
        }

        String normalizedExpression = expression.trim();
        
        // Short-circuit for common cases
        if ("true".equalsIgnoreCase(normalizedExpression)) {
            return true;
        }
        if ("false".equalsIgnoreCase(normalizedExpression)) {
            return false;
        }

        try {
            // Get or compile the expression
            Serializable compiled = compiledExpressions.computeIfAbsent(normalizedExpression, expr -> {
                ParserContext parserContext = createParserContext();
                return MVEL.compileExpression(expr, parserContext);
            });

            // Create variables map from context
            Map<String, Object> variables = createVariablesFromContext(context);

            // Evaluate the expression
            Object result = MVEL.executeExpression(compiled, variables);

            if (result instanceof Boolean) {
                return (Boolean) result;
            } else {
                logger.warn("Expression [{}] returned non-boolean result: {}. Treating as true if not null.", 
                        expression, result);
                return result != null;
            }
        } catch (Exception e) {
            logger.error("Failed to evaluate condition [{}]: {}", expression, e.getMessage());
            throw new ConditionEvaluationException("Failed to evaluate condition: " + expression, e);
        }
    }

    /**
     * Validates an expression without evaluating it.
     * 
     * @param expression The expression to validate
     * @return true if the expression is syntactically valid
     */
    public static boolean isValidExpression(String expression) {
        if (expression == null || expression.isBlank()) {
            return true;
        }

        try {
            ParserContext parserContext = createParserContext();
            MVEL.compileExpression(expression.trim(), parserContext);
            return true;
        } catch (Exception e) {
            logger.debug("Invalid expression [{}]: {}", expression, e.getMessage());
            return false;
        }
    }

    /**
     * Clears the compiled expression cache.
     * Useful for testing or when expressions might change at runtime.
     */
    public static void clearCache() {
        compiledExpressions.clear();
    }

    /**
     * Creates a parser context with common imports and type hints.
     */
    private static ParserContext createParserContext() {
        ParserContext ctx = new ParserContext();
        
        // Import common classes that might be useful in expressions
        ctx.addImport(String.class);
        ctx.addImport(java.util.List.class);
        ctx.addImport(java.util.Map.class);
        ctx.addImport(java.util.Set.class);
        
        // Add input variable types for better error messages
        ctx.addInput("filename", String.class);
        ctx.addInput("extension", String.class);
        ctx.addInput("path", String.class);
        ctx.addInput("size", Long.class);
        ctx.addInput("inputId", String.class);
        ctx.addInput("mimeType", String.class);
        ctx.addInput("index", String.class);
        ctx.addInput("tags", java.util.Set.class);
        ctx.addInput("metadata", Map.class);
        
        return ctx;
    }

    /**
     * Creates a variables map from the pipeline context.
     */
    private static Map<String, Object> createVariablesFromContext(PipelineContext context) {
        Map<String, Object> vars = new HashMap<>();
        
        vars.put("filename", context.getFilename() != null ? context.getFilename() : "");
        vars.put("extension", context.getExtension() != null ? context.getExtension() : "");
        vars.put("path", context.getPath() != null ? context.getPath() : "");
        vars.put("size", context.getSize());
        vars.put("inputId", context.getInputId() != null ? context.getInputId() : "");
        vars.put("mimeType", context.getMimeType() != null ? context.getMimeType() : "");
        vars.put("index", context.getIndex() != null ? context.getIndex() : "");
        vars.put("tags", context.getTags() != null ? context.getTags() : java.util.Collections.emptySet());
        vars.put("metadata", context.getMetadata() != null ? context.getMetadata() : java.util.Collections.emptyMap());
        
        return vars;
    }

    /**
     * Exception thrown when condition evaluation fails.
     */
    public static class ConditionEvaluationException extends RuntimeException {
        public ConditionEvaluationException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
