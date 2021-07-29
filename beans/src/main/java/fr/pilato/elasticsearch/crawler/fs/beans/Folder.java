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

package fr.pilato.elasticsearch.crawler.fs.beans;

/**
 * Represents a folder we have visited
 */
public class Folder {

    public static final String CONTENT_TYPE = "text/directory";

    private Path path;
    private File file;

    public Folder() {
        path = new Path();
        file = new File();
        file.setContentType(CONTENT_TYPE);
    }

    /**
     * Build a folder with a single ctor call
     * @param name      The folder name
     * @param root      Root of the folder
     * @param real      The full path to the folder
     * @param virtual   The virtual path from the root
     */
    public Folder(String name, String root, String real, String virtual) {
        path = new Path();
        path.setRoot(root);
        path.setReal(real);
        path.setVirtual(virtual);
        file = new File();
        file.setFilename(name);
        file.setContentType(CONTENT_TYPE);
    }

    public Path getPath() {
        return path;
    }

    public void setPath(Path path) {
        this.path = path;
    }

    public File getFile() {
        return file;
    }

    public void setFile(File file) {
        this.file = file;
    }
}
