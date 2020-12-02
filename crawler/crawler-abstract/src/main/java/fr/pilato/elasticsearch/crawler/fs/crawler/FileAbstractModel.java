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

package fr.pilato.elasticsearch.crawler.fs.crawler;


import java.time.LocalDateTime;

public class FileAbstractModel {
    private final String name;
    private final boolean file;
    private final boolean directory;
    private final LocalDateTime lastModifiedDate;
    private final LocalDateTime creationDate;
    private final LocalDateTime accessDate;
    private final String path;
    private final String fullpath;
    private final long size;
    private final String owner;
    private final String group;
    private final int permissions;
    private final String extension;

    public FileAbstractModel(String name, boolean file, LocalDateTime lastModifiedDate, LocalDateTime creationDate, LocalDateTime accessDate,
                             String extension, String path, String fullpath, long size, String owner, String group, int permissions) {
        this.name = name;
        this.file = file;
        this.directory = !file;
        this.lastModifiedDate = lastModifiedDate;
        this.creationDate = creationDate;
        this.accessDate = accessDate;
        this.path = path;
        this.fullpath = fullpath;
        this.size = size;
        this.owner = owner;
        this.group = group;
        this.permissions = permissions;
        this.extension = extension;
    }

    public String getName() {
        return name;
    }

    public boolean isFile() {
        return file;
    }

    public boolean isDirectory() {
        return directory;
    }

    public LocalDateTime getLastModifiedDate() {
        return lastModifiedDate;
    }

    public LocalDateTime getCreationDate() {
        return creationDate;
    }

    public LocalDateTime getAccessDate() {
        return accessDate;
    }

    public String getPath() {
        return path;
    }

    public String getFullpath() {
        return fullpath;
    }

    public long getSize() {
        return size;
    }

    public String getOwner() {
        return owner;
    }

    public String getGroup() {
        return group;
    }

    public int getPermissions() {
        return permissions;
    }

    public String getExtension() {
        return extension;
    }

    @Override
    public String toString() {
        return "FileAbstractModel{" + "name='" + name + '\'' +
                ", file=" + file +
                ", directory=" + directory +
                ", lastModifiedDate=" + lastModifiedDate +
                ", creationDate=" + creationDate +
                ", accessDate=" + accessDate +
                ", path='" + path + '\'' +
                ", owner='" + owner + '\'' +
                ", group='" + group + '\'' +
                ", permissions=" + permissions +
                ", extension='" + extension + '\'' +
                ", fullpath='" + fullpath + '\'' +
                ", size=" + size +
                '}';
    }
}
