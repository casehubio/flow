package io.casehub.flow.graphql;

import jakarta.enterprise.context.ApplicationScoped;
import org.eclipse.microprofile.graphql.GraphQLApi;
import org.eclipse.microprofile.graphql.Query;

@GraphQLApi
@ApplicationScoped
public class PlatformInfoResolver {

    @Query
    public PlatformInfo platformInfo() {
        return new PlatformInfo("CaseHub", "0.2-SNAPSHOT");
    }
}
