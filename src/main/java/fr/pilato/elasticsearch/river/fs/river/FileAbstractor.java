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

package fr.pilato.elasticsearch.river.fs.river;

import org.elasticsearch.common.logging.ESLogger;
import org.elasticsearch.common.logging.Loggers;

import java.io.File;
import java.io.InputStream;
import java.util.Collection;

public abstract class FileAbstractor<T> {
    protected ESLogger logger = Loggers.getLogger(this.getClass());

    public abstract FileAbstractModel toFileAbstractModel(String path, T file);
    public abstract InputStream getInputStream(FileAbstractModel file) throws Exception;
    public abstract Collection<FileAbstractModel> getFiles(String dir) throws Exception;

    public FileAbstractor(FsRiverFeedDefinition fsDef) {
        // Do nothing here
    }

    static public String computeFullPath(String parent, String filename) {
        return parent.concat(File.separator).concat(filename);
    }

}
