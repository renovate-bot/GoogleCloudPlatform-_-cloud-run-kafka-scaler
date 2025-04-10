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
import javax.annotation.Nullable;

/** See the Scaling Configuration reference in README for details. */
@AutoValue
public abstract class Metric {

  public enum Type {
    EXTERNAL,
    RESOURCE
  }

  public abstract Type type();

  @Nullable
  public abstract Resource resource();

  @Nullable
  public abstract External external();

  @SuppressWarnings("unchecked")
  static Metric fromYamlMap(Map<String, Object> input, String resourcePath) {
    Metric.Builder builder = new AutoValue_Metric.Builder();

    Validation.checkKeyExists(input, "type", resourcePath);

    if (input.containsKey("type")) {
      switch ((String) input.get("type")) {
        case "External":
          builder = builder.type(Type.EXTERNAL);

          Validation.checkKeyExists(input, "external", resourcePath);
          builder =
              builder.external(
                  External.fromYamlMap(
                      (Map<String, Object>) input.get("external"), resourcePath + ".external"));
          break;
        case "Resource":
          builder = builder.type(Type.RESOURCE);
          Validation.checkKeyExists(input, "resource", resourcePath);
          builder =
              builder.resource(
                  Resource.fromYamlMap(
                      (Map<String, Object>) input.get("resource"), resourcePath + ".resource"));
          break;
        default:
          throw new IllegalArgumentException(
              String.format("Unsupported %s.type: %s", resourcePath, input.get("type")));
      }
    }

    return builder.build();
  }

  @AutoValue.Builder
  public abstract static class Builder {
    public abstract Builder type(Type type);

    public abstract Builder external(External external);

    public abstract Builder resource(Resource resource);

    public abstract Metric build();
  }

  public static Builder builder() {
    return new AutoValue_Metric.Builder();
  }
}
