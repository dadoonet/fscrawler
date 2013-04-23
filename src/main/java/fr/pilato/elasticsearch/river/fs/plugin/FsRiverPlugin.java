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

package fr.pilato.elasticsearch.river.fs.plugin;

import fr.pilato.elasticsearch.river.fs.rest.ManageRiverAction;
import fr.pilato.elasticsearch.river.fs.river.FsRiverModule;
import org.elasticsearch.common.inject.Inject;
import org.elasticsearch.common.inject.Module;
import org.elasticsearch.plugins.AbstractPlugin;
import org.elasticsearch.rest.RestModule;
import org.elasticsearch.river.RiversModule;

/**
 * @author dadoonet (David Pilato)
 */
public class FsRiverPlugin extends AbstractPlugin {

    @Inject public FsRiverPlugin() {
    }

    @Override public String name() {
        return "river-fs";
    }

    @Override public String description() {
        return "River FileSystem Plugin";
    }

    @Override public void processModule(Module module) {
        if (module instanceof RiversModule) {
            ((RiversModule) module).registerRiver("fs", FsRiverModule.class);
        }


        if (module instanceof RestModule) {
            ((RestModule) module).addRestAction(ManageRiverAction.class);
        }
    }
}
