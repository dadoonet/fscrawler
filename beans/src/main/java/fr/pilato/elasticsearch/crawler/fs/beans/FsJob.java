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

import java.time.LocalDateTime;
import java.util.Objects;

/**
 * Define a FS Job meta data
 */
public class FsJob {

    private String name;
    private LocalDateTime lastrun;
    private long indexed;
    private long deleted;

    public static class Builder {
        private String name;
        private LocalDateTime lastrun;
        private long indexed = 0;
        private long deleted = 0;

        public Builder setName(String name) {
            this.name = name;
            return this;
        }

        public Builder setLastrun(LocalDateTime lastrun) {
            this.lastrun = lastrun;
            return this;
        }

        public Builder setIndexed(long indexed) {
            this.indexed = indexed;
            return this;
        }

        public Builder setDeleted(long deleted) {
            this.deleted = deleted;
            return this;
        }

        public FsJob build() {
            return new FsJob(name, lastrun, indexed, deleted);
        }
    }

    public static Builder builder() {
        return new Builder();
    }

    public FsJob() {

    }

    public FsJob(String name, LocalDateTime lastrun, long indexed, long deleted) {
        this.name = name;
        this.lastrun = lastrun;
        this.indexed = indexed;
        this.deleted = deleted;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public LocalDateTime getLastrun() {
        return lastrun;
    }

    public void setLastrun(LocalDateTime lastrun) {
        this.lastrun = lastrun;
    }

    public long getIndexed() {
        return indexed;
    }

    public void setIndexed(long indexed) {
        this.indexed = indexed;
    }

    public long getDeleted() {
        return deleted;
    }

    public void setDeleted(long deleted) {
        this.deleted = deleted;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        FsJob fsJob = (FsJob) o;

        if (indexed != fsJob.indexed) return false;
        if (deleted != fsJob.deleted) return false;
        if (!Objects.equals(name, fsJob.name)) return false;
        return Objects.equals(lastrun, fsJob.lastrun);

    }

    @Override
    public int hashCode() {
        int result = name != null ? name.hashCode() : 0;
        result = 31 * result + (lastrun != null ? lastrun.hashCode() : 0);
        result = 31 * result + (int) (indexed ^ (indexed >>> 32));
        result = 31 * result + (int) (deleted ^ (deleted >>> 32));
        return result;
    }
}
