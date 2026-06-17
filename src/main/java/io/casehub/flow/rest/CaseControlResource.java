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
import org.eclipse.microprofile.openapi.annotations.responses.APIResponse;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;
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
@Tag(name = "Case Control", description = "Case lifecycle operations (suspend, resume, cancel)")
public class CaseControlResource {

  private static final Logger LOG = Logger.getLogger(CaseControlResource.class);

  @Inject CaseHubRuntime caseHubRuntime;
  @Inject AccessControlProvider acl;
  @Inject CurrentPrincipal currentPrincipal;

  /**
   * Suspend case execution.
   *
   * @param caseId case instance UUID
   * @param request optional request with reason field
   * @return 202 Accepted with operation confirmation, 404 if case not found, 409 if invalid state
   */
  @POST
  @Path("suspend")
  @Operation(summary = "Suspend a running case",
             description = "Queues a case suspension for async processing")
  @Parameter(name = "caseId", description = "Case instance UUID", required = true)
  @APIResponse(responseCode = "202", description = "Suspension queued",
               content = @Content(schema = @Schema(implementation = CaseControlResponse.class)))
  @APIResponse(responseCode = "404", description = "Case not found",
               content = @Content(schema = @Schema(implementation = ProblemDetail.class)))
  @APIResponse(responseCode = "409", description = "Invalid state transition",
               content = @Content(schema = @Schema(implementation = ProblemDetail.class)))
  @APIResponse(responseCode = "500", description = "Internal server error",
               content = @Content(schema = @Schema(implementation = ProblemDetail.class)))
  public Uni<Response> suspend(
      @PathParam("caseId") UUID caseId, CaseControlRequest request) {
    String resourceId = AclResourceType.CASE + ":" + caseId;
    if (!acl.canAccess(currentPrincipal.actorId(), resourceId, AclAction.WRITE)) {
      throw new AccessDeniedException(currentPrincipal.actorId(), resourceId, AclAction.WRITE);
    }

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

  /**
   * Resume suspended case execution.
   *
   * @param caseId case instance UUID
   * @param request optional request with reason field
   * @return 202 Accepted with operation confirmation, 404 if case not found, 409 if invalid state
   */
  @POST
  @Path("resume")
  @Operation(summary = "Resume a suspended case",
             description = "Queues a case resumption for async processing")
  @Parameter(name = "caseId", description = "Case instance UUID", required = true)
  @APIResponse(responseCode = "202", description = "Resumption queued",
               content = @Content(schema = @Schema(implementation = CaseControlResponse.class)))
  @APIResponse(responseCode = "404", description = "Case not found",
               content = @Content(schema = @Schema(implementation = ProblemDetail.class)))
  @APIResponse(responseCode = "409", description = "Invalid state transition",
               content = @Content(schema = @Schema(implementation = ProblemDetail.class)))
  @APIResponse(responseCode = "500", description = "Internal server error",
               content = @Content(schema = @Schema(implementation = ProblemDetail.class)))
  public Uni<Response> resume(@PathParam("caseId") UUID caseId, CaseControlRequest request) {
    String resourceId = AclResourceType.CASE + ":" + caseId;
    if (!acl.canAccess(currentPrincipal.actorId(), resourceId, AclAction.WRITE)) {
      throw new AccessDeniedException(currentPrincipal.actorId(), resourceId, AclAction.WRITE);
    }

    return Uni.createFrom()
        .emitter(
            em -> {
              try {
                if (request != null && request.reason() != null) {
                  LOG.infof("Resuming case %s, reason: %s", caseId, request.reason());
                }
                caseHubRuntime.resumeCase(caseId);
                em.complete(
                    new CaseControlResponse(
                        caseId, "resume", "accepted", "Case resumption queued for processing"));
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
              LOG.errorf(ex, "Failed to resume case %s", caseId);
              return Response.status(500)
                  .entity(
                      new ProblemDetail(
                          "Internal server error",
                          500,
                          "Failed to resume case: " + ex.getMessage()))
                  .build();
            });
  }

  /**
   * Cancel case execution.
   *
   * @param caseId case instance UUID
   * @param request optional request with reason field
   * @return 202 Accepted with operation confirmation, 404 if case not found, 409 if invalid state
   */
  @POST
  @Path("cancel")
  @Operation(summary = "Cancel a running case",
             description = "Queues a case cancellation for async processing")
  @Parameter(name = "caseId", description = "Case instance UUID", required = true)
  @APIResponse(responseCode = "202", description = "Cancellation queued",
               content = @Content(schema = @Schema(implementation = CaseControlResponse.class)))
  @APIResponse(responseCode = "404", description = "Case not found",
               content = @Content(schema = @Schema(implementation = ProblemDetail.class)))
  @APIResponse(responseCode = "409", description = "Invalid state transition",
               content = @Content(schema = @Schema(implementation = ProblemDetail.class)))
  @APIResponse(responseCode = "500", description = "Internal server error",
               content = @Content(schema = @Schema(implementation = ProblemDetail.class)))
  public Uni<Response> cancel(@PathParam("caseId") UUID caseId, CaseControlRequest request) {
    String resourceId = AclResourceType.CASE + ":" + caseId;
    if (!acl.canAccess(currentPrincipal.actorId(), resourceId, AclAction.ADMIN)) {
      throw new AccessDeniedException(currentPrincipal.actorId(), resourceId, AclAction.ADMIN);
    }

    return Uni.createFrom()
        .emitter(
            em -> {
              try {
                if (request != null && request.reason() != null) {
                  LOG.infof("Cancelling case %s, reason: %s", caseId, request.reason());
                }
                caseHubRuntime.cancelCase(caseId);
                em.complete(
                    new CaseControlResponse(
                        caseId, "cancel", "accepted", "Case cancellation queued for processing"));
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
              LOG.errorf(ex, "Failed to cancel case %s", caseId);
              return Response.status(500)
                  .entity(
                      new ProblemDetail(
                          "Internal server error",
                          500,
                          "Failed to cancel case: " + ex.getMessage()))
                  .build();
            });
  }
}
