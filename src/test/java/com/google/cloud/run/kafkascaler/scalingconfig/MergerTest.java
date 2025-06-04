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

import static com.google.common.truth.Truth.assertThat;

import com.google.common.collect.ImmutableList;
import java.time.Duration;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public final class MergerTest {

  @Test
  public void merge_withNullPatch_returnsOriginal() {
    Behavior original = DefaultBehavior.VALUE;
    Behavior patch = null;

    assertThat(Merger.merge(original, patch)).isEqualTo(original);
  }

  @Test
  public void merge_withNullOriginal_returnsPatch() {
    Behavior original = null;
    Behavior patch = DefaultBehavior.VALUE;

    assertThat(Merger.merge(original, patch)).isEqualTo(patch);
  }

  @Test
  public void merge_withEmptyPatch_returnsOriginal() {
    Behavior original = DefaultBehavior.VALUE;
    Behavior patch = Behavior.builder().build();

    assertThat(Merger.merge(original, patch)).isEqualTo(original);
  }

  @Test
  public void merge_withCooldownPatch_returnsMerged() {
    Behavior original = DefaultBehavior.VALUE;
    Duration cooldownSeconds = Duration.ofSeconds(60);
    Behavior patch = Behavior.builder().cooldownSeconds(cooldownSeconds).build();

    Behavior result = Merger.merge(original, patch);
    assertThat(result.cooldownSeconds()).isEqualTo(cooldownSeconds);
    assertThat(result.scaleUp()).isEqualTo(original.scaleUp());
    assertThat(result.scaleDown()).isEqualTo(original.scaleDown());
  }

  @Test
  public void merge_withScaleUpPatch_returnsMerged() {
    Behavior original = DefaultBehavior.VALUE;

    Policy policy =
        Policy.builder()
            .type(Policy.Type.INSTANCES)
            .value(50)
            .periodSeconds(Duration.ofSeconds(60))
            .build();
    Scaling scaleUp = Scaling.builder().policies(ImmutableList.of(policy)).build();
    Behavior patch = Behavior.builder().scaleUp(scaleUp).build();

    Behavior result = Merger.merge(original, patch);
    assertThat(result.scaleUp()).isEqualTo(scaleUp);
    assertThat(result.scaleDown()).isEqualTo(original.scaleDown());
  }

  @Test
  public void merge_withScaleDownPatch_returnsMerged() {
    Behavior original = DefaultBehavior.VALUE;

    Policy policy =
        Policy.builder()
            .type(Policy.Type.PERCENT)
            .value(1)
            .periodSeconds(Duration.ofSeconds(60))
            .build();
    Scaling scaleDown = Scaling.builder().policies(ImmutableList.of(policy)).build();
    Behavior patch = Behavior.builder().scaleDown(scaleDown).build();

    Behavior result = Merger.merge(original, patch);
    assertThat(result.scaleDown()).isEqualTo(scaleDown);
    assertThat(result.scaleUp()).isEqualTo(original.scaleUp());
  }

  @Test
  public void merge_withScaleUpAndScaleDownPatch_returnsMerged() {
    Behavior original = DefaultBehavior.VALUE;

    Policy scaleUpPolicy =
        Policy.builder()
            .type(Policy.Type.PERCENT)
            .value(50)
            .periodSeconds(Duration.ofSeconds(60))
            .build();
    Policy scaleDownPolicy =
        Policy.builder()
            .type(Policy.Type.INSTANCES)
            .value(999)
            .periodSeconds(Duration.ofSeconds(60))
            .build();
    Scaling scaleUp = Scaling.builder().policies(ImmutableList.of(scaleUpPolicy)).build();
    Scaling scaleDown = Scaling.builder().policies(ImmutableList.of(scaleDownPolicy)).build();

    Behavior patch = Behavior.builder().scaleUp(scaleUp).scaleDown(scaleDown).build();

    Behavior result = Merger.merge(original, patch);

    assertThat(result.scaleUp()).isEqualTo(scaleUp);
    assertThat(result.scaleDown()).isEqualTo(scaleDown);
  }
}
