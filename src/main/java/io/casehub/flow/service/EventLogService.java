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

import io.casehub.api.model.event.CaseHubEventType;
import io.casehub.api.model.event.EventStreamType;
import jakarta.enterprise.context.ApplicationScoped;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Service for event log operations.
 *
 * <p>Provides business logic for retrieving, filtering, and paginating case event logs.
 */
@ApplicationScoped
public class EventLogService {

  /**
   * Convert string event type names to enum set.
   *
   * @param eventTypeNames list of event type names
   * @return set of CaseHubEventType enums
   * @throws IllegalArgumentException if any name is invalid
   */
  public Set<CaseHubEventType> convertEventTypes(List<String> eventTypeNames) {
    if (eventTypeNames == null || eventTypeNames.isEmpty()) {
      return Set.of();
    }
    return eventTypeNames.stream()
        .map(name -> CaseHubEventType.valueOf(name))
        .collect(Collectors.toSet());
  }

  /**
   * Convert string stream type names to enum set.
   *
   * @param streamTypeNames list of stream type names
   * @return set of EventStreamType enums
   * @throws IllegalArgumentException if any name is invalid
   */
  public Set<EventStreamType> convertStreamTypes(List<String> streamTypeNames) {
    if (streamTypeNames == null || streamTypeNames.isEmpty()) {
      return Set.of();
    }
    return streamTypeNames.stream()
        .map(name -> EventStreamType.valueOf(name))
        .collect(Collectors.toSet());
  }
}
