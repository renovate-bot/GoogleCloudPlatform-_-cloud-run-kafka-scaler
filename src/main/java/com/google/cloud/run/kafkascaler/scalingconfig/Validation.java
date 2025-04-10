/*
 * Copyright 2025 Google LLC
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 */
package com.google.cloud.run.kafkascaler.scalingconfig;

import java.util.Map;

/** Utility methods for validating scaling config parsing. */
final class Validation {

  private Validation() {}

  /**
   * Checks that a key exists in a map.
   *
   * @param input The map to check.
   * @param key The key to check for.
   * @param resourcePath The path to the resource being validated. This is only used for logging.
   * @throws IllegalArgumentException if the key does not exist in the map.
   */
  public static void checkKeyExists(Map<String, Object> input, String key, String resourcePath) {
    if (!input.containsKey(key)) {
      throw new IllegalArgumentException(
          String.format("Failed to parse scaling config: %s.%s is required", resourcePath, key));
    }
  }
}
