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

/**
 * Describes either scale scale up or scale down policy, depending on where it's nested in the
 * scaling configuration yaml.
 *
 * <p>This allows the customer to specify "Do not scale up more than 1 instance per 10 seconds" or
 * "Do not scale down more 50% in 1 minute".
 *
 * <p>See the Scaling Configuration reference in README for details.
 */
@AutoValue
public abstract class Policy {

  public enum Type {
    PERCENT("Percent"),
    INSTANCES("Instances"),
    STABILIZATION("Stabilization");

    private final String debugString;

    Type(String debugString) {
      this.debugString = debugString;
    }

    @Override
    public String toString() {
      return debugString;
    }
  }

  public abstract Type type();

  public abstract int value();

  public abstract Duration periodSeconds();

  static Policy fromYamlMap(Map<String, Object> input, String resourcePath) {
    Validation.checkKeyExists(input, "type", resourcePath);
    Validation.checkKeyExists(input, "value", resourcePath);
    Validation.checkKeyExists(input, "periodSeconds", resourcePath);

    Policy.Builder builder =
        new AutoValue_Policy.Builder()
            .value((int) input.get("value"))
            .periodSeconds(Duration.ofSeconds((int) input.get("periodSeconds")));
    switch ((String) input.get("type")) {
      case "Percent":
        builder = builder.type(Type.PERCENT);
        break;
      case "Instances":
        builder = builder.type(Type.INSTANCES);
        break;
      default:
        throw new IllegalArgumentException("Unsupported policy type: " + input.get("type"));
    }

    return builder.build();
  }

  @AutoValue.Builder
  public abstract static class Builder {
    public abstract Builder type(Type type);

    public abstract Builder value(int value);

    public abstract Builder periodSeconds(Duration periodSeconds);

    public abstract Policy build();
  }

  public static Builder builder() {
    return new AutoValue_Policy.Builder();
  }
}
