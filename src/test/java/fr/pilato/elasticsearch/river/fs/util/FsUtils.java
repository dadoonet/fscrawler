/*
 * Licensed to Elasticsearch under one or more contributor
 * license agreements. See the NOTICE file distributed with
 * this work for additional information regarding copyright
 * ownership. Elasticsearch licenses this file to you under
 * the Apache License, Version 2.0 (the "License"); you may
 * not use this file except in compliance with the License.
 * You may obtain a copy of the License at
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

package fr.pilato.elasticsearch.river.fs.util;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.channels.FileChannel;

public class FsUtils {
    public static void copyFile(File sourceFile, File destinationFile) throws IOException {
        FileInputStream sourceIs = null;
        FileChannel source = null;
        FileOutputStream destinationOs = null;
        FileChannel destination = null;
        try {
            sourceIs = new FileInputStream(sourceFile);
            source = sourceIs.getChannel();
            destinationOs = new FileOutputStream(destinationFile);
            destination = destinationOs.getChannel();
            destination.transferFrom(source, 0, source.size());
        } finally {
            if (source != null) {
                source.close();
            }
            if (sourceIs != null) {
                sourceIs.close();
            }
            if (destination != null) {
                destination.close();
            }
            if (destinationOs != null) {
                destinationOs.close();
            }
        }
    }
}
