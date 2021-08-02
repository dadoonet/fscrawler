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

package fr.pilato.elasticsearch.crawler.fs.crawler.ftp;

import static fr.pilato.elasticsearch.crawler.fs.framework.FsCrawlerUtil.toOctalPermission;

import org.apache.commons.net.ftp.FTPFile;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class FTPUtils {
  private static final Logger logger = LogManager.getLogger(FTPUtils.class);

  /**
   * Determines FTPFile permissions.
   */
  public static int getFilePermissions(final FTPFile file) {
    try {
      int user = toOctalPermission(
          file.hasPermission(FTPFile.USER_ACCESS, FTPFile.READ_PERMISSION),
          file.hasPermission(FTPFile.USER_ACCESS, FTPFile.WRITE_PERMISSION),
          file.hasPermission(FTPFile.USER_ACCESS, FTPFile.EXECUTE_PERMISSION));
      int group = toOctalPermission(
          file.hasPermission(FTPFile.GROUP_ACCESS, FTPFile.READ_PERMISSION),
          file.hasPermission(FTPFile.GROUP_ACCESS, FTPFile.WRITE_PERMISSION),
          file.hasPermission(FTPFile.GROUP_ACCESS, FTPFile.EXECUTE_PERMISSION));
      int others = toOctalPermission(
          file.hasPermission(FTPFile.WORLD_ACCESS, FTPFile.READ_PERMISSION),
          file.hasPermission(FTPFile.WORLD_ACCESS, FTPFile.WRITE_PERMISSION),
          file.hasPermission(FTPFile.WORLD_ACCESS, FTPFile.EXECUTE_PERMISSION));

      return user * 100 + group * 10 + others;
    } catch (Exception e) {
      logger.warn("Failed to determine 'permissions' of {}: {}", file, e.getMessage());
      return -1;
    }
  }

}
