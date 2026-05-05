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
package io.casehub.flow.exception;

/**
 * Thrown when a CaseHub instance is not found for a registered definition.
 *
 * <p>This should not happen in normal operation since all registered definitions should have
 * corresponding CaseHub instances.
 */
public class CaseHubNotFoundException extends RuntimeException {
  public CaseHubNotFoundException(String namespace, String name, String version) {
    super(
        String.format(
            "No CaseHub found for definition namespace '%s', name '%s', version '%s'",
            namespace, name, version));
  }
}
