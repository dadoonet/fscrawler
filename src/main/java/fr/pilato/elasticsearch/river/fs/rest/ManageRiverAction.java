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

package fr.pilato.elasticsearch.river.fs.rest;

import org.elasticsearch.client.Client;
import org.elasticsearch.common.inject.Inject;
import org.elasticsearch.common.settings.Settings;
import org.elasticsearch.common.xcontent.XContentBuilder;
import org.elasticsearch.common.xcontent.XContentBuilderString;
import org.elasticsearch.rest.*;
import org.elasticsearch.rest.RestRequest.Method;

import java.io.IOException;

import static org.elasticsearch.common.xcontent.XContentFactory.jsonBuilder;

public class ManageRiverAction extends BaseRestHandler {

    @Inject
    public ManageRiverAction(Settings settings, Client client, RestController controller) {
        super(settings, client);

        // Define _start / _stop REST Endpoints
        controller.registerHandler(Method.GET, "/_river/{rivername}/{command}", this);
    }

    @Override
    public void handleRequest(RestRequest request, RestChannel channel) throws Exception {
        if (logger.isDebugEnabled()) logger.debug("REST ManageRiverAction called");

        String rivername = request.param("rivername");
        String command = request.param("command");

        try {
            String status = "STARTED";
            if ("_stop".equals(command)) status = "STOPPED";

            XContentBuilder xb = jsonBuilder()
                    .startObject()
                    .startObject("fs")
                    .field("status", status)
                    .endObject()
                    .endObject();
            client.prepareIndex("_river", rivername, "_fsstatus").setSource(xb).execute().actionGet();

            XContentBuilder builder = jsonBuilder();
            builder
                    .startObject()
                    .field(new XContentBuilderString("ok"), true)
                    .endObject();
            channel.sendResponse(new BytesRestResponse(RestStatus.OK, builder));
        } catch (IOException e) {
            try {
                channel.sendResponse(new BytesRestResponse(channel, e));
            } catch (IOException e1) {
                channel.sendResponse(new BytesRestResponse(RestStatus.INTERNAL_SERVER_ERROR));
            }
        }
    }
}
