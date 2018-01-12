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

package fr.pilato.elasticsearch.crawler.fs.fileabstractor;


import java.time.LocalDateTime;

public class FileAbstractModel {
    public String name;
    public boolean file;
    public boolean directory;
    public LocalDateTime lastModifiedDate;
    public LocalDateTime creationDate;
    public String path;
    public String fullpath;
    public long size;
    public String owner;
    public String group;
    public String extension;

    @Override
    public String toString() {
        String sb = "FileAbstractModel{" + "name='" + name + '\'' +
                ", file=" + file +
                ", directory=" + directory +
                ", lastModifiedDate=" + lastModifiedDate +
                ", creationDate=" + creationDate +
                ", path='" + path + '\'' +
                ", owner='" + owner + '\'' +
                ", group='" + group + '\'' +
                ", extension='" + extension + '\'' +
                ", fullpath='" + fullpath + '\'' +
                ", size=" + size +
                '}';
        return sb;
    }
}
