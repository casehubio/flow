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
import io.casehub.platform.api.acl.AccessControlProvider;
import io.casehub.platform.api.acl.AccessDeniedException;
import io.casehub.platform.api.acl.AclAction;
import io.casehub.platform.api.acl.AclResourceType;
import io.casehub.platform.api.identity.CurrentPrincipal;
import io.smallrye.mutiny.Uni;
import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.media.Content;
import org.eclipse.microprofile.openapi.annotations.media.Schema;
import org.eclipse.microprofile.openapi.annotations.parameters.Parameter;
import org.eclipse.microprofile.openapi.annotations.parameters.RequestBody;
import org.eclipse.microprofile.openapi.annotations.responses.APIResponse;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;
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
@Tag(name = "Signals", description = "Send signals to running cases")
public class SignalResource {

  private static final Logger LOG = Logger.getLogger(SignalResource.class);

  @Inject CaseHubRuntime caseHubRuntime;
  @Inject AccessControlProvider acl;
  @Inject CurrentPrincipal currentPrincipal;

  @POST
  @Operation(summary = "Send signal to a case",
             description = "Sends a signal value to a running case instance at the specified context path")
  @Parameter(name = "caseId", description = "Case instance UUID", required = true)
  @RequestBody(description = "Signal with context path and value",
               required = true,
               content = @Content(schema = @Schema(implementation = SendSignalRequest.class)))
  @APIResponse(responseCode = "202", description = "Signal accepted",
               content = @Content(schema = @Schema(implementation = SignalResponse.class)))
  @APIResponse(responseCode = "400", description = "Invalid request",
               content = @Content(schema = @Schema(implementation = ProblemDetail.class)))
  @APIResponse(responseCode = "404", description = "Case not found",
               content = @Content(schema = @Schema(implementation = ProblemDetail.class)))
  @APIResponse(responseCode = "500", description = "Internal server error",
               content = @Content(schema = @Schema(implementation = ProblemDetail.class)))
  public Uni<Response> sendSignal(
      @PathParam("caseId") UUID caseId, @Valid SendSignalRequest request) {

    String resourceId = AclResourceType.CASE + ":" + caseId;

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

    return Uni.createFrom().completionStage(acl.canAccess(currentPrincipal.actorId(), resourceId, AclAction.WRITE))
        .flatMap(allowed -> {
          if (!allowed) throw new AccessDeniedException(currentPrincipal.actorId(), resourceId, AclAction.WRITE);
          return Uni.createFrom().completionStage(() -> caseHubRuntime.query(caseId, ".", Object.class));
        })
        .map(
            ignored -> {
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
        .onFailure(ex -> ex instanceof RuntimeException && !(ex instanceof AccessDeniedException))
        .recoverWithUni(
            ex -> {
              if (ex.getMessage() != null && ex.getMessage().contains("not found")) {
                LOG.warnf(ex, "Case not found: %s", caseId);
                return Uni.createFrom()
                    .item(
                        Response.status(404)
                            .entity(
                                new ProblemDetail(
                                    "Case not found",
                                    404,
                                    ex.getMessage()))
                            .build());
              }
              LOG.errorf(
                  ex, "Failed to send signal to case %s at path %s", caseId, request.path());
              return Uni.createFrom()
                  .item(
                      Response.status(500)
                          .entity(
                              new ProblemDetail(
                                  "Internal server error",
                                  500,
                                  "Failed to send signal: " + ex.getMessage()))
                          .build());
            });
  }
}
