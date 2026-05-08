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

import io.casehub.api.model.event.CaseHubEventType;
import io.casehub.api.model.event.EventStreamType;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

class EventLogServiceTest {

  private final EventLogService service = new EventLogService();

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
}
