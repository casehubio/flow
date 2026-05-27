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
package io.casehub.flow.service;

import io.casehub.api.engine.CaseHubRuntime;
import io.casehub.engine.common.internal.model.CaseInstance;
import io.casehub.engine.common.internal.model.CaseMetaModel;
import io.casehub.engine.common.spi.CaseInstanceRepository;
import io.casehub.flow.exception.CaseHubNotFoundException;
import io.casehub.flow.exception.CaseInstanceNotFoundException;
import io.casehub.flow.exception.DefinitionNotFoundException;
import io.casehub.flow.rest.dto.CaseInstanceResponse;
import io.casehub.flow.rest.dto.StartCaseRequest;
import io.smallrye.mutiny.Uni;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Service for managing case instance lifecycle.
 *
 * <p>Provides operations for:
 *
 * <ul>
 *   <li>Starting new case instances from definitions (CDI, classpath, YAML)
 *   <li>Querying case instance status and metadata
 *   <li>Retrieving case context data
 * </ul>
 */
@ApplicationScoped
public class CaseInstanceService {

  @Inject CaseDefinitionService definitionService;
  @Inject CaseInstanceRepository instanceRepository;
  @Inject CaseHubRuntime caseHubRuntime;

  /**
   * Start a new case instance.
   *
   * @param request start case request with definition reference and initial context
   * @return case instance response with status and metadata
   */
  public Uni<CaseInstanceResponse> startCase(StartCaseRequest request) {
    String namespace = request.definition().namespace();
    String name = request.definition().name();
    String version = request.definition().version();
    Map<String, Object> context = request.context() != null ? request.context() : Map.of();

    // 1. Validate definition exists
    return definitionService
        .findByKey(namespace, name, version)
        .onItem()
        .ifNull()
        .failWith(() -> new DefinitionNotFoundException(namespace, name, version))

        // 2. Find CaseHub (should exist for registered definition)
        .flatMap(def -> definitionService.findCaseHub(namespace, name, version))
        .onItem()
        .ifNull()
        .failWith(() -> new CaseHubNotFoundException(namespace, name, version))
        .map(Optional::orElseThrow)

        // 3. Start case via CaseHub
        .flatMap(hub -> Uni.createFrom().completionStage(() -> hub.startCase(context)))
        .onItem()
        .ifNull()
        .failWith(() -> new CaseInstanceNotFoundException("Case started but UUID is null"))

        // 4. Fetch CaseInstance from repository
        .flatMap(caseId -> instanceRepository.findByUuid(caseId))
        .onItem()
        .ifNull()
        .failWith(
            () ->
                new CaseInstanceNotFoundException(
                    "Case started but not found in repository"))

        // 5. Map to response DTO
        .map(this::toCaseInstanceResponse);
  }

  /**
   * Get a case instance by ID.
   *
   * @param caseId case instance UUID
   * @return case instance response with status and metadata
   */
  public Uni<CaseInstanceResponse> getCaseInstance(UUID caseId) {
    return instanceRepository
        .findByUuid(caseId)
        .onItem()
        .ifNull()
        .failWith(() -> new CaseInstanceNotFoundException(caseId))
        .map(this::toCaseInstanceResponse);
  }

  /**
   * Get full case context.
   *
   * @param caseId case instance UUID
   * @return case context data as map
   */
  @SuppressWarnings("unchecked")
  public Uni<Map<String, Object>> getCaseContext(UUID caseId) {
    return Uni.createFrom()
        .completionStage(() -> caseHubRuntime.query(caseId, ".", Map.class))
        .map(map -> (Map<String, Object>) map)
        .onItem()
        .ifNull()
        .failWith(() -> new CaseInstanceNotFoundException(caseId))
        .onFailure(RuntimeException.class)
        .recoverWithUni(
            ex -> {
              if (ex.getMessage() != null && ex.getMessage().contains("not found")) {
                return Uni.createFrom()
                    .failure(new CaseInstanceNotFoundException(caseId));
              }
              return Uni.createFrom().failure(ex);
            });
  }

  /**
   * Get a specific path in case context.
   *
   * @param caseId case instance UUID
   * @param path dot-notation path to query (e.g., "customer.name")
   * @return value at path, or null if path doesn't exist
   */
  public Uni<Object> getContextPath(UUID caseId, String path) {
    return Uni.createFrom()
        .completionStage(() -> caseHubRuntime.query(caseId, path, Object.class))
        .onFailure(RuntimeException.class)
        .recoverWithUni(
            ex -> {
              if (ex.getMessage() != null && ex.getMessage().contains("not found")) {
                return Uni.createFrom()
                    .failure(new CaseInstanceNotFoundException(caseId));
              }
              return Uni.createFrom().failure(ex);
            });
  }

  private CaseInstanceResponse toCaseInstanceResponse(CaseInstance instance) {
    CaseMetaModel meta = instance.getCaseMetaModel();
    return new CaseInstanceResponse(
        instance.getUuid(),
        instance.getState(),
        meta.getNamespace(),
        meta.getName(),
        meta.getVersion(),
        meta.getCreatedAt(),
        meta.getCreatedAt()); // Using createdAt for updatedAt since no separate updatedAt field
  }
}
