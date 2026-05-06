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
import io.casehub.flow.rest.dto.StartCaseRequest;
import io.casehub.flow.service.CaseInstanceService;
import io.smallrye.mutiny.Uni;
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
public class CaseInstanceResource {

  @Inject CaseInstanceService caseInstanceService;

  /**
   * Start a new case instance.
   *
   * @param request start case request with definition reference and initial context
   * @return 200 OK with case instance response, 404 if definition not found, 400 for invalid
   *     request
   */
  @POST
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
  public Uni<Response> getCaseInstance(@PathParam("caseId") UUID caseId) {
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
  public Uni<Response> getContext(@PathParam("caseId") UUID caseId) {
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
  public Uni<Response> getContextPath(@PathParam("caseId") UUID caseId, @PathParam("path") String path) {
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

  /**
   * RFC 7807 Problem Details for HTTP APIs.
   *
   * @param title a short, human-readable summary of the problem type
   * @param status the HTTP status code
   * @param detail a human-readable explanation specific to this occurrence
   */
  public record ProblemDetail(String title, int status, String detail) {}
}
