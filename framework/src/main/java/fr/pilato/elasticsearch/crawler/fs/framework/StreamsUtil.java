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

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

public class StreamsUtil {
    private static final int BUF_SIZE = 0x1000; // 4K

    /**
     * Copies all bytes from the input stream to the output stream.
     * Does not close or flush either stream.
     *
     * @param from the input stream to read from
     * @param to the output stream to write to
     * @return the number of bytes copied
     * @throws IOException if an I/O error occurs
     */
    public static long copy(InputStream from, OutputStream to)
            throws IOException {
        assert from != null;
        assert to != null;
        byte[] buf = new byte[BUF_SIZE];
        long total = 0;
        while (true) {
            int r = from.read(buf);
            if (r == -1) {
                break;
            }
            to.write(buf, 0, r);
            total += r;
        }
        return total;
    }
}
