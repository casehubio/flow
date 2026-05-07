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
package io.casehub.flow.rest.dto;

/**
 * RFC 7807 Problem Details for HTTP APIs.
 *
 * @param title a short, human-readable summary of the problem type
 * @param status the HTTP status code
 * @param detail a human-readable explanation specific to this occurrence
 */
public record ProblemDetail(String title, int status, String detail) {}
