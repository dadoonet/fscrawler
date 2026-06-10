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
import org.junit.jupiter.api.extension.ConditionEvaluationResult;
import org.junit.jupiter.api.extension.ExecutionCondition;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.testcontainers.DockerClientFactory;

@Target({ElementType.METHOD, ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
@ExtendWith(DisabledIfNoDocker.DockerCondition.class)
public @interface DisabledIfNoDocker {
    class DockerCondition implements ExecutionCondition {

        @Override
        public ConditionEvaluationResult evaluateExecutionCondition(ExtensionContext context) {
            try {
                if (DockerClientFactory.instance().isDockerAvailable()) {
                    return ConditionEvaluationResult.enabled("Docker is available");
                }
                return ConditionEvaluationResult.disabled("No Docker environment available");
            } catch (Exception e) {
                return ConditionEvaluationResult.disabled(
                        "No Docker environment available (Exception: " + e.getMessage() + ")");
            }
        }
    }
}
