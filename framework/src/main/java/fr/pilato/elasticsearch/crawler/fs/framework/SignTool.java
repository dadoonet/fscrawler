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

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

/**
 * Utility class to sign *things*
 *
 * @author David Pilato (aka dadoonet)
 */
public class SignTool {

    public static String sign(String toSign) throws NoSuchAlgorithmException {

        MessageDigest md = MessageDigest.getInstance("MD5");
        md.update(toSign.getBytes());

        StringBuilder key = new StringBuilder();
        byte[] b = md.digest();
        for (byte aB : b) {
            long t = aB < 0 ? 256 + aB : aB;
            key.append(Long.toHexString(t));
        }

        return key.toString();
    }

}
