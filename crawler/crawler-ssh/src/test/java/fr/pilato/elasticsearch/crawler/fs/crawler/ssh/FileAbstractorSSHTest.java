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

package fr.pilato.elasticsearch.crawler.fs.crawler.ssh;

import fr.pilato.elasticsearch.crawler.fs.crawler.FileAbstractModel;
import fr.pilato.elasticsearch.crawler.fs.settings.FsSettings;
import fr.pilato.elasticsearch.crawler.fs.settings.Server;
import fr.pilato.elasticsearch.crawler.fs.test.framework.AbstractFSCrawlerTestCase;
import org.junit.Ignore;
import org.junit.Test;

import java.util.Collection;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;

public class FileAbstractorSSHTest extends AbstractFSCrawlerTestCase {

    @Test @Ignore
    public void testConnectToWindows() throws Exception {
        String path = "/E:/test";
        String host = "192.168.1.103";
        String user = "user";
        String pass = "password";
        FsSettings fsSettings = FsSettings.builder("foo")
                .setServer(
                        Server.builder()
                                .setHostname(host)
                                .setUsername(user)
                                .setPassword(pass)
                                .build()
                )
                .build();
        FileAbstractorSSH ssh = new FileAbstractorSSH(fsSettings);
        ssh.open();
        boolean exists = ssh.exists(path);
        assertThat(exists, is(true));
        Collection<FileAbstractModel> files = ssh.getFiles(path);
        logger.debug("Found {} files", files.size());
        for (FileAbstractModel file : files) {
            logger.debug(" - {}", file);
        }
        ssh.close();
    }

}
