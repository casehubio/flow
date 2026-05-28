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
import io.casehub.api.model.event.CaseEventLogRecord;
import io.casehub.api.model.event.CaseHubEventType;
import io.casehub.api.model.event.EventStreamType;
import io.casehub.engine.common.spi.CaseInstanceRepository;
import io.casehub.flow.exception.CaseInstanceNotFoundException;
import io.casehub.flow.rest.dto.EventLogEntryResponse;
import io.casehub.flow.rest.dto.PagedResponse;
import io.smallrye.mutiny.Uni;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletionStage;
import java.util.stream.Collectors;

/**
 * Service for event log operations.
 *
 * <p>Provides business logic for retrieving, filtering, and paginating case event logs.
 */
@ApplicationScoped
public class EventLogService {

  @Inject CaseHubRuntime caseHubRuntime;
  @Inject CaseInstanceRepository instanceRepository;

  /**
   * Get paginated and filtered event log for a case.
   *
   * @param caseId case instance UUID
   * @param page page number (1-indexed)
   * @param size page size
   * @param eventTypeNames optional event type filter
   * @param streamTypeNames optional stream type filter
   * @return paged response with event log entries
   */
  public Uni<PagedResponse<EventLogEntryResponse>> getEventLog(
      UUID caseId,
      int page,
      int size,
      List<String> eventTypeNames,
      List<String> streamTypeNames) {

    // Validate case exists first
    return instanceRepository
        .findByUuid(caseId)
        .onItem()
        .ifNull()
        .failWith(() -> new CaseInstanceNotFoundException(caseId))
        .flatMap(
            instance -> {
              // Convert filter strings to enums
              Set<CaseHubEventType> eventTypes = convertEventTypes(eventTypeNames);
              Set<EventStreamType> streamTypes = convertStreamTypes(streamTypeNames);

              // Choose appropriate runtime method based on filters
              CompletionStage<List<CaseEventLogRecord>> eventLogFuture;
              if (!eventTypes.isEmpty() && !streamTypes.isEmpty()) {
                eventLogFuture = caseHubRuntime.eventLog(caseId, eventTypes, streamTypes);
              } else if (!eventTypes.isEmpty()) {
                eventLogFuture = caseHubRuntime.eventLog(caseId, eventTypes);
              } else if (!streamTypes.isEmpty()) {
                // Engine supports streamType in combination with eventType, but not alone
                // Fetch all and filter in-memory
                eventLogFuture =
                    caseHubRuntime
                        .eventLog(caseId)
                        .thenApply(events -> filterByStreamType(events, streamTypes));
              } else {
                eventLogFuture = caseHubRuntime.eventLog(caseId);
              }

              return Uni.createFrom()
                  .completionStage(eventLogFuture)
                  .map(events -> buildPagedResponse(events, page, size));
            });
  }

  /**
   * Build paged response from event list.
   *
   * @param allEvents all events (after filtering)
   * @param page page number (1-indexed)
   * @param size page size
   * @return paged response
   */
  private PagedResponse<EventLogEntryResponse> buildPagedResponse(
      List<CaseEventLogRecord> allEvents, int page, int size) {

    int totalElements = allEvents.size();
    int totalPages = (totalElements + size - 1) / size;

    // Calculate pagination bounds
    int offset = (page - 1) * size;
    int endIndex = Math.min(offset + size, totalElements);

    // Handle out-of-bounds page (return empty)
    List<EventLogEntryResponse> content;
    if (offset >= totalElements) {
      content = List.of();
    } else {
      content = allEvents.subList(offset, endIndex).stream()
          .map(this::toEventLogEntryResponse)
          .toList();
    }

    return new PagedResponse<>(content, page, size, totalElements, totalPages);
  }

  /**
   * Map CaseEventLogRecord to EventLogEntryResponse.
   *
   * @param record event log record from engine
   * @return response DTO
   */
  private EventLogEntryResponse toEventLogEntryResponse(CaseEventLogRecord record) {
    return new EventLogEntryResponse(
        record.eventType(),
        record.streamType(),
        record.timestamp(),
        record.payload(),
        record.metadata());
  }

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

  /**
   * Filter event log records by stream type.
   *
   * @param events list of event log records
   * @param streamTypes set of stream types to filter by
   * @return filtered list of events
   */
  private List<CaseEventLogRecord> filterByStreamType(
      List<CaseEventLogRecord> events, Set<EventStreamType> streamTypes) {
    return events.stream()
        .filter(event -> streamTypes.contains(event.streamType()))
        .toList();
  }
}
