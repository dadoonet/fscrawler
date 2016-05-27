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

package fr.pilato.elasticsearch.crawler.fs.fileabstractor;

import fr.pilato.elasticsearch.crawler.fs.meta.settings.FsSettings;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.InputStream;
import java.util.Collection;

public abstract class FileAbstractor<T> {
    protected static final Logger logger = LogManager.getLogger(FileAbstractor.class);

    protected final FsSettings fsSettings;

    public abstract FileAbstractModel toFileAbstractModel(String path, T file);

    public abstract InputStream getInputStream(FileAbstractModel file) throws Exception;

    public abstract Collection<FileAbstractModel> getFiles(String dir) throws Exception;

    public abstract boolean exists(String dir) throws Exception;

    public abstract void open() throws Exception;

    public abstract void close() throws Exception;

    public FileAbstractor(FsSettings fsSettings) {
        this.fsSettings = fsSettings;
    }
}
