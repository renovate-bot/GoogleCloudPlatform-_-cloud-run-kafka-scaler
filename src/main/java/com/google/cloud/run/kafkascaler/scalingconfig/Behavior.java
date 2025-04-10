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
import java.time.Duration;
import java.util.Map;
import javax.annotation.Nullable;

/**
 * Scaling behavior containing scale-up and/or scale-down scaling policies.
 *
 * <p>See the Scaling Configuration reference in README for details.
 */
@AutoValue
public abstract class Behavior {
  @Nullable
  public abstract Scaling scaleDown();

  @Nullable
  public abstract Scaling scaleUp();

  @Nullable
  public abstract Duration cooldownSeconds();

  @SuppressWarnings("unchecked")
  static Behavior fromYamlMap(Map<String, Object> input, String resourcePath) {
    Behavior.Builder builder = new AutoValue_Behavior.Builder();
    if (input.containsKey("scaleDown")) {
      builder =
          builder.scaleDown(
              Scaling.fromYamlMap(
                  (Map<String, Object>) input.get("scaleDown"), resourcePath + ".scaleDown"));
    }

    if (input.containsKey("scaleUp")) {
      builder =
          builder.scaleUp(
              Scaling.fromYamlMap(
                  (Map<String, Object>) input.get("scaleUp"), resourcePath + ".scaleUp"));
    }

    if (input.containsKey("cooldownSeconds")) {
      builder = builder.cooldownSeconds(Duration.ofSeconds((int) input.get("cooldownSeconds")));
    } else {
      builder = builder.cooldownSeconds(Duration.ZERO);
    }

    return builder.build();
  }

  @AutoValue.Builder
  public abstract static class Builder {
    public abstract Builder scaleDown(Scaling scaleDown);

    public abstract Builder scaleUp(Scaling scaleUp);

    public abstract Builder cooldownSeconds(Duration cooldownSeconds);

    public abstract Behavior build();
  }

  public static Builder builder() {
    return new AutoValue_Behavior.Builder();
  }
}
