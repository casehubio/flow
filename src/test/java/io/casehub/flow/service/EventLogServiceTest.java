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

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.casehub.api.engine.CaseHubRuntime;
import io.casehub.api.model.event.CaseEventLogRecord;
import io.casehub.api.model.event.CaseHubEventType;
import io.casehub.api.model.event.EventStreamType;
import io.casehub.flow.rest.dto.EventLogEntryResponse;
import io.casehub.flow.rest.dto.PagedResponse;

import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class EventLogServiceTest {

    private CaseHubRuntime runtime;
    private EventLogService service;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @BeforeEach
    void setUp() {
        runtime = mock(CaseHubRuntime.class);
        service = new EventLogService();
        service.caseHubRuntime = runtime;
    }

    @Test
    void convertEventTypes_withValidValues_returnsEnumSet() {
        List<String> input = List.of("WORKER_EXECUTION_COMPLETED", "CASE_STATUS_CHANGED");

        Set<CaseHubEventType> result = service.convertEventTypes(input);

        assertThat(result)
                .containsExactlyInAnyOrder(
                        CaseHubEventType.WORKER_EXECUTION_COMPLETED,
                        CaseHubEventType.CASE_STATUS_CHANGED);
    }

    @Test
    void convertEventTypes_withInvalidValue_throwsIllegalArgumentException() {
        List<String> input = List.of("INVALID_EVENT_TYPE");

        assertThatThrownBy(() -> service.convertEventTypes(input))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("INVALID_EVENT_TYPE");
    }

    @Test
    void convertStreamTypes_withValidValues_returnsEnumSet() {
        List<String> input = List.of("CASE", "WORKER");

        Set<EventStreamType> result = service.convertStreamTypes(input);

        assertThat(result)
                .containsExactlyInAnyOrder(
                        EventStreamType.CASE,
                        EventStreamType.WORKER);
    }

    @Test
    void convertStreamTypes_withInvalidValue_throwsIllegalArgumentException() {
        List<String> input = List.of("INVALID_STREAM");

        assertThatThrownBy(() -> service.convertStreamTypes(input))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("INVALID_STREAM");
    }

    @Test
    void convertEventTypes_withNullOrEmpty_returnsEmptySet() {
        assertThat(service.convertEventTypes(null)).isEmpty();
        assertThat(service.convertEventTypes(List.of())).isEmpty();
    }

    @Test
    void convertStreamTypes_withNullOrEmpty_returnsEmptySet() {
        assertThat(service.convertStreamTypes(null)).isEmpty();
        assertThat(service.convertStreamTypes(List.of())).isEmpty();
    }

    @Test
    void getEventLog_noFiltersNoPagination_returnsAllEvents() {
        UUID caseId = UUID.randomUUID();
        Instant now = Instant.now();
        ObjectNode payload = objectMapper.createObjectNode().put("key", "value");
        ObjectNode metadata = objectMapper.createObjectNode().put("traceId", "abc");

        List<CaseEventLogRecord> records = List.of(
                new CaseEventLogRecord(
                        CaseHubEventType.WORKER_EXECUTION_COMPLETED,
                        EventStreamType.CASE,
                        now,
                        payload,
                        metadata));

        when(runtime.eventLog(caseId))
                .thenReturn(CompletableFuture.completedFuture(records));

        PagedResponse<EventLogEntryResponse> result =
                service.getEventLog(caseId, 1, 50, null, null).await().indefinitely();

        assertThat(result.content()).hasSize(1);
        assertThat(result.content().get(0).eventType())
                .isEqualTo(CaseHubEventType.WORKER_EXECUTION_COMPLETED);
        assertThat(result.content().get(0).streamType())
                .isEqualTo(EventStreamType.CASE);
        assertThat(result.content().get(0).timestamp()).isEqualTo(now);
        assertThat(result.content().get(0).payload()).isEqualTo(payload);
        assertThat(result.content().get(0).metadata()).isEqualTo(metadata);
        assertThat(result.page()).isEqualTo(1);
        assertThat(result.size()).isEqualTo(50);
        assertThat(result.totalElements()).isEqualTo(1);
        assertThat(result.totalPages()).isEqualTo(1);

        verify(runtime).eventLog(caseId);
    }

    @Test
    void getEventLog_firstPage_returnsCorrectSubset() {
        UUID caseId = UUID.randomUUID();
        List<CaseEventLogRecord> records = createMockRecords(100);

        when(runtime.eventLog(caseId))
                .thenReturn(CompletableFuture.completedFuture(records));

        PagedResponse<EventLogEntryResponse> result =
                service.getEventLog(caseId, 1, 10, null, null).await().indefinitely();

        assertThat(result.content()).hasSize(10);
        assertThat(result.page()).isEqualTo(1);
        assertThat(result.size()).isEqualTo(10);
        assertThat(result.totalElements()).isEqualTo(100);
        assertThat(result.totalPages()).isEqualTo(10);
    }

    @Test
    void getEventLog_middlePage_returnsCorrectSubset() {
        UUID caseId = UUID.randomUUID();
        List<CaseEventLogRecord> records = createMockRecords(100);

        when(runtime.eventLog(caseId))
                .thenReturn(CompletableFuture.completedFuture(records));

        PagedResponse<EventLogEntryResponse> result =
                service.getEventLog(caseId, 5, 10, null, null).await().indefinitely();

        assertThat(result.content()).hasSize(10);
        assertThat(result.page()).isEqualTo(5);
        assertThat(result.totalElements()).isEqualTo(100);
        assertThat(result.totalPages()).isEqualTo(10);
    }

    @Test
    void getEventLog_lastPage_returnsPartialSubset() {
        UUID caseId = UUID.randomUUID();
        List<CaseEventLogRecord> records = createMockRecords(95);

        when(runtime.eventLog(caseId))
                .thenReturn(CompletableFuture.completedFuture(records));

        PagedResponse<EventLogEntryResponse> result =
                service.getEventLog(caseId, 10, 10, null, null).await().indefinitely();

        assertThat(result.content()).hasSize(5);  // Last page has 5 items
        assertThat(result.page()).isEqualTo(10);
        assertThat(result.totalElements()).isEqualTo(95);
        assertThat(result.totalPages()).isEqualTo(10);
    }

    @Test
    void getEventLog_pageBeyondTotal_returnsEmptyPage() {
        UUID caseId = UUID.randomUUID();
        List<CaseEventLogRecord> records = createMockRecords(10);

        when(runtime.eventLog(caseId))
                .thenReturn(CompletableFuture.completedFuture(records));

        PagedResponse<EventLogEntryResponse> result =
                service.getEventLog(caseId, 5, 10, null, null).await().indefinitely();

        assertThat(result.content()).isEmpty();
        assertThat(result.page()).isEqualTo(5);
        assertThat(result.totalElements()).isEqualTo(10);
        assertThat(result.totalPages()).isEqualTo(1);
    }

    @Test
    void getEventLog_emptyEventLog_returnsEmptyPage() {
        UUID caseId = UUID.randomUUID();

        when(runtime.eventLog(caseId))
                .thenReturn(CompletableFuture.completedFuture(List.of()));

        PagedResponse<EventLogEntryResponse> result =
                service.getEventLog(caseId, 1, 50, null, null).await().indefinitely();

        assertThat(result.content()).isEmpty();
        assertThat(result.totalElements()).isEqualTo(0);
        assertThat(result.totalPages()).isEqualTo(0);
    }

    private List<CaseEventLogRecord> createMockRecords(int count) {
        ObjectNode payload = objectMapper.createObjectNode().put("index", 0);
        ObjectNode metadata = objectMapper.createObjectNode().put("traceId", "test");

        return java.util.stream.IntStream.range(0, count)
                .mapToObj(i -> new CaseEventLogRecord(
                        CaseHubEventType.WORKER_EXECUTION_COMPLETED,
                        EventStreamType.CASE,
                        Instant.now().plusSeconds(i),
                        payload,
                        metadata))
                .toList();
    }
}
