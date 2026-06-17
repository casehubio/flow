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

import io.casehub.flow.exception.CaseInstanceNotFoundException;
import io.casehub.flow.exception.DefinitionNotFoundException;
import io.casehub.flow.rest.dto.CaseInstanceResponse;
import io.casehub.flow.rest.dto.ProblemDetail;
import io.casehub.flow.rest.dto.StartCaseRequest;
import io.casehub.flow.service.CaseInstanceService;
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
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import java.util.UUID;

/**
 * REST API for case instance lifecycle management.
 *
 * <p>Endpoints:
 *
 * <ul>
 *   <li>POST /api/v1/cases — start case instance
 *   <li>GET /api/v1/cases/{caseId} — get case status
 *   <li>GET /api/v1/cases/{caseId}/context — get full context
 *   <li>GET /api/v1/cases/{caseId}/context/{path} — query context by path
 * </ul>
 */
@Path("/api/v1/cases")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
@Tag(name = "Case Instances", description = "Case instance lifecycle and context")
public class CaseInstanceResource {

  @Inject CaseInstanceService caseInstanceService;
  @Inject AccessControlProvider acl;
  @Inject CurrentPrincipal currentPrincipal;

  /**
   * Start a new case instance.
   *
   * @param request start case request with definition reference and initial context
   * @return 200 OK with case instance response, 404 if definition not found, 400 for invalid
   *     request
   */
  @POST
  @Operation(summary = "Start a new case instance",
             description = "Creates and starts a new case instance from a registered definition")
  @RequestBody(description = "Case start request with definition reference and optional context",
               required = true,
               content = @Content(schema = @Schema(implementation = StartCaseRequest.class)))
  @APIResponse(responseCode = "200", description = "Case instance started",
               content = @Content(schema = @Schema(implementation = CaseInstanceResponse.class)))
  @APIResponse(responseCode = "400", description = "Invalid request",
               content = @Content(schema = @Schema(implementation = ProblemDetail.class)))
  @APIResponse(responseCode = "404", description = "Case definition not found",
               content = @Content(schema = @Schema(implementation = ProblemDetail.class)))
  @APIResponse(responseCode = "500", description = "Internal server error",
               content = @Content(schema = @Schema(implementation = ProblemDetail.class)))
  public Uni<Response> startCase(StartCaseRequest request) {
    // Validation
    if (request == null || request.definition() == null) {
      return Uni.createFrom()
          .item(
              Response.status(400)
                  .entity(
                      new ProblemDetail(
                          "Invalid request", 400, "Request body and definition are required"))
                  .build());
    }

    String resourceId = AclResourceType.CASE_DEFINITION + ":"
        + request.definition().namespace() + "/" + request.definition().name();
    if (!acl.canAccess(currentPrincipal.actorId(), resourceId, AclAction.WRITE)) {
      throw new AccessDeniedException(currentPrincipal.actorId(), resourceId, AclAction.WRITE);
    }

    return caseInstanceService
        .startCase(request)
        .map(response -> Response.ok(response).build())
        .onFailure(DefinitionNotFoundException.class)
        .recoverWithItem(
            ex ->
                Response.status(404)
                    .entity(
                        new ProblemDetail(
                            "Case definition not found", 404, ex.getMessage()))
                    .build())
        .onFailure()
        .recoverWithItem(
            ex ->
                Response.status(500)
                    .entity(
                        new ProblemDetail(
                            "Internal server error",
                            500,
                            "Failed to start case instance: " + ex.getMessage()))
                    .build());
  }

  /**
   * Get a case instance by ID.
   *
   * @param caseId case instance UUID
   * @return 200 OK with case instance response, 404 if not found
   */
  @GET
  @Path("/{caseId}")
  @Operation(summary = "Get case instance by ID",
             description = "Returns the status and metadata of a case instance")
  @Parameter(name = "caseId", description = "Case instance UUID", required = true)
  @APIResponse(responseCode = "200", description = "Case instance found",
               content = @Content(schema = @Schema(implementation = CaseInstanceResponse.class)))
  @APIResponse(responseCode = "404", description = "Case instance not found",
               content = @Content(schema = @Schema(implementation = ProblemDetail.class)))
  @APIResponse(responseCode = "500", description = "Internal server error",
               content = @Content(schema = @Schema(implementation = ProblemDetail.class)))
  public Uni<Response> getCaseInstance(@PathParam("caseId") UUID caseId) {
    String resourceId = AclResourceType.CASE + ":" + caseId;
    if (!acl.canAccess(currentPrincipal.actorId(), resourceId, AclAction.READ)) {
      throw new AccessDeniedException(currentPrincipal.actorId(), resourceId, AclAction.READ);
    }

    return caseInstanceService
        .getCaseInstance(caseId)
        .map(response -> Response.ok(response).build())
        .onFailure(CaseInstanceNotFoundException.class)
        .recoverWithItem(
            ex ->
                Response.status(404)
                    .entity(
                        new ProblemDetail(
                            "Case instance not found", 404, ex.getMessage()))
                    .build())
        .onFailure()
        .recoverWithItem(
            ex ->
                Response.status(500)
                    .entity(
                        new ProblemDetail(
                            "Internal server error", 500, ex.getMessage()))
                    .build());
  }

  /**
   * Get full case context.
   *
   * @param caseId case instance UUID
   * @return 200 OK with context map, 404 if case not found
   */
  @GET
  @Path("/{caseId}/context")
  @Operation(summary = "Get full case context",
             description = "Returns the complete context data of a case instance")
  @Parameter(name = "caseId", description = "Case instance UUID", required = true)
  @APIResponse(responseCode = "200", description = "Case context data")
  @APIResponse(responseCode = "404", description = "Case instance not found",
               content = @Content(schema = @Schema(implementation = ProblemDetail.class)))
  @APIResponse(responseCode = "500", description = "Internal server error",
               content = @Content(schema = @Schema(implementation = ProblemDetail.class)))
  public Uni<Response> getContext(@PathParam("caseId") UUID caseId) {
    String resourceId = AclResourceType.CASE + ":" + caseId;
    if (!acl.canAccess(currentPrincipal.actorId(), resourceId, AclAction.READ)) {
      throw new AccessDeniedException(currentPrincipal.actorId(), resourceId, AclAction.READ);
    }

    return caseInstanceService
        .getCaseContext(caseId)
        .map(context -> Response.ok(context).build())
        .onFailure(CaseInstanceNotFoundException.class)
        .recoverWithItem(
            ex ->
                Response.status(404)
                    .entity(
                        new ProblemDetail(
                            "Case instance not found", 404, ex.getMessage()))
                    .build())
        .onFailure()
        .recoverWithItem(
            ex ->
                Response.status(500)
                    .entity(
                        new ProblemDetail(
                            "Internal server error", 500, ex.getMessage()))
                    .build());
  }

  /**
   * Get a specific path in case context.
   *
   * @param caseId case instance UUID
   * @param path dot-notation path to query
   * @return 200 OK with value at path, 404 if case not found
   */
  @GET
  @Path("/{caseId}/context/{path}")
  @Operation(summary = "Get case context by path",
             description = "Returns a specific value from the case context using dot-notation path")
  @Parameter(name = "caseId", description = "Case instance UUID", required = true)
  @Parameter(name = "path", description = "Dot-notation context path (e.g., customer.name)",
             required = true, example = "customer.name")
  @APIResponse(responseCode = "200", description = "Value at context path")
  @APIResponse(responseCode = "404", description = "Case instance not found",
               content = @Content(schema = @Schema(implementation = ProblemDetail.class)))
  @APIResponse(responseCode = "500", description = "Internal server error",
               content = @Content(schema = @Schema(implementation = ProblemDetail.class)))
  public Uni<Response> getContextPath(@PathParam("caseId") UUID caseId, @PathParam("path") String path) {
    String resourceId = AclResourceType.CASE + ":" + caseId;
    if (!acl.canAccess(currentPrincipal.actorId(), resourceId, AclAction.READ)) {
      throw new AccessDeniedException(currentPrincipal.actorId(), resourceId, AclAction.READ);
    }

    return caseInstanceService
        .getContextPath(caseId, path)
        .map(value -> Response.ok(value).build())
        .onFailure(CaseInstanceNotFoundException.class)
        .recoverWithItem(
            ex ->
                Response.status(404)
                    .entity(
                        new ProblemDetail(
                            "Case instance not found", 404, ex.getMessage()))
                    .build())
        .onFailure()
        .recoverWithItem(
            ex ->
                Response.status(500)
                    .entity(
                        new ProblemDetail(
                            "Internal server error", 500, ex.getMessage()))
                    .build());
  }
}
