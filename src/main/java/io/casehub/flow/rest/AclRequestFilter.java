package io.casehub.flow.rest;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.casehub.engine.rest.dto.ProblemDetail;
import io.casehub.platform.api.acl.AccessControlProvider;
import io.casehub.platform.api.acl.AclAction;
import io.casehub.platform.api.acl.AclResourceType;
import io.casehub.platform.api.identity.CurrentPrincipal;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.container.ContainerRequestContext;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import java.io.ByteArrayInputStream;
import org.jboss.resteasy.reactive.server.ServerRequestFilter;

/**
 * Temporary ACL enforcement filter for engine-rest endpoints. Replaced when engine#768 moves ACL
 * into the engine-rest SPI. See scaffold#36.
 */
@ApplicationScoped
public class AclRequestFilter {

  @Inject AccessControlProvider acl;
  @Inject CurrentPrincipal currentPrincipal;
  @Inject ObjectMapper objectMapper;

  @ServerRequestFilter
  public Response filter(ContainerRequestContext ctx) {
    String path = ctx.getUriInfo().getPath();
    if (path.startsWith("/")) path = path.substring(1);

    if (!path.startsWith("api/v1/")) return null;

    var params = ctx.getUriInfo().getPathParameters();
    String caseId = params.getFirst("caseId");
    String namespace = params.getFirst("namespace");
    String name = params.getFirst("name");
    String method = ctx.getMethod();

    String resourceId;
    AclAction action;

    if (caseId != null) {
      resourceId = AclResourceType.CASE + ":" + caseId;
      if (path.endsWith("/cancel")) {
        action = AclAction.ADMIN;
      } else if ("GET".equals(method)) {
        action = AclAction.READ;
      } else {
        action = AclAction.WRITE;
      }
    } else if (namespace != null && name != null) {
      resourceId = AclResourceType.CASE_DEFINITION + ":" + namespace + "/" + name;
      action = AclAction.READ;
    } else if ("POST".equals(method) && path.equals("api/v1/cases")) {
      try {
        byte[] body = ctx.getEntityStream().readAllBytes();
        JsonNode node = objectMapper.readTree(body);
        JsonNode def = node.path("definition");
        String ns = def.path("namespace").asText(null);
        String nm = def.path("name").asText(null);
        ctx.setEntityStream(new ByteArrayInputStream(body));
        if (ns == null || nm == null) return null;
        resourceId = AclResourceType.CASE_DEFINITION + ":" + ns + "/" + nm;
        action = AclAction.WRITE;
      } catch (Exception e) {
        return null;
      }
    } else {
      return null;
    }

    boolean allowed = acl.canAccess(currentPrincipal.actorId(), resourceId, action);
    if (allowed) return null;
    return Response.status(403)
        .entity(new ProblemDetail("Access denied", 403, "Insufficient permissions"))
        .type(MediaType.APPLICATION_JSON_TYPE)
        .build();
  }
}
