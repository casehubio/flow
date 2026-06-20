package io.casehub.flow.rest;

import static io.restassured.RestAssured.given;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.notNullValue;

import io.casehub.flow.profile.JwtAclTestProfile;
import io.casehub.platform.api.acl.AccessControlProvider;
import io.casehub.platform.api.acl.AclAction;
import io.casehub.platform.api.acl.AclResourceType;
import io.casehub.platform.api.identity.CurrentPrincipal;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.TestProfile;
import io.quarkus.test.security.TestSecurity;
import io.quarkus.test.security.oidc.Claim;
import io.quarkus.test.security.oidc.OidcSecurity;
import jakarta.inject.Inject;
import java.util.UUID;
import org.junit.jupiter.api.Test;

@QuarkusTest
@TestProfile(JwtAclTestProfile.class)
class AclIntegrationTest {

  private static final String ACTOR_ID = "test-actor";
  private static final String TENANT_ID = "test-tenant";
  private static final UUID CASE_ID = UUID.fromString("00000000-0000-0000-0000-000000000001");

  @Inject AccessControlProvider acl;
  @Inject CurrentPrincipal currentPrincipal;

  // --- Config-based principal (fallback, no @TestSecurity) ---

  @Test
  void testPrincipalConfigured() {
    assertThat(currentPrincipal.actorId()).isEqualTo(ACTOR_ID);
    assertThat(currentPrincipal.tenancyId()).isEqualTo(TENANT_ID);
  }

  // --- CaseDefinitionResource ---

  @Test
  void testDenyReadDefinitionByNamespaceAndName() {
    given()
        .when()
        .get("/api/v1/case-definitions/deny-ns/deny-def")
        .then()
        .statusCode(403);
  }

  @Test
  void testDenyReadDefinitionByVersion() {
    given()
        .when()
        .get("/api/v1/case-definitions/deny-ns/deny-def-v/1.0.0")
        .then()
        .statusCode(403);
  }

  @Test
  void testAllowReadDefinitionAfterGrant() {
    String resourceId = AclResourceType.CASE_DEFINITION + ":allow-ns/allow-def";
    acl.grant(ACTOR_ID, resourceId, AclAction.READ, null).toCompletableFuture().join();

    given()
        .when()
        .get("/api/v1/case-definitions/allow-ns/allow-def")
        .then()
        .statusCode(404);
  }

  // --- CaseInstanceResource ---

  @Test
  void testDenyStartCase() {
    given()
        .contentType("application/json")
        .body("""
            {
              "definition": {
                "namespace": "deny-ns",
                "name": "deny-case",
                "version": "1.0.0"
              }
            }
            """)
        .when()
        .post("/api/v1/cases")
        .then()
        .statusCode(403);
  }

  @Test
  void testDenyGetCaseInstance() {
    given()
        .when()
        .get("/api/v1/cases/" + CASE_ID)
        .then()
        .statusCode(403);
  }

  @Test
  void testDenyGetCaseContext() {
    given()
        .when()
        .get("/api/v1/cases/" + CASE_ID + "/context")
        .then()
        .statusCode(403);
  }

  @Test
  void testDenyGetCaseContextByPath() {
    given()
        .when()
        .get("/api/v1/cases/" + CASE_ID + "/context/some.path")
        .then()
        .statusCode(403);
  }

  // --- CaseControlResource ---

  @Test
  void testDenySuspendCase() {
    given()
        .contentType("application/json")
        .body("{}")
        .when()
        .post("/api/v1/cases/" + CASE_ID + "/suspend")
        .then()
        .statusCode(403);
  }

  @Test
  void testDenyResumeCase() {
    given()
        .contentType("application/json")
        .body("{}")
        .when()
        .post("/api/v1/cases/" + CASE_ID + "/resume")
        .then()
        .statusCode(403);
  }

  @Test
  void testDenyCancelCase() {
    given()
        .contentType("application/json")
        .body("{}")
        .when()
        .post("/api/v1/cases/" + CASE_ID + "/cancel")
        .then()
        .statusCode(403);
  }

  // --- SignalResource ---

  @Test
  void testDenySendSignal() {
    given()
        .contentType("application/json")
        .body("""
            {
              "path": "some.path",
              "value": "test"
            }
            """)
        .when()
        .post("/api/v1/cases/" + CASE_ID + "/signals")
        .then()
        .statusCode(403);
  }

  // --- EventLogResource ---

  @Test
  void testDenyGetEventLog() {
    given()
        .when()
        .get("/api/v1/cases/" + CASE_ID + "/events")
        .then()
        .statusCode(403);
  }

  // --- JWT identity via @TestSecurity + @OidcSecurity ---

  @Test
  @TestSecurity(user = "jwt-actor", roles = {"admin"})
  @OidcSecurity(claims = @Claim(key = "tenancyId", value = "jwt-tenant"))
  void testJwtClaimsPopulatePrincipal() {
    assertThat(currentPrincipal.actorId()).isEqualTo("jwt-actor");
    assertThat(currentPrincipal.tenancyId()).isEqualTo("jwt-tenant");
    assertThat(currentPrincipal.groups()).contains("admin");
  }

  @Test
  @TestSecurity(user = "jwt-actor", roles = {"user"})
  @OidcSecurity(claims = @Claim(key = "tenancyId", value = "jwt-tenant"))
  void testDenyWithJwtIdentity() {
    given()
        .when()
        .get("/api/v1/case-definitions/jwt-deny-ns/jwt-deny-def")
        .then()
        .statusCode(403);
  }

  @Test
  @TestSecurity(user = "jwt-actor", roles = {"user"})
  @OidcSecurity(claims = @Claim(key = "tenancyId", value = "jwt-tenant"))
  void testAllowAfterGrantWithJwtIdentity() {
    String resourceId = AclResourceType.CASE_DEFINITION + ":jwt-ns/jwt-def";
    acl.grant("jwt-actor", resourceId, AclAction.READ, null).toCompletableFuture().join();

    given()
        .when()
        .get("/api/v1/case-definitions/jwt-ns/jwt-def")
        .then()
        .statusCode(404);
  }

  // --- JWT positive: grant + verify real response data ---

  private static final String JWT_ACTOR = "jwt-actor";
  private static final String DEF_NS = "test-yaml";
  private static final String DEF_NAME = "YAML Test Case";
  private static final String DEF_VERSION = "1.0.0";
  private static final String DEF_RESOURCE =
      AclResourceType.CASE_DEFINITION + ":" + DEF_NS + "/" + DEF_NAME;

  private String startCaseAsJwtActor() {
    acl.grant(JWT_ACTOR, DEF_RESOURCE, AclAction.WRITE, null).toCompletableFuture().join();

    String caseId =
        given()
            .contentType("application/json")
            .body(
                """
            {"definition":{"namespace":"%s","name":"%s","version":"%s"}}
            """
                    .formatted(DEF_NS, DEF_NAME, DEF_VERSION))
            .when()
            .post("/api/v1/cases")
            .then()
            .statusCode(200)
            .extract()
            .path("caseId");

    String caseResource = AclResourceType.CASE + ":" + caseId;
    acl.grant(JWT_ACTOR, caseResource, AclAction.READ, null).toCompletableFuture().join();
    acl.grant(JWT_ACTOR, caseResource, AclAction.WRITE, null).toCompletableFuture().join();
    acl.grant(JWT_ACTOR, caseResource, AclAction.ADMIN, null).toCompletableFuture().join();

    return caseId;
  }

  @Test
  @TestSecurity(user = JWT_ACTOR, roles = {"user"})
  @OidcSecurity(claims = @Claim(key = "tenancyId", value = "test-tenant"))
  void testReadDefinitionWithJwt() {
    acl.grant(JWT_ACTOR, DEF_RESOURCE, AclAction.READ, null).toCompletableFuture().join();

    given()
        .when()
        .get("/api/v1/case-definitions/" + DEF_NS + "/" + DEF_NAME)
        .then()
        .statusCode(200)
        .body("[0].namespace", equalTo(DEF_NS))
        .body("[0].name", equalTo(DEF_NAME));
  }

  @Test
  @TestSecurity(user = JWT_ACTOR, roles = {"user"})
  @OidcSecurity(claims = @Claim(key = "tenancyId", value = "test-tenant"))
  void testReadDefinitionByVersionWithJwt() {
    acl.grant(JWT_ACTOR, DEF_RESOURCE, AclAction.READ, null).toCompletableFuture().join();

    given()
        .when()
        .get("/api/v1/case-definitions/" + DEF_NS + "/" + DEF_NAME + "/" + DEF_VERSION)
        .then()
        .statusCode(200)
        .body("namespace", equalTo(DEF_NS))
        .body("version", equalTo(DEF_VERSION));
  }

  @Test
  @TestSecurity(user = JWT_ACTOR, roles = {"user"})
  @OidcSecurity(claims = @Claim(key = "tenancyId", value = "test-tenant"))
  void testStartCaseWithJwt() {
    acl.grant(JWT_ACTOR, DEF_RESOURCE, AclAction.WRITE, null).toCompletableFuture().join();

    given()
        .contentType("application/json")
        .body(
            """
            {"definition":{"namespace":"%s","name":"%s","version":"%s"}}
            """
                .formatted(DEF_NS, DEF_NAME, DEF_VERSION))
        .when()
        .post("/api/v1/cases")
        .then()
        .statusCode(200)
        .body("caseId", notNullValue())
        .body("status", equalTo("RUNNING"))
        .body("namespace", equalTo(DEF_NS))
        .body("name", equalTo(DEF_NAME));
  }

  @Test
  @TestSecurity(user = JWT_ACTOR, roles = {"user"})
  @OidcSecurity(claims = @Claim(key = "tenancyId", value = "test-tenant"))
  void testGetCaseInstanceWithJwt() {
    String caseId = startCaseAsJwtActor();

    given()
        .when()
        .get("/api/v1/cases/" + caseId)
        .then()
        .statusCode(200)
        .body("caseId", equalTo(caseId))
        .body("status", notNullValue())
        .body("namespace", equalTo(DEF_NS));
  }

  @Test
  @TestSecurity(user = JWT_ACTOR, roles = {"user"})
  @OidcSecurity(claims = @Claim(key = "tenancyId", value = "test-tenant"))
  void testGetCaseContextWithJwt() {
    String caseId = startCaseAsJwtActor();

    given()
        .when()
        .get("/api/v1/cases/" + caseId + "/context")
        .then()
        .statusCode(200);
  }

  @Test
  @TestSecurity(user = JWT_ACTOR, roles = {"user"})
  @OidcSecurity(claims = @Claim(key = "tenancyId", value = "test-tenant"))
  void testSuspendCaseWithJwt() {
    String caseId = startCaseAsJwtActor();

    given()
        .contentType("application/json")
        .body("{}")
        .when()
        .post("/api/v1/cases/" + caseId + "/suspend")
        .then()
        .statusCode(202)
        .body("caseId", equalTo(caseId))
        .body("operation", equalTo("suspend"));
  }

  @Test
  @TestSecurity(user = JWT_ACTOR, roles = {"user"})
  @OidcSecurity(claims = @Claim(key = "tenancyId", value = "test-tenant"))
  void testCancelCaseWithJwt() {
    String caseId = startCaseAsJwtActor();

    given()
        .contentType("application/json")
        .body("{}")
        .when()
        .post("/api/v1/cases/" + caseId + "/cancel")
        .then()
        .statusCode(202)
        .body("caseId", equalTo(caseId))
        .body("operation", equalTo("cancel"));
  }

  @Test
  @TestSecurity(user = JWT_ACTOR, roles = {"user"})
  @OidcSecurity(claims = @Claim(key = "tenancyId", value = "test-tenant"))
  void testSendSignalWithJwt() {
    String caseId = startCaseAsJwtActor();

    given()
        .contentType("application/json")
        .body("""
            {"path": "data", "value": "signal-value"}
            """)
        .when()
        .post("/api/v1/cases/" + caseId + "/signals")
        .then()
        .statusCode(202)
        .body("caseId", equalTo(caseId))
        .body("status", equalTo("accepted"));
  }

  @Test
  @TestSecurity(user = JWT_ACTOR, roles = {"user"})
  @OidcSecurity(claims = @Claim(key = "tenancyId", value = "test-tenant"))
  void testGetEventLogWithJwt() {
    String caseId = startCaseAsJwtActor();

    given()
        .when()
        .get("/api/v1/cases/" + caseId + "/events")
        .then()
        .statusCode(200)
        .body("page", equalTo(1));
  }
}
