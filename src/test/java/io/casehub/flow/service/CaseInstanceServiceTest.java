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

import io.casehub.api.model.CaseStatus;
import io.casehub.flow.exception.DefinitionNotFoundException;
import io.casehub.flow.rest.dto.CaseInstanceResponse;
import io.casehub.flow.rest.dto.StartCaseRequest;
import io.casehub.flow.rest.dto.StartCaseRequest.CaseDefinitionRef;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.vertx.RunOnVertxContext;
import io.quarkus.test.vertx.UniAsserter;
import jakarta.inject.Inject;
import java.util.Map;
import org.junit.jupiter.api.Test;

@QuarkusTest
class CaseInstanceServiceTest {

  @Inject CaseInstanceService service;

  @Test
  @RunOnVertxContext
  void startCase_withCdiBeanDefinition_startsSuccessfully(UniAsserter asserter) {
    StartCaseRequest request =
        new StartCaseRequest(
            new CaseDefinitionRef("test-api", "Document Approval", "1.0.0"),
            Map.of("documentId", "DOC-123"));

    asserter.execute(
        () ->
            service
                .startCase(request)
                .invoke(
                    response -> {
                      assertThat(response.caseId()).isNotNull();
                      assertThat(response.status()).isIn(CaseStatus.RUNNING, CaseStatus.COMPLETED);
                      assertThat(response.namespace()).isEqualTo("test-api");
                      assertThat(response.name()).isEqualTo("Document Approval");
                      assertThat(response.version()).isEqualTo("1.0.0");
                      assertThat(response.createdAt()).isNotNull();
                      assertThat(response.updatedAt()).isNotNull();
                    }));
  }

  @Test
  @RunOnVertxContext
  void startCase_withYamlDefinition_startsSuccessfully(UniAsserter asserter) {
    StartCaseRequest request =
        new StartCaseRequest(
            new CaseDefinitionRef("test-yaml", "YAML Test Case", "1.0.0"),
            Map.of("data", "test-value"));

    asserter.execute(
        () ->
            service
                .startCase(request)
                .invoke(
                    response -> {
                      assertThat(response.caseId()).isNotNull();
                      assertThat(response.status()).isIn(CaseStatus.RUNNING, CaseStatus.COMPLETED);
                      assertThat(response.namespace()).isEqualTo("test-yaml");
                      assertThat(response.name()).isEqualTo("YAML Test Case");
                    }));
  }

  @Test
  @RunOnVertxContext
  void startCase_withClasspathCaseHub_startsSuccessfully(UniAsserter asserter) {
    StartCaseRequest request =
        new StartCaseRequest(
            new CaseDefinitionRef("test-classpath", "Classpath Only Case", "1.0.0"),
            Map.of("data", "test"));

    asserter.execute(
        () ->
            service
                .startCase(request)
                .invoke(
                    response -> {
                      assertThat(response.caseId()).isNotNull();
                      assertThat(response.status()).isIn(CaseStatus.RUNNING, CaseStatus.COMPLETED);
                      assertThat(response.namespace()).isEqualTo("test-classpath");
                      assertThat(response.name()).isEqualTo("Classpath Only Case");
                    }));
  }

  @Test
  @RunOnVertxContext
  void startCase_throwsDefinitionNotFoundException_whenDefinitionNotFound(
      UniAsserter asserter) {
    StartCaseRequest request =
        new StartCaseRequest(
            new CaseDefinitionRef("non-existent", "Unknown Case", "1.0.0"), Map.of());

    asserter.assertFailedWith(
        () -> service.startCase(request), throwable -> {
          assertThat(throwable).isInstanceOf(DefinitionNotFoundException.class);
          assertThat(throwable.getMessage()).contains("No case definition found");
        });
  }
}
