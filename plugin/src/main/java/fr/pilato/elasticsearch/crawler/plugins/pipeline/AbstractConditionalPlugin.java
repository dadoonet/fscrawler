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

import fr.pilato.elasticsearch.crawler.plugins.AbstractPlugin;

import java.util.Map;

/**
 * Abstract base class for plugins that support conditional execution.
 * Adds the "when" condition field used by filter and output plugins.
 */
public abstract class AbstractConditionalPlugin extends AbstractPlugin {

    protected String when;

    /**
     * Returns the condition expression for this plugin.
     * @return the MVEL expression or null if always executed
     */
    public String getWhen() {
        return when;
    }

    /**
     * Sets the condition expression for this plugin.
     * @param when the MVEL expression
     */
    public void setWhen(String when) {
        this.when = when;
    }

    @Override
    protected void configureCommon(Map<String, Object> config) {
        super.configureCommon(config);
        this.when = getConfigValue(config, "when", String.class, null);
    }
}
