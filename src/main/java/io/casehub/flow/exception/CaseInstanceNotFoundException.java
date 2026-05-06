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

import java.util.UUID;

/**
 * Thrown when a case instance is not found in the repository.
 */
public class CaseInstanceNotFoundException extends RuntimeException {
  public CaseInstanceNotFoundException(UUID caseId) {
    super(String.format("No case instance found with id '%s'", caseId));
  }

  public CaseInstanceNotFoundException(String message) {
    super(message);
  }
}
