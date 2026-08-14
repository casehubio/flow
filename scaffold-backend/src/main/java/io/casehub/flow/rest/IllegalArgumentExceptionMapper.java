package io.casehub.flow.rest;

import io.casehub.engine.rest.dto.ProblemDetail;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.ext.ExceptionMapper;
import jakarta.ws.rs.ext.Provider;

@Provider
public class IllegalArgumentExceptionMapper implements ExceptionMapper<IllegalArgumentException> {

  @Override
  public Response toResponse(IllegalArgumentException exception) {
    return Response.status(400)
        .entity(new ProblemDetail("Invalid request", 400, exception.getMessage()))
        .build();
  }
}
