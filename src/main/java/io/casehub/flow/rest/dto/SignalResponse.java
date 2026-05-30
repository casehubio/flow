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

import java.util.UUID;
import org.eclipse.microprofile.openapi.annotations.media.Schema;

/**
 * Response for signal acceptance.
 *
 * @param caseId case instance UUID
 * @param status acceptance status ("accepted")
 * @param message human-readable message
 */
@Schema(description = "Response after sending a signal")
public record SignalResponse(
    @Schema(description = "Case instance UUID",
            example = "550e8400-e29b-41d4-a716-446655440000")
    UUID caseId,
    @Schema(description = "Signal delivery status", example = "accepted")
    String status,
    @Schema(description = "Human-readable status message",
            example = "Signal delivered to case")
    String message) {}
