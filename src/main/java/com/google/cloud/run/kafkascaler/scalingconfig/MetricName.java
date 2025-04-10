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

import com.google.auto.value.AutoValue;
import java.util.Map;

/** See the Scaling Configuration reference in README for details. */
@AutoValue
public abstract class MetricName {

  public abstract String name();

  static MetricName fromYamlMap(Map<String, Object> input, String resourcePath) {
    AutoValue_MetricName.Builder builder = new AutoValue_MetricName.Builder();

    Validation.checkKeyExists(input, "name", resourcePath);

    return builder.name((String) input.get("name")).build();
  }

  @AutoValue.Builder
  public abstract static class Builder {

    public abstract Builder name(String name);

    public abstract MetricName build();
  }

  public static Builder builder() {
    return new AutoValue_MetricName.Builder();
  }
}
