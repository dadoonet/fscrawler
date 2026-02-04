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

/**
 * Interface for plugins that support conditional routing.
 * Used by FilterPlugin and OutputPlugin to determine if they should
 * process a specific document based on conditions.
 * 
 * Phase 1: Conditions are not evaluated (shouldApply always returns true).
 * Phase 2: MVEL expressions are used to evaluate conditions.
 */
public interface ConditionalPlugin {

    /**
     * Returns the MVEL condition expression for this plugin.
     * If null or empty, the plugin always applies.
     *
     * @return the condition expression, or null if no condition
     */
    String getWhen();

    /**
     * Sets the condition expression.
     *
     * @param when the MVEL condition expression
     */
    void setWhen(String when);

    /**
     * Determines if this plugin should be applied to the given document.
     * 
     * Phase 1 implementation: Always returns true (no conditions).
     * Phase 2 implementation: Evaluates the MVEL condition.
     *
     * @param context the document context containing tags, metadata, etc.
     * @return true if this plugin should process the document
     */
    default boolean shouldApply(PipelineContext context) {
        // Phase 1: No conditional routing, always apply
        return true;
    }
}
