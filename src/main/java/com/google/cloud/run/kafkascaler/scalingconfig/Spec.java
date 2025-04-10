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
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import javax.annotation.Nullable;

/** See the Scaling Configuration reference in README for details. */
@AutoValue
public abstract class Spec {
  public abstract ScaleTargetRef scaleTargetRef();

  public abstract ImmutableList<Metric> metrics();

  @Nullable
  public abstract Behavior behavior();

  @SuppressWarnings("unchecked")
  static Spec fromYamlMap(Map<String, Object> input, String resourcePath) {
    Spec.Builder builder = new AutoValue_Spec.Builder();

    Validation.checkKeyExists(input, "scaleTargetRef", resourcePath);
    Validation.checkKeyExists(input, "metrics", resourcePath);

    builder =
        builder.scaleTargetRef(
            ScaleTargetRef.fromYamlMap(
                (Map<String, Object>) input.get("scaleTargetRef"),
                resourcePath + ".scaleTargetRef"));

    List<Metric> metrics = new ArrayList<>();
    for (Map<String, Object> metric : (List<Map<String, Object>>) input.get("metrics")) {
      metrics.add(Metric.fromYamlMap(metric, resourcePath + ".metrics"));
    }

    builder = builder.metrics(ImmutableList.copyOf(metrics));

    if (input.containsKey("behavior")) {
      builder =
          builder.behavior(
              Behavior.fromYamlMap(
                  (Map<String, Object>) input.get("behavior"), resourcePath + ".behavior"));
    }

    return builder.build();
  }

  @AutoValue.Builder
  public abstract static class Builder {

    public abstract Builder scaleTargetRef(ScaleTargetRef scaleTargetRef);

    public abstract Builder metrics(ImmutableList<Metric> metrics);

    public abstract Builder behavior(Behavior behavior);

    public abstract Spec build();
  }

  public Builder toBuilder() {
    return builder().scaleTargetRef(scaleTargetRef()).metrics(metrics()).behavior(behavior());
  }

  public static Builder builder() {
    return new AutoValue_Spec.Builder();
  }
}
