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

/** See the Scaling Configuration reference in README for details. */
@AutoValue
public abstract class MetricTarget {

  private static final double DEFAULT_SCALING_TOLERANCE = 0.1;

  private static final int DEFAULT_ACTIVATION_THRESHOLD = 0;

  private static final int DEFAULT_WINDOW_SECONDS = 120;

  public enum Type {
    AVERAGE,
    UTILIZATION
  }

  public abstract Type type();

  @Nullable
  public abstract Integer averageUtilization();

  @Nullable
  public abstract Integer averageValue();

  public abstract int activationThreshold();

  public abstract double tolerance();

  public abstract Duration windowSeconds();

  static MetricTarget fromYamlMap(Map<String, Object> input, String resourcePath) {
    MetricTarget.Builder builder = new AutoValue_MetricTarget.Builder();

    Validation.checkKeyExists(input, "type", resourcePath);

    switch ((String) input.get("type")) {
      case "AverageValue":
        builder.type(Type.AVERAGE);
        Validation.checkKeyExists(input, "averageValue", resourcePath);
        builder.averageValue((int) input.get("averageValue"));
        break;
      case "Utilization":
        builder.type(Type.UTILIZATION);
        Validation.checkKeyExists(input, "averageUtilization", resourcePath);
        builder.averageUtilization((int) input.get("averageUtilization"));
        break;
      default:
        throw new IllegalArgumentException(
            String.format("Unsupported %s.metric.type: %s", resourcePath, input.get("type")));
    }

    return builder
        .activationThreshold(
            (int) input.getOrDefault("activationThreshold", DEFAULT_ACTIVATION_THRESHOLD))
        .tolerance((double) input.getOrDefault("tolerance", DEFAULT_SCALING_TOLERANCE))
        .windowSeconds(
            Duration.ofSeconds((int) input.getOrDefault("windowSeconds", DEFAULT_WINDOW_SECONDS)))
        .build();
  }

  @AutoValue.Builder
  public abstract static class Builder {
    public abstract Builder type(Type type);

    public abstract Builder averageUtilization(Integer averageUtilization);

    public abstract Builder averageValue(Integer averageValue);

    public abstract Builder activationThreshold(int activationThreshold);

    public abstract Builder tolerance(double tolerance);

    public abstract Builder windowSeconds(Duration windowSeconds);

    public abstract MetricTarget build();
  }

  public static Builder builder() {
    return new AutoValue_MetricTarget.Builder()
        .activationThreshold(DEFAULT_ACTIVATION_THRESHOLD)
        .tolerance(DEFAULT_SCALING_TOLERANCE)
        .windowSeconds(Duration.ofSeconds(DEFAULT_WINDOW_SECONDS));
  }
}
