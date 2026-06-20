package io.casehub.flow.profile;

import io.casehub.platform.api.identity.CurrentPrincipal;
import io.quarkus.security.identity.SecurityIdentity;
import jakarta.annotation.Priority;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Alternative;
import jakarta.inject.Inject;
import java.util.Set;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.eclipse.microprofile.jwt.JsonWebToken;

/**
 * Test-only CurrentPrincipal that reads identity from SecurityIdentity/JWT when a request
 * context is active (populated by @TestSecurity + @OidcSecurity), and falls back to config
 * properties during startup when no request scope exists.
 *
 * <p>Excluded by default via quarkus.arc.exclude-types — activated only in profiles that
 * omit it from the exclusion list.
 */
@Alternative
@Priority(1)
@ApplicationScoped
public class JwtAwareTestPrincipal implements CurrentPrincipal {

  @Inject SecurityIdentity identity;
  @Inject JsonWebToken jwt;

  @ConfigProperty(name = "casehub.platform.principal.actorId", defaultValue = "anonymous")
  String fallbackActorId;

  @ConfigProperty(name = "casehub.tenancy.default-id", defaultValue = "default")
  String fallbackTenancyId;

  @Override
  public String actorId() {
    try {
      if (!identity.isAnonymous()) {
        return identity.getPrincipal().getName();
      }
    } catch (Exception e) {
      return fallbackActorId;
    }
    return fallbackActorId;
  }

  @Override
  public Set<String> groups() {
    try {
      if (!identity.isAnonymous()) {
        return identity.getRoles();
      }
    } catch (Exception e) {
      return Set.of();
    }
    return Set.of();
  }

  @Override
  public String tenancyId() {
    try {
      if (!identity.isAnonymous()) {
        Object claim = jwt.getClaim("tenancyId");
        if (claim != null) return claim.toString();
      }
    } catch (Exception e) {
      return fallbackTenancyId;
    }
    return fallbackTenancyId;
  }

  @Override
  public boolean isCrossTenantAdmin() {
    try {
      if (!identity.isAnonymous()) {
        Boolean claim = jwt.getClaim("crossTenantAdmin");
        return Boolean.TRUE.equals(claim);
      }
    } catch (Exception e) {
      return false;
    }
    return false;
  }
}
