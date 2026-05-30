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
package io.casehub.flow.rest.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.util.Map;
import org.eclipse.microprofile.openapi.annotations.media.Schema;

/**
 * Request to start a new case instance.
 *
 * @param definition case definition reference (namespace, name, version)
 * @param context initial case context data (optional, defaults to empty map).
 *                Acceptable value types: String, Number, Boolean, null, Map, List.
 */
@Schema(description = "Request to start a new case instance")
public record StartCaseRequest(
    @Schema(description = "Case definition reference", required = true)
    @NotNull @Valid CaseDefinitionRef definition,
    @Schema(description = "Initial case context data", nullable = true,
            example = "{\"customer\": {\"name\": \"John\"}}")
    Map<String, Object> context) {

  public StartCaseRequest {
    context = context == null ? Map.of() : Map.copyOf(context);
  }

  /**
   * Case definition reference.
   *
   * @param namespace case namespace
   * @param name case name
   * @param version case version
   */
  @Schema(description = "Reference to a registered case definition")
  public record CaseDefinitionRef(
      @Schema(description = "Case namespace", required = true, example = "acme")
      @NotBlank String namespace,
      @Schema(description = "Case name", required = true, example = "Order Processing")
      @NotBlank String name,
      @Schema(description = "Case version", required = true, example = "1.0.0")
      @NotBlank String version) {}
}
