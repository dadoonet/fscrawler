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

package fr.pilato.elasticsearch.crawler.fs.rest;


import fr.pilato.elasticsearch.crawler.fs.client.ElasticsearchClientException;
import fr.pilato.elasticsearch.crawler.fs.framework.Version;
import fr.pilato.elasticsearch.crawler.fs.service.FsCrawlerManagementService;
import fr.pilato.elasticsearch.crawler.fs.settings.FsSettings;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;

import java.io.IOException;

/**
 * Root resource (exposed at "/" path)
 */
@Path("/")
public class ServerStatusApi extends RestApi {

    private final FsCrawlerManagementService managementService;
    private final FsSettings settings;

    ServerStatusApi(FsCrawlerManagementService managementService, FsSettings settings) {
        this.managementService = managementService;
        this.settings = settings;
    }

    @GET
    @Produces(MediaType.APPLICATION_JSON)
    public ServerStatusResponse getStatus() throws IOException, ElasticsearchClientException {
        ServerStatusResponse status = new ServerStatusResponse();
        status.setVersion(Version.getVersion());
        status.setElasticsearch(managementService.getVersion());
        status.setOk(true);
        status.setSettings(settings);
        return status;
    }

}
