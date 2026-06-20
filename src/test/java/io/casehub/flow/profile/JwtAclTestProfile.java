package io.casehub.flow.profile;

import io.quarkus.test.junit.QuarkusTestProfile;
import java.util.Map;

/**
 * Test profile that activates JPA-backed ACL + JWT-aware CurrentPrincipal.
 *
 * <p>JwtAwareTestPrincipal reads actorId/tenancyId/groups from SecurityIdentity and JWT
 * claims populated by @TestSecurity + @OidcSecurity annotations. Falls back to config
 * properties during startup.
 */
public class JwtAclTestProfile implements QuarkusTestProfile {

  @Override
  public Map<String, String> getConfigOverrides() {
    return Map.of(
        "quarkus.arc.exclude-types",
        "io.casehub.platform.oidc.OidcCurrentPrincipal,io.casehub.work.runtime.service.NoOpGroupMembershipProvider,io.casehub.work.runtime.service.TenantScopedPrincipal",
        "casehub.platform.principal.actorId", "test-actor",
        "casehub.tenancy.default-id", "test-tenant");
  }
}
