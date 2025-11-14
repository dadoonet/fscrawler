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

package fr.pilato.elasticsearch.crawler.fs.framework;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Represents a single Access Control List entry for a file.
 */
public class FileAcl {

    private String principal;
    private String type;
    private List<String> permissions;
    private List<String> flags;

    public FileAcl() {
        // Default constructor needed for serialization
    }

    public FileAcl(String principal, String type, List<String> permissions, List<String> flags) {
        this.principal = principal;
        this.type = type;
        this.permissions = permissions != null ? new ArrayList<>(permissions) : null;
        this.flags = flags != null ? new ArrayList<>(flags) : null;
    }

    public String getPrincipal() {
        return principal;
    }

    public void setPrincipal(String principal) {
        this.principal = principal;
    }

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    public List<String> getPermissions() {
        return permissions != null ? Collections.unmodifiableList(permissions) : null;
    }

    public void setPermissions(List<String> permissions) {
        this.permissions = permissions != null ? new ArrayList<>(permissions) : null;
    }

    public List<String> getFlags() {
        return flags != null ? Collections.unmodifiableList(flags) : null;
    }

    public void setFlags(List<String> flags) {
        this.flags = flags != null ? new ArrayList<>(flags) : null;
    }
}
