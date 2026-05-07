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
package io.casehub.flow.rest;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.is;
import static org.mockito.Mockito.verify;

import io.casehub.api.engine.CaseHubRuntime;
import io.quarkus.test.InjectMock;
import io.quarkus.test.junit.QuarkusTest;
import io.restassured.http.ContentType;
import java.util.UUID;
import org.junit.jupiter.api.Test;

@QuarkusTest
class CaseControlResourceTest {

  @InjectMock CaseHubRuntime caseHubRuntime;

  @Test
  void testSuspendWithNullRequestBody() {
    UUID caseId = UUID.randomUUID();

    given()
        .contentType(ContentType.JSON)
        .when()
        .post("/api/v1/cases/" + caseId + "/suspend")
        .then()
        .statusCode(202)
        .body("caseId", is(caseId.toString()))
        .body("operation", is("suspend"))
        .body("status", is("accepted"))
        .body("message", is("Case suspension queued for processing"));

    verify(caseHubRuntime).suspendCase(caseId);
  }
}
