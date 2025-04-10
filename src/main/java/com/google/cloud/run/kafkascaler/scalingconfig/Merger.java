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

/**
 * Utility class to merge two scaling configs.
 *
 * <p>Patches one ScalingConfig onto another at the scale-up and scale-down level.
 */
public final class Merger {

  public static Behavior merge(Behavior original, Behavior patch) {
    Behavior.Builder newConfigBuilder = Behavior.builder();

    Scaling patchScaleUp = patch.scaleUp();
    if (patchScaleUp != null) {
      newConfigBuilder.scaleUp(patchScaleUp);
    } else {
      newConfigBuilder.scaleUp(original.scaleUp());
    }

    Scaling patchScaleDown = patch.scaleDown();
    if (patchScaleDown != null) {
      newConfigBuilder.scaleDown(patchScaleDown);
    } else {
      newConfigBuilder.scaleDown(original.scaleDown());
    }

    if (patch.cooldownSeconds() != null) {
      newConfigBuilder.cooldownSeconds(patch.cooldownSeconds());
    } else {
      newConfigBuilder.cooldownSeconds(original.cooldownSeconds());
    }

    return newConfigBuilder.build();
  }

  private Merger() {}
}
