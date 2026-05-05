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

import io.casehub.api.model.CaseStatus;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.time.Instant;
import java.util.UUID;

/**
 * Case instance response with status and metadata.
 *
 * @param caseId unique case instance identifier
 * @param status current case status (RUNNING, WAITING, SUSPENDED, COMPLETED, FAULTED, CANCELLED)
 * @param namespace case definition namespace
 * @param name case definition name
 * @param version case definition version
 * @param createdAt case instance creation timestamp
 * @param updatedAt last update timestamp
 */
public record CaseInstanceResponse(
    @NotNull UUID caseId,
    @NotNull CaseStatus status,
    @NotBlank String namespace,
    @NotBlank String name,
    @NotBlank String version,
    @NotNull Instant createdAt,
    @NotNull Instant updatedAt) {}
