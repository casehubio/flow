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

import io.casehub.api.engine.CaseHub;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.vertx.RunOnVertxContext;
import io.quarkus.test.vertx.UniAsserter;
import jakarta.inject.Inject;
import java.util.Optional;
import org.junit.jupiter.api.Test;

@QuarkusTest
class CaseDefinitionServiceTest {

  @Inject CaseDefinitionService service;

  @Test
  @RunOnVertxContext
  void findCaseHub_returnsCaseHub_forCdiBeans(UniAsserter asserter) {
    // CDI bean from CaseDefinitionResourceTest: Document Approval v1.0.0
    asserter.execute(
        () ->
            service
                .findCaseHub("test-api", "Document Approval", "1.0.0")
                .invoke(
                    hub -> {
                      assertThat(hub).isPresent();
                      assertThat(hub.get().getDefinition().getNamespace()).isEqualTo("test-api");
                      assertThat(hub.get().getDefinition().getName())
                          .isEqualTo("Document Approval");
                      assertThat(hub.get().getDefinition().getVersion()).isEqualTo("1.0.0");
                    }));
  }

  @Test
  @RunOnVertxContext
  void findCaseHub_returnsCaseHub_forClasspathCaseHub(UniAsserter asserter) {
    // Classpath CaseHub from LoaderIntegrationTest: Classpath Only Case
    asserter.execute(
        () ->
            service
                .findCaseHub("test-classpath", "Classpath Only Case", "1.0.0")
                .invoke(
                    hub -> {
                      assertThat(hub).isPresent();
                      assertThat(hub.get().getDefinition().getNamespace())
                          .isEqualTo("test-classpath");
                      assertThat(hub.get().getDefinition().getName())
                          .isEqualTo("Classpath Only Case");
                    }));
  }

  @Test
  @RunOnVertxContext
  void findCaseHub_returnsCaseHub_forYamlDefinition(UniAsserter asserter) {
    // YAML definition from LoaderIntegrationTest: YAML Test Case
    asserter.execute(
        () ->
            service
                .findCaseHub("test-yaml", "YAML Test Case", "1.0.0")
                .invoke(
                    hub -> {
                      assertThat(hub).isPresent();
                      assertThat(hub.get().getDefinition().getNamespace())
                          .isEqualTo("test-yaml");
                      assertThat(hub.get().getDefinition().getName())
                          .isEqualTo("YAML Test Case");
                    }));
  }

  @Test
  @RunOnVertxContext
  void findCaseHub_returnsEmpty_whenNotFound(UniAsserter asserter) {
    asserter.execute(
        () ->
            service
                .findCaseHub("non-existent", "Unknown Case", "1.0.0")
                .invoke(hub -> assertThat(hub).isEmpty()));
  }
}
