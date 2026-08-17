package io.casehub.flow.graphql;

import org.eclipse.microprofile.graphql.Type;

@Type("PlatformInfo")
public record PlatformInfo(String name, String version) {
}
