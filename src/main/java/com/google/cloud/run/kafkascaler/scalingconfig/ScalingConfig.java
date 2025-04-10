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
public abstract class ScalingConfig {

  public abstract Spec spec();

  @SuppressWarnings("unchecked")
  static ScalingConfig fromYamlMap(Map<String, Object> input) {
    Validation.checkKeyExists(input, "spec", "");
    Spec spec = Spec.fromYamlMap((Map<String, Object>) input.get("spec"), "spec");
    return new AutoValue_ScalingConfig.Builder().spec(spec).build();
  }

  @AutoValue.Builder
  public abstract static class Builder {
    public abstract Builder spec(Spec spec);

    public abstract ScalingConfig build();
  }

  public Builder toBuilder() {
    return builder().spec(spec());
  }

  public static Builder builder() {
    return new AutoValue_ScalingConfig.Builder();
  }
}
