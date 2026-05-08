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

import io.casehub.flow.rest.dto.ProblemDetail;
import io.casehub.flow.service.EventLogService;
import io.smallrye.mutiny.Uni;
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
 * <p>Endpoints:
 *
 * <ul>
 *   <li>GET /api/v1/cases/{caseId}/events — get paginated event log
 * </ul>
 */
@Path("/api/v1/cases/{caseId}/events")
@Produces(MediaType.APPLICATION_JSON)
public class EventLogResource {

  private static final Logger LOG = Logger.getLogger(EventLogResource.class);

  @Inject EventLogService eventLogService;

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
  public Uni<Response> getEventLog(
      @PathParam("caseId") UUID caseId,
      @QueryParam("page") @DefaultValue("1") int page,
      @QueryParam("size") @DefaultValue("50") int size,
      @QueryParam("eventType") List<String> eventType,
      @QueryParam("streamType") List<String> streamType) {

    return eventLogService
        .getEventLog(caseId, page, size, eventType, streamType)
        .map(pagedResponse -> Response.ok(pagedResponse).build())
        .onFailure(IllegalArgumentException.class)
        .recoverWithItem(
            ex -> {
              LOG.warnf(ex, "Case not found: %s", caseId);
              return Response.status(404)
                  .entity(
                      new ProblemDetail(
                          "Case instance not found", 404, ex.getMessage()))
                  .build();
            })
        .onFailure()
        .recoverWithItem(
            ex -> {
              LOG.errorf(ex, "Failed to retrieve event log for case %s", caseId);
              return Response.status(500)
                  .entity(
                      new ProblemDetail(
                          "Internal server error", 500, ex.getMessage()))
                  .build();
            });
  }
}
