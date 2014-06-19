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

import org.elasticsearch.ElasticsearchException;
import org.elasticsearch.client.Client;
import org.elasticsearch.common.inject.Inject;
import org.elasticsearch.common.io.stream.BytesStreamOutput;
import org.elasticsearch.common.settings.Settings;
import org.elasticsearch.common.xcontent.XContentBuilder;
import org.elasticsearch.common.xcontent.XContentBuilderString;
import org.elasticsearch.common.xcontent.XContentFactory;
import org.elasticsearch.common.xcontent.XContentType;
import org.elasticsearch.rest.*;
import org.elasticsearch.rest.RestRequest.Method;

import java.io.IOException;

import static org.elasticsearch.common.xcontent.XContentFactory.jsonBuilder;
import static org.elasticsearch.ExceptionsHelper.detailedMessage;

public class ManageRiverAction extends BaseRestHandler {

    @Inject
    public ManageRiverAction(Settings settings, Client client, RestController controller) {
        super(settings, client);

        // Define _start / _stop REST Endpoints
        controller.registerHandler(Method.GET, "/_river/{rivername}/{command}", this);
    }

    @Override
    public void handleRequest(RestRequest request, RestChannel channel) {
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

            XContentBuilder builder = RestXContentBuilder.restContentBuilder(request);
            builder
                    .startObject()
                    .field(new XContentBuilderString("ok"), true)
                    .endObject();
            channel.sendResponse(new BytesRestResponse(RestStatus.OK, builder));
        } catch (IOException e) {
            onFailure(channel, request, e);
        }
    }

    protected void onFailure(RestChannel channel, RestRequest request,
                             IOException e) {
        try {
            channel.sendResponse(new XContentThrowableRestResponse(request, e));
        } catch (IOException e1) {
            logger.error("Failed to send failure response", e1);
        }
    }

  public static class RestXContentBuilder {

    public static XContentBuilder restContentBuilder(RestRequest request) throws IOException {
      XContentType contentType = XContentType.fromRestContentType(request.param("format", request.header("Content-Type")));
      if (contentType == null) {
        // default to JSON
        contentType = XContentType.JSON;
      }
      XContentBuilder builder = new XContentBuilder(XContentFactory.xContent(contentType),
      new BytesStreamOutput());
      if (request.paramAsBoolean("pretty", false)) {
        builder.prettyPrint().lfAtEnd();
      }
      String casing = request.param("case");
      if (casing != null && "camelCase".equals(casing)) {
        builder.fieldCaseConversion(XContentBuilder.FieldCaseConversion.CAMELCASE);
      } else {
        // we expect all REST interfaces to write results in underscore casing, so
        // no need for double casing
        builder.fieldCaseConversion(XContentBuilder.FieldCaseConversion.NONE);
      }
      return builder;
    }

    public static XContentBuilder emptyBuilder(RestRequest request) throws IOException {
      return restContentBuilder(request).startObject().endObject();
    }

  }

  public static class XContentThrowableRestResponse extends BytesRestResponse {

    public XContentThrowableRestResponse(RestRequest request, Throwable t) throws IOException {
      this(request, ((t instanceof ElasticsearchException) ? ((ElasticsearchException) t).status() : RestStatus.INTERNAL_SERVER_ERROR), t);
    }

    public XContentThrowableRestResponse(RestRequest request, RestStatus status, Throwable t) throws IOException {
      super(status, convert(request, status, t));
    }

    private static XContentBuilder convert(RestRequest request, RestStatus status, Throwable t) throws IOException {
      XContentBuilder builder = RestXContentBuilder.restContentBuilder(request).startObject()
          .field("error", detailedMessage(t))
          .field("status", status.getStatus());
      if (t != null && request.paramAsBoolean("error_trace", false)) {
        builder.startObject("error_trace");
        boolean first = true;
        while (t != null) {
          if (!first) {
            builder.startObject("cause");
          }
          buildThrowable(t, builder);
          if (!first) {
            builder.endObject();
          }
          t = t.getCause();
          first = false;
        }
        builder.endObject();
      }
      builder.endObject();
      return builder;
    }

    private static void buildThrowable(Throwable t, XContentBuilder builder) throws IOException {
      builder.field("message", t.getMessage());
      for (StackTraceElement stElement : t.getStackTrace()) {
        builder.startObject("at")
        .field("class", stElement.getClassName())
        .field("method", stElement.getMethodName());
        if (stElement.getFileName() != null) {
          builder.field("file", stElement.getFileName());
        }
        if (stElement.getLineNumber() >= 0) {
          builder.field("line", stElement.getLineNumber());
        }
        builder.endObject();
      }
    }
  }

}
