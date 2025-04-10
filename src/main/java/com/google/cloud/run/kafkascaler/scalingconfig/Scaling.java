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
import com.google.common.collect.ImmutableList;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import javax.annotation.Nullable;

/**
 * Describes collection of scaling policies, how to merge them, and a stabilization window.
 *
 * <p>See the Scaling Configuration reference in README for details.
 */
@AutoValue
public abstract class Scaling {

  public enum SelectPolicy {
    MAX("Max"),
    MIN("Min"),
    DISABLED("Disabled");

    private final String debugString;

    SelectPolicy(String debugString) {
      this.debugString = debugString;
    }

    @Override
    public String toString() {
      return debugString;
    }
  }

  @Nullable
  public abstract Duration stabilizationWindowSeconds();

  public abstract ImmutableList<Policy> policies();

  @Nullable
  public abstract SelectPolicy selectPolicy();

  @SuppressWarnings("unchecked")
  static Scaling fromYamlMap(Map<String, Object> input, String resourcePath) {
    Scaling.Builder builder = new AutoValue_Scaling.Builder();

    if (input.containsKey("stabilizationWindowSeconds")) {
      builder =
          builder.stabilizationWindowSeconds(
              Duration.ofSeconds((int) input.get("stabilizationWindowSeconds")));
    }

    if (input.containsKey("policies")) {
      List<Policy> policies = new ArrayList<>();
      for (Map<String, Object> policy : (List<Map<String, Object>>) input.get("policies")) {
        policies.add(Policy.fromYamlMap(policy, resourcePath + ".policies"));
      }
      builder = builder.policies(ImmutableList.copyOf(policies));
    }

    if (input.containsKey("selectPolicy")) {
      switch ((String) input.get("selectPolicy")) {
        case "Max":
          builder = builder.selectPolicy(SelectPolicy.MAX);
          break;
        case "Min":
          builder = builder.selectPolicy(SelectPolicy.MIN);
          break;
        case "Disabled":
          builder = builder.selectPolicy(SelectPolicy.DISABLED);
          break;
        default:
          throw new IllegalArgumentException(
              "Unsupported select policy: " + input.get("selectPolicy"));
      }
    }

    return builder.build();
  }

  @AutoValue.Builder
  public abstract static class Builder {
    public abstract Builder stabilizationWindowSeconds(Duration stabilizationWindowSeconds);

    public abstract Builder policies(ImmutableList<Policy> policies);

    public abstract Builder selectPolicy(SelectPolicy selectPolicy);

    public abstract Scaling build();
  }

  public static Builder builder() {
    return new AutoValue_Scaling.Builder().policies(ImmutableList.of());
  }
}
