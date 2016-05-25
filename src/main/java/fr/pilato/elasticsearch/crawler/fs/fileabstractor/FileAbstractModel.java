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


import java.time.Instant;

public class FileAbstractModel {
    public String name;
    public boolean file;
    public boolean directory;
    public Instant lastModifiedDate;
    public Instant creationDate;
    public String path;
    public String fullpath;
    public long size;
    public String owner;
    public String group;

    @Override
    public String toString() {
        final StringBuilder sb = new StringBuilder("FileAbstractModel{");
        sb.append("name='").append(name).append('\'');
        sb.append(", file=").append(file);
        sb.append(", directory=").append(directory);
        sb.append(", lastModifiedDate=").append(lastModifiedDate);
        sb.append(", creationDate=").append(creationDate);
        sb.append(", path='").append(path).append('\'');
        sb.append(", owner='").append(owner).append('\'');
        sb.append(", group='").append(group).append('\'');
        sb.append(", fullpath='").append(fullpath).append('\'');
        sb.append(", size=").append(size);
        sb.append('}');
        return sb.toString();
    }
}
