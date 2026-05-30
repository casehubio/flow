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

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import org.eclipse.microprofile.openapi.annotations.media.Schema;

/**
 * Request payload for sending signal to case instance.
 *
 * @param path dot-notation path in CaseContext (e.g., "approvals.user", "orders[0].status")
 * @param value signal data to set at path. Acceptable value types: String, Number, Boolean,
 *     null, Map, List.
 */
@Schema(description = "Request to send a signal to a running case")
public record SendSignalRequest(
    @Schema(description = "Dot-notation context path", required = true,
            example = "approvals.manager")
    @NotBlank String path,
    @Schema(description = "Signal value (String, Number, Boolean, Map, or List)",
            required = true, example = "approved")
    @NotNull Object value) {}
