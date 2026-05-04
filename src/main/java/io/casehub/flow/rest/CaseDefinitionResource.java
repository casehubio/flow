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

import io.casehub.api.model.CaseDefinition;
import io.casehub.flow.service.CaseDefinitionService;
import io.smallrye.mutiny.Uni;
import org.jboss.resteasy.reactive.RestQuery;
import jakarta.inject.Inject;
import jakarta.ws.rs.DefaultValue;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.List;

/**
 * REST API for case definition discovery.
 *
 * <p>Provides read-only access to case definitions registered via CDI beans or YAML sources.
 *
 * <p>Endpoints:
 *
 * <ul>
 *   <li>GET /api/v1/case-definitions — list all definitions
 *   <li>GET /api/v1/case-definitions/{namespace}/{name} — all versions of a definition
 *   <li>GET /api/v1/case-definitions/{namespace}/{name}/{version} — specific version
 * </ul>
 *
 * <p>Error responses follow RFC 7807 Problem Details format.
 */
@Path("/api/v1/case-definitions")
@Produces(MediaType.APPLICATION_JSON)
public class CaseDefinitionResource {

  @Inject CaseDefinitionService caseDefinitionService;

  /**
   * List all registered case definitions with pagination.
   *
   * @param page page number (1-indexed, defaults to 1)
   * @param size page size (defaults to 20, max 100)
   * @return paginated case definitions with metadata
   */
  @GET
  public Uni<Response> listAll(
      @RestQuery @DefaultValue("1") Integer page, @RestQuery @DefaultValue("20") Integer size) {
    if (page < 1) {
      return Uni.createFrom()
          .item(
              Response.status(Response.Status.BAD_REQUEST)
                  .entity(
                      new ProblemDetail(
                          "Invalid page parameter",
                          400,
                          "Page number must be greater than or equal to 1"))
                  .build());
    }

    if (size < 1 || size > 100) {
      return Uni.createFrom()
          .item(
              Response.status(Response.Status.BAD_REQUEST)
                  .entity(
                      new ProblemDetail(
                          "Invalid size parameter",
                          400,
                          "Page size must be between 1 and 100"))
                  .build());
    }

    int pageIndex = page - 1;
    return caseDefinitionService.listAll(pageIndex, size).map(result -> Response.ok(result).build());
  }

  /**
   * Get all versions of a case definition by namespace and name.
   *
   * @param namespace the case namespace
   * @param name the case name
   * @return all versions matching the namespace and name
   */
  @GET
  @Path("/{namespace}/{name}")
  public Uni<Response> getByNamespaceAndName(
      @PathParam("namespace") String namespace, @PathParam("name") String name) {
    // JAX-RS should decode path params automatically, but ensure it's decoded
    String decodedName = URLDecoder.decode(name, StandardCharsets.UTF_8);
    String decodedNamespace = URLDecoder.decode(namespace, StandardCharsets.UTF_8);
    return caseDefinitionService
        .findByNamespaceAndName(decodedNamespace, decodedName)
        .map(
            definitions -> {
              if (definitions.isEmpty()) {
                return Response.status(Response.Status.NOT_FOUND)
                    .entity(
                        new ProblemDetail(
                            "Case definition not found",
                            404,
                            String.format(
                                "No case definition found for namespace '%s' and name '%s'",
                                namespace, name)))
                    .build();
              }
              return Response.ok(definitions).build();
            });
  }

  /**
   * Get a specific case definition by namespace, name, and version.
   *
   * @param namespace the case namespace
   * @param name the case name
   * @param version the case version
   * @return the matching case definition
   */
  @GET
  @Path("/{namespace}/{name}/{version}")
  public Uni<Response> getByNamespaceAndNameAndVersion(
      @PathParam("namespace") String namespace,
      @PathParam("name") String name,
      @PathParam("version") String version) {
    // JAX-RS should decode path params automatically, but ensure it's decoded
    String decodedName = URLDecoder.decode(name, StandardCharsets.UTF_8);
    String decodedNamespace = URLDecoder.decode(namespace, StandardCharsets.UTF_8);
    String decodedVersion = URLDecoder.decode(version, StandardCharsets.UTF_8);
    return caseDefinitionService
        .findByKey(decodedNamespace, decodedName, decodedVersion)
        .map(
            definition -> {
              if (definition == null) {
                return Response.status(Response.Status.NOT_FOUND)
                    .entity(
                        new ProblemDetail(
                            "Case definition not found",
                            404,
                            String.format(
                                "No case definition found for namespace '%s', name '%s', version '%s'",
                                namespace, name, version)))
                    .build();
              }
              return Response.ok(definition).build();
            });
  }

  /**
   * RFC 7807 Problem Detail for error responses.
   *
   * @param title short, human-readable summary
   * @param status HTTP status code
   * @param detail human-readable explanation specific to this occurrence
   */
  public record ProblemDetail(String title, int status, String detail) {}
}
