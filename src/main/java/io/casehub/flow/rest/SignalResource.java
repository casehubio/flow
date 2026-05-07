/*
 * Copyright 2026-Present The Case Hub Authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.casehub.flow.rest;

import io.casehub.api.engine.CaseHubRuntime;
import io.casehub.flow.exception.CaseInstanceNotFoundException;
import io.casehub.flow.rest.dto.ProblemDetail;
import io.casehub.flow.rest.dto.SendSignalRequest;
import io.casehub.flow.rest.dto.SignalResponse;
import io.smallrye.mutiny.Uni;
import jakarta.inject.Inject;
import jakarta.validation.Valid;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import java.util.UUID;
import org.jboss.logging.Logger;

/**
 * REST API for sending signals to case instances.
 *
 * <p>Endpoints:
 *
 * <ul>
 *   <li>POST /api/v1/cases/{caseId}/signals — send signal to case
 * </ul>
 */
@Path("/api/v1/cases/{caseId}/signals")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class SignalResource {

  private static final Logger LOG = Logger.getLogger(SignalResource.class);

  @Inject CaseHubRuntime caseHubRuntime;

  @POST
  public Uni<Response> sendSignal(
      @PathParam("caseId") UUID caseId, @Valid SendSignalRequest request) {

    if (request == null) {
      return Uni.createFrom()
          .item(
              Response.status(400)
                  .entity(
                      new ProblemDetail(
                          "Invalid request",
                          400,
                          "Request body is required"))
                  .build());
    }

    // Send signal to engine
    return Uni.createFrom()
        .item(
            () -> {
              caseHubRuntime.signal(caseId, request.path(), request.value());
              return new SignalResponse(caseId, "accepted", "Signal queued for processing");
            })
        .map(response -> Response.status(202).entity(response).build())
        .onFailure(CaseInstanceNotFoundException.class)
        .recoverWithItem(
            ex -> {
              LOG.warnf(ex, "Case not found: %s", caseId);
              return Response.status(404)
                  .entity(new ProblemDetail("Case not found", 404, ex.getMessage()))
                  .build();
            })
        .onFailure()
        .recoverWithItem(
            ex -> {
              LOG.errorf(
                  ex, "Failed to send signal to case %s at path %s", caseId, request.path());
              return Response.status(500)
                  .entity(
                      new ProblemDetail(
                          "Internal server error",
                          500,
                          "Failed to send signal: " + ex.getMessage()))
                  .build();
            });
  }
}
