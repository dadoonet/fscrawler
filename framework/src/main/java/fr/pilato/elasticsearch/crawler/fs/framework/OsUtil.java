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

package fr.pilato.elasticsearch.crawler.fs.framework;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.lang.management.ManagementFactory;
import java.lang.management.OperatingSystemMXBean;
import java.lang.reflect.Method;

public class OsUtil {
    private static final Logger logger = LogManager.getLogger(OsUtil.class);

    private static final OperatingSystemMXBean osMxBean = ManagementFactory.getOperatingSystemMXBean();

    private static final Method getFreePhysicalMemorySize;
    private static final Method getTotalPhysicalMemorySize;
    private static final Method getFreeSwapSpaceSize;
    private static final Method getTotalSwapSpaceSize;

    static {
        getFreePhysicalMemorySize = getMethod("getFreePhysicalMemorySize");
        getTotalPhysicalMemorySize = getMethod("getTotalPhysicalMemorySize");
        getFreeSwapSpaceSize = getMethod("getFreeSwapSpaceSize");
        getTotalSwapSpaceSize = getMethod("getTotalSwapSpaceSize");
    }

    /**
     * Returns the amount of free physical memory in bytes.
     */
    public static long getFreePhysicalMemorySize() {
        if (getFreePhysicalMemorySize == null) {
            logger.warn("getFreePhysicalMemorySize is not available");
            return 0;
        }
        try {
            final long freeMem = (long) getFreePhysicalMemorySize.invoke(osMxBean);
            if (freeMem < 0) {
                logger.warn("OS reported a negative free memory value [{}]", freeMem);
                return 0;
            }
            return freeMem;
        } catch (Exception e) {
            logger.warn("exception retrieving free physical memory", e);
            return 0;
        }
    }

    /**
     * Returns the total amount of physical memory in bytes.
     */
    public static long getTotalPhysicalMemorySize() {
        if (getTotalPhysicalMemorySize == null) {
            logger.warn("getTotalPhysicalMemorySize is not available");
            return 0;
        }
        try {
            final long totalMem = (long) getTotalPhysicalMemorySize.invoke(osMxBean);
            if (totalMem < 0) {
                logger.warn("OS reported a negative total memory value [{}]", totalMem);
                return 0;
            }
            return totalMem;
        } catch (Exception e) {
            logger.warn("exception retrieving total physical memory", e);
            return 0;
        }
    }

    /**
     * Returns the amount of free swap space in bytes.
     */
    public static long getFreeSwapSpaceSize() {
        if (getFreeSwapSpaceSize == null) {
            return -1;
        }
        try {
            return (long) getFreeSwapSpaceSize.invoke(osMxBean);
        } catch (Exception e) {
            return -1;
        }
    }

    /**
     * Returns the total amount of swap space in bytes.
     */
    public static long getTotalSwapSpaceSize() {
        if (getTotalSwapSpaceSize == null) {
            return -1;
        }
        try {
            return (long) getTotalSwapSpaceSize.invoke(osMxBean);
        } catch (Exception e) {
            return -1;
        }
    }

    /**
     * Returns a given method of the OperatingSystemMXBean, or null if the method is not found or unavailable.
     */
    private static Method getMethod(String methodName) {
        try {
            return Class.forName("com.sun.management.OperatingSystemMXBean").getMethod(methodName);
        } catch (Exception e) {
            // not available
            return null;
        }
    }
}
