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

import io.casehub.flow.rest.dto.SendSignalRequest;
import io.smallrye.mutiny.Uni;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import java.util.UUID;

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

  @POST
  public Uni<Response> sendSignal(
      @PathParam("caseId") UUID caseId, SendSignalRequest request) {

    if (request == null || request.path() == null || request.value() == null) {
      return Uni.createFrom()
          .item(
              Response.status(400)
                  .entity(
                      new ProblemDetail(
                          "Invalid request",
                          400,
                          "Request body, path, and value are required"))
                  .build());
    }

    return Uni.createFrom().item(Response.status(202).build());
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
