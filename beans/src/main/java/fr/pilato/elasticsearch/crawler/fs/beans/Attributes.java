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

import fr.pilato.elasticsearch.crawler.fs.framework.FileAcl;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Represents additional file attributes.
 */
public class Attributes {

    private String owner;
    private String group;
    private int permissions;
    private List<FileAcl> acl;

    public String getOwner() {
        return owner;
    }

    public void setOwner(String owner) {
        this.owner = owner;
    }

    public String getGroup() {
        return group;
    }

    public void setGroup(String group) {
        this.group = group;
    }

    public int getPermissions() {
        return permissions;
    }

    public void setPermissions(int permissions) {
        this.permissions = permissions;
    }

    public List<FileAcl> getAcl() {
        return acl != null ? Collections.unmodifiableList(acl) : null;
    }

    public void setAcl(List<FileAcl> acl) {
        if (acl == null || acl.isEmpty()) {
            this.acl = null;
        } else {
            this.acl = new ArrayList<>(acl);
        }
    }
}
