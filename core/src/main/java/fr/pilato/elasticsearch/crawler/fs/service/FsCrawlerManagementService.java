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

package fr.pilato.elasticsearch.crawler.fs.service;

import fr.pilato.elasticsearch.crawler.fs.beans.Folder;

import java.io.IOException;
import java.util.Collection;

public interface FsCrawlerManagementService extends FsCrawlerService {

    /**
     * Retrieve the list of files that are currently available within a dir
     * @param path the virtual path
     * @return a list of known files
     * @throws Exception In case of problems
     */
    Collection<String> getFileDirectory(String path) throws Exception;

    /**
     * Retrieve the list of sub folders that are currently available within a dir
     * @param path the virtual path
     * @return a list of known folders
     * @throws Exception In case of problems
     */
    Collection<String> getFolderDirectory(String path) throws Exception;

    /**
     * Store a visited directory. It will be used to compare old dirs vs
     * current directories. So we will be able to remove data if needed.
     * @param indexFolder index name to store this information
     * @param id id of the directory
     * @param folder Folder to store
     */
    void storeVisitedDirectory(String indexFolder, String id, Folder folder);


    /**
     * Remove a document from the target service
     * @param index     Index name
     * @param id        Document ID
     */
    void delete(String index, String id);
}
