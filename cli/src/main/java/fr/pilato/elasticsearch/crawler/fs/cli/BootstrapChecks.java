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

package fr.pilato.elasticsearch.crawler.fs.cli;

import fr.pilato.elasticsearch.crawler.fs.framework.ByteSizeValue;
import fr.pilato.elasticsearch.crawler.fs.framework.OsUtil;
import fr.pilato.elasticsearch.crawler.fs.framework.Percentage;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;

/**
 * This class checks at startup if everything is correctly set.
 * We are using a class for it, so it's easier to track checks within a single place
 */
public class BootstrapChecks {

    private static final Logger logger = LogManager.getLogger(BootstrapChecks.class);

    public static void check() {
        checkJvm();
        checkUTF8();
    }

    private static void checkJvm() {
        ByteSizeValue swapTotalSize = new ByteSizeValue(OsUtil.getTotalSwapSpaceSize());
        ByteSizeValue swapFreeSize = new ByteSizeValue(OsUtil.getFreeSwapSpaceSize());
        ByteSizeValue ramTotalSize = new ByteSizeValue(OsUtil.getTotalPhysicalMemorySize());
        ByteSizeValue ramFreeSize = new ByteSizeValue(OsUtil.getFreePhysicalMemorySize());
        ByteSizeValue heapTotalSize = new ByteSizeValue(Runtime.getRuntime().maxMemory());
        ByteSizeValue heapFreeSize = new ByteSizeValue(Runtime.getRuntime().freeMemory());

        logger.info("Memory [Free/Total=Percent]: HEAP [{}/{}={}], RAM [{}/{}={}], Swap [{}/{}={}].",
                heapFreeSize, heapTotalSize, computePercentage(heapFreeSize, heapTotalSize),
                ramFreeSize, ramTotalSize, computePercentage(ramFreeSize, ramTotalSize),
                swapFreeSize, swapTotalSize, computePercentage(swapFreeSize, swapTotalSize));
    }

    static Percentage computePercentage(ByteSizeValue current, ByteSizeValue max) {
        if (max.getBytes() <= 0) {
            return new Percentage();
        }
        return new Percentage((BigDecimal.valueOf(((double) current.getBytes()) / (double) max.getBytes() * 100)).setScale(2, RoundingMode.HALF_EVEN).doubleValue(), true);
    }

    private static void checkUTF8() {
        String encoding = System.getProperty("file.encoding");
        if (!encoding.equals(StandardCharsets.UTF_8.name())) {
            logger.warn("[file.encoding] should be [{}] but is [{}]", StandardCharsets.UTF_8.name(), encoding);
        }
    }

}
