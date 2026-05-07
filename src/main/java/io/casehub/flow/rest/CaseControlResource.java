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
import io.casehub.flow.rest.dto.CaseControlRequest;
import io.casehub.flow.rest.dto.CaseControlResponse;
import io.casehub.flow.rest.dto.ProblemDetail;
import io.smallrye.mutiny.Uni;
import jakarta.inject.Inject;
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
 * REST API for case lifecycle control operations.
 *
 * <p>Endpoints:
 *
 * <ul>
 *   <li>POST /api/v1/cases/{caseId}/suspend — suspend case execution
 *   <li>POST /api/v1/cases/{caseId}/resume — resume suspended case
 *   <li>POST /api/v1/cases/{caseId}/cancel — cancel/terminate case
 * </ul>
 */
@Path("/api/v1/cases/{caseId}")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class CaseControlResource {

  private static final Logger LOG = Logger.getLogger(CaseControlResource.class);

  @Inject CaseHubRuntime caseHubRuntime;

  /**
   * Suspend case execution.
   *
   * @param caseId case instance UUID
   * @param request optional request with reason field
   * @return 202 Accepted with operation confirmation, 404 if case not found, 409 if invalid state
   */
  @POST
  @Path("suspend")
  public Uni<Response> suspend(
      @PathParam("caseId") UUID caseId, CaseControlRequest request) {
    return Uni.createFrom()
        .emitter(
            em -> {
              try {
                if (request != null && request.reason() != null) {
                  LOG.infof("Suspending case %s, reason: %s", caseId, request.reason());
                }
                caseHubRuntime.suspendCase(caseId);
                em.complete(
                    new CaseControlResponse(
                        caseId, "suspend", "accepted", "Case suspension queued for processing"));
              } catch (Exception e) {
                em.fail(e);
              }
            })
        .map(response -> Response.status(202).entity(response).build())
        .onFailure(IllegalArgumentException.class)
        .recoverWithItem(
            ex -> {
              LOG.warnf(ex, "Case not found: %s", caseId);
              return Response.status(404)
                  .entity(new ProblemDetail("Case not found", 404, ex.getMessage()))
                  .build();
            })
        .onFailure(IllegalStateException.class)
        .recoverWithItem(
            ex -> {
              LOG.warnf(ex, "Invalid state transition for case %s", caseId);
              return Response.status(409)
                  .entity(new ProblemDetail("Invalid state transition", 409, ex.getMessage()))
                  .build();
            })
        .onFailure()
        .recoverWithItem(
            ex -> {
              LOG.errorf(ex, "Failed to suspend case %s", caseId);
              return Response.status(500)
                  .entity(
                      new ProblemDetail(
                          "Internal server error",
                          500,
                          "Failed to suspend case: " + ex.getMessage()))
                  .build();
            });
  }
}
