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

import fr.pilato.elasticsearch.crawler.fs.FsParser;
import fr.pilato.elasticsearch.crawler.fs.beans.FsCrawlerCheckpoint;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.IOException;
import java.time.Duration;
import java.time.LocalDateTime;

/**
 * REST API for controlling the crawler (pause, resume, status)
 */
@Path("/_crawler")
public class CrawlerApi extends RestApi {
    private static final Logger logger = LogManager.getLogger();

    private final FsParser fsParser;
    private final String jobName;

    public CrawlerApi(FsParser fsParser, String jobName) {
        this.fsParser = fsParser;
        this.jobName = jobName;
    }

    /**
     * Pause the crawler. The current progress will be saved to a checkpoint file.
     * @return response indicating success or failure
     */
    @POST
    @Path("/pause")
    @Produces(MediaType.APPLICATION_JSON)
    public Response pause() {
        logger.info("REST request to pause crawler");
        
        if (fsParser.isClosed()) {
            return Response.status(Response.Status.BAD_REQUEST)
                    .entity(new SimpleResponse(false, "Crawler is not running"))
                    .build();
        }
        
        if (fsParser.isPaused()) {
            return Response.status(Response.Status.BAD_REQUEST)
                    .entity(new SimpleResponse(false, "Crawler is already paused"))
                    .build();
        }
        
        fsParser.pause();
        return Response.ok(new SimpleResponse(true, "Crawler paused. Checkpoint saved.")).build();
    }

    /**
     * Resume a paused crawler.
     * @return response indicating success or failure
     */
    @POST
    @Path("/resume")
    @Produces(MediaType.APPLICATION_JSON)
    public Response resume() {
        logger.info("REST request to resume crawler");
        
        if (fsParser.isClosed()) {
            return Response.status(Response.Status.BAD_REQUEST)
                    .entity(new SimpleResponse(false, "Crawler is not running"))
                    .build();
        }
        
        if (!fsParser.isPaused()) {
            return Response.status(Response.Status.BAD_REQUEST)
                    .entity(new SimpleResponse(false, "Crawler is not paused"))
                    .build();
        }
        
        fsParser.resume();
        return Response.ok(new SimpleResponse(true, "Crawler resumed")).build();
    }

    /**
     * Get the current status of the crawler including checkpoint information.
     * @return the crawler status
     */
    @GET
    @Path("/status")
    @Produces(MediaType.APPLICATION_JSON)
    public CrawlerStatusResponse getStatus() {
        FsCrawlerCheckpoint checkpoint = fsParser.getCheckpoint();
        if (checkpoint != null) {
            CrawlerStatusResponse response = new CrawlerStatusResponse(checkpoint);
            response.setState(fsParser.getState());
            return response;
        } else {
            if (fsParser.getCheckpointHandler() != null) {
                logger.debug("No checkpoint in memory, but we have a checkpoint handler. This might indicate that the " +
                        "crawler is not running or just started. Trying to load checkpoint from file...");
                // Try to load checkpoint from file if parser doesn't have one in memory
                try {
                    FsCrawlerCheckpoint savedCheckpoint = fsParser.getCheckpointHandler().read(jobName);
                    if (savedCheckpoint != null) {
                        return new CrawlerStatusResponse(savedCheckpoint);
                    }
                } catch (IOException e) {
                    logger.debug("No saved checkpoint found: {}", e.getMessage());
                }
            } else {
                logger.debug("No checkpoint in memory and no checkpoint handler. This probably means there's no active " +
                        "crawler. Did you start with --loop 0?");
            }
        }

        logger.warn("Failed to get the checkpoint status");
        CrawlerStatusResponse response = new CrawlerStatusResponse();
        response.setOk(false);
        response.setMessage("Failed to get the checkpoint status");
        return response;
    }

    /**
     * Clear the checkpoint file. This is useful to force a fresh start.
     * @return response indicating success or failure
     */
    @DELETE
    @Path("/checkpoint")
    @Produces(MediaType.APPLICATION_JSON)
    public Response clearCheckpoint() {
        logger.info("REST request to clear checkpoint");
        
        if (!fsParser.isClosed() && !fsParser.isPaused()) {
            return Response.status(Response.Status.BAD_REQUEST)
                    .entity(new SimpleResponse(false, "Cannot clear checkpoint while crawler is running. Pause or stop it first."))
                    .build();
        }
        
        try {
            if (fsParser.getCheckpointHandler() != null) {
                fsParser.getCheckpointHandler().clean(jobName);
                return Response.ok(new SimpleResponse(true, "Checkpoint cleared")).build();
            }
            return Response.status(Response.Status.NOT_FOUND)
                    .entity(new SimpleResponse(false,
                            "Failed to clear checkpoint as we don't have a checkpoint handler. " +
                                    "This probably means there's no active crawler. " +
                                    "Did you start with --loop 0?"))
                    .build();
        } catch (IOException e) {
            logger.warn("Failed to clear checkpoint: {}", e.getMessage());
            return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                    .entity(new SimpleResponse(false, "Failed to clear checkpoint: " + e.getMessage()))
                    .build();
        }
    }
}
