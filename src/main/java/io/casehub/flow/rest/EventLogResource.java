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
import io.casehub.flow.exception.ValidationException;
import io.casehub.flow.rest.dto.PagedResponse;
import io.casehub.flow.rest.dto.ProblemDetail;
import io.casehub.flow.service.EventLogService;
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
import jakarta.ws.rs.DefaultValue;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import java.util.List;
import java.util.UUID;
import org.jboss.logging.Logger;

/**
 * REST API for case event log operations.
 *
 * <p>Provides access to immutable event logs for case instances, enabling:
 *
 * <ul>
 *   <li>Observability - track worker executions, state changes, signals
 *   <li>Debugging - inspect case execution history with timestamps and payloads
 *   <li>Compliance - audit trail for regulatory requirements
 * </ul>
 *
 * <p>Endpoints:
 *
 * <ul>
 *   <li>GET /api/v1/cases/{caseId}/events — get paginated and filtered event log
 * </ul>
 *
 * <p>Query Parameters:
 *
 * <ul>
 *   <li>page (int, default=1) - page number (1-indexed)
 *   <li>size (int, default=50, max=1000) - page size
 *   <li>eventType (String[], optional) - filter by event types (repeatable)
 *   <li>streamType (String[], optional) - filter by stream types (repeatable)
 * </ul>
 *
 * <p>Example:
 *
 * <pre>
 * GET /api/v1/cases/123e4567-e89b-12d3-a456-426614174000/events?page=1&size=20&eventType=WORKER_EXECUTION_COMPLETED
 * </pre>
 *
 * <p>Response: {@link PagedResponse} containing {@link EventLogEntryResponse} objects
 *
 * <p>Error Responses:
 *
 * <ul>
 *   <li>400 Bad Request - invalid pagination or filter parameters
 *   <li>404 Not Found - case does not exist
 *   <li>500 Internal Server Error - unexpected failure
 * </ul>
 */
@Path("/api/v1/cases/{caseId}/events")
@Produces(MediaType.APPLICATION_JSON)
@Tag(name = "Event Log", description = "Case event log and audit trail")
public class EventLogResource {

  private static final Logger LOG = Logger.getLogger(EventLogResource.class);
  private static final int MAX_PAGE_SIZE = 1000;

  @Inject EventLogService eventLogService;
  @Inject AccessControlProvider acl;
  @Inject CurrentPrincipal currentPrincipal;

  /**
   * Get event log for a case.
   *
   * @param caseId case instance UUID
   * @param page page number (1-indexed, default 1)
   * @param size page size (default 50)
   * @param eventType optional event type filter (repeatable)
   * @param streamType optional stream type filter (repeatable)
   * @return 200 OK with paged event log, 404 if case not found, 400 for invalid parameters
   */
  @GET
  @Operation(summary = "Get case event log",
             description = "Returns a paginated and filtered event log for a case instance")
  @Parameter(name = "caseId", description = "Case instance UUID", required = true)
  @Parameter(name = "page", description = "Page number (1-indexed)", example = "1")
  @Parameter(name = "size", description = "Page size (1-1000)", example = "50")
  @Parameter(name = "eventType", description = "Filter by event type (repeatable)",
             example = "CASE_STARTED")
  @Parameter(name = "streamType", description = "Filter by stream type (repeatable)",
             example = "CASE")
  @APIResponse(responseCode = "200", description = "Paginated event log",
               content = @Content(schema = @Schema(implementation = PagedResponse.class)))
  @APIResponse(responseCode = "400", description = "Invalid parameters",
               content = @Content(schema = @Schema(implementation = ProblemDetail.class)))
  @APIResponse(responseCode = "404", description = "Case instance not found",
               content = @Content(schema = @Schema(implementation = ProblemDetail.class)))
  @APIResponse(responseCode = "500", description = "Internal server error",
               content = @Content(schema = @Schema(implementation = ProblemDetail.class)))
  public Uni<Response> getEventLog(
      @PathParam("caseId") UUID caseId,
      @QueryParam("page") @DefaultValue("1") int page,
      @QueryParam("size") @DefaultValue("50") int size,
      @QueryParam("eventType") List<String> eventType,
      @QueryParam("streamType") List<String> streamType) {

    String resourceId = AclResourceType.CASE + ":" + caseId;
    if (!acl.canAccess(currentPrincipal.actorId(), resourceId, AclAction.READ)) {
      throw new AccessDeniedException(currentPrincipal.actorId(), resourceId, AclAction.READ);
    }

    return Uni.createFrom()
        .item(
            () -> {
              // Validate parameters (throws ValidationException on failure)
              validatePaginationParams(page, size);
              eventLogService.convertEventTypes(eventType); // throws IllegalArgumentException
              eventLogService.convertStreamTypes(streamType); // throws IllegalArgumentException
              return true;
            })
        .flatMap(
            ignored ->
                eventLogService.getEventLog(caseId, page, size, eventType, streamType))
        .map(pagedResponse -> Response.ok(pagedResponse).build())
        .onFailure(ValidationException.class)
        .recoverWithItem(
            ex -> {
              LOG.warnf(ex, "Invalid pagination parameters");
              return Response.status(400)
                  .entity(
                      new ProblemDetail(
                          "Invalid pagination parameters", 400, ex.getMessage()))
                  .build();
            })
        .onFailure(IllegalArgumentException.class)
        .recoverWithItem(
            ex -> {
              // Enum validation errors
              LOG.warnf(ex, "Invalid enum filter parameter: %s", ex.getMessage());
              return Response.status(400)
                  .entity(
                      new ProblemDetail("Invalid filter parameter", 400, ex.getMessage()))
                  .build();
            })
        .onFailure(CaseInstanceNotFoundException.class)
        .recoverWithItem(
            ex -> {
              LOG.warnf(ex, "Case not found: %s", caseId);
              return Response.status(404)
                  .entity(
                      new ProblemDetail("Case instance not found", 404, ex.getMessage()))
                  .build();
            })
        .onFailure()
        .recoverWithItem(
            ex -> {
              LOG.errorf(ex, "Failed to retrieve event log for case %s", caseId);
              return Response.status(500)
                  .entity(
                      new ProblemDetail(
                          "Internal server error",
                          500,
                          "Failed to retrieve event log: " + ex.getMessage()))
                  .build();
            });
  }

  /**
   * Validate pagination parameters.
   *
   * @param page page number (must be >= 1)
   * @param size page size (must be between 1 and MAX_PAGE_SIZE)
   * @throws ValidationException if parameters are invalid
   */
  private void validatePaginationParams(int page, int size) {
    if (page < 1) {
      throw new ValidationException("Page must be >= 1, got: " + page);
    }
    if (size < 1 || size > MAX_PAGE_SIZE) {
      throw new ValidationException(
          "Size must be between 1 and " + MAX_PAGE_SIZE + ", got: " + size);
    }
  }
}
