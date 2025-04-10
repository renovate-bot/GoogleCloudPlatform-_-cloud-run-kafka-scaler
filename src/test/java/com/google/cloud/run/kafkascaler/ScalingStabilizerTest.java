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
package com.google.cloud.run.kafkascaler;

import static com.google.common.truth.Truth.assertThat;

import com.google.cloud.run.kafkascaler.scalingconfig.Behavior;
import com.google.cloud.run.kafkascaler.scalingconfig.Policy;
import com.google.cloud.run.kafkascaler.scalingconfig.Scaling;
import com.google.common.collect.ImmutableList;
import java.time.Duration;
import java.time.Instant;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/** */
@RunWith(JUnit4.class)
public final class ScalingStabilizerTest {

  @Test
  public void scalingStablizer_withEmptyConfig() {
    Behavior behavior = Behavior.builder().build();
    ScalingStabilizer scalingStabilizer = new ScalingStabilizer(100);

    assertThat(scalingStabilizer.getBoundedRecommendation(behavior, Instant.now(), 5, 1000))
        .isEqualTo(1000);

    assertThat(scalingStabilizer.getBoundedRecommendation(behavior, Instant.now(), 1000, 1))
        .isEqualTo(1);
  }

  @Test
  public void getBoundedRecommendation_scaleUpStabilizationOnly_returnsStabilizationWindowBound() {
    Scaling scaling = Scaling.builder().stabilizationWindowSeconds(Duration.ofMinutes(5)).build();
    Behavior behavior = Behavior.builder().scaleUp(scaling).build();

    Instant now = Instant.now();
    ScalingStabilizer scalingStabilizer = new ScalingStabilizer(100);

    assertThat(scalingStabilizer.getBoundedRecommendation(behavior, now.plusSeconds(60), 100, 110))
        .isEqualTo(100);
    assertThat(scalingStabilizer.getBoundedRecommendation(behavior, now.plusSeconds(120), 100, 110))
        .isEqualTo(100);
    assertThat(scalingStabilizer.getBoundedRecommendation(behavior, now.plusSeconds(180), 100, 110))
        .isEqualTo(100);
    assertThat(scalingStabilizer.getBoundedRecommendation(behavior, now.plusSeconds(240), 100, 110))
        .isEqualTo(100);
    assertThat(scalingStabilizer.getBoundedRecommendation(behavior, now.plusSeconds(301), 100, 110))
        .isEqualTo(110);
  }

  @Test
  public void
      getBoundedRecommendation_scaleDownStabilizationOnly_returnsStabilizationWindowBound() {
    Scaling scaling = Scaling.builder().stabilizationWindowSeconds(Duration.ofMinutes(5)).build();
    Behavior behavior = Behavior.builder().scaleDown(scaling).build();

    Instant now = Instant.now();
    ScalingStabilizer scalingStabilizer = new ScalingStabilizer(100);

    assertThat(scalingStabilizer.getBoundedRecommendation(behavior, now.plusSeconds(60), 100, 90))
        .isEqualTo(100);
    assertThat(scalingStabilizer.getBoundedRecommendation(behavior, now.plusSeconds(120), 100, 90))
        .isEqualTo(100);
    assertThat(scalingStabilizer.getBoundedRecommendation(behavior, now.plusSeconds(180), 100, 90))
        .isEqualTo(100);
    assertThat(scalingStabilizer.getBoundedRecommendation(behavior, now.plusSeconds(240), 100, 90))
        .isEqualTo(100);
    assertThat(scalingStabilizer.getBoundedRecommendation(behavior, now.plusSeconds(301), 100, 90))
        .isEqualTo(90);
  }

  @Test
  public void getBoundedRecommendation_unchangedRecommendation_countsTowardScaleUpStabilization() {
    Behavior behavior =
        Behavior.builder()
            .scaleUp(Scaling.builder().stabilizationWindowSeconds(Duration.ofMinutes(5)).build())
            .build();
    Instant now = Instant.now();

    ScalingStabilizer scalingStabilizer = new ScalingStabilizer(100);

    assertThat(
            scalingStabilizer.getBoundedRecommendation(
                behavior, now.plus(Duration.ofMinutes(4)), 100, 100))
        .isEqualTo(100);
    assertThat(
            scalingStabilizer.getBoundedRecommendation(
                behavior, now.plus(Duration.ofMinutes(6)), 100, 110))
        .isEqualTo(100);
  }

  @Test
  public void
      getBoundedRecommendation_unchangedRecommendation_countsTowardScaleDownStabilization() {
    Behavior behavior =
        Behavior.builder()
            .scaleDown(Scaling.builder().stabilizationWindowSeconds(Duration.ofMinutes(5)).build())
            .build();
    Instant now = Instant.now();

    ScalingStabilizer scalingStabilizer = new ScalingStabilizer(100);

    assertThat(
            scalingStabilizer.getBoundedRecommendation(
                behavior, now.plus(Duration.ofMinutes(4)), 100, 100))
        .isEqualTo(100);
    assertThat(
            scalingStabilizer.getBoundedRecommendation(
                behavior, now.plus(Duration.ofMinutes(6)), 100, 90))
        .isEqualTo(100);
  }

  @Test
  public void getBoundedRecommendation_scaleDownPercentPolicy_returnsPercentPolicyBound() {
    Policy percentPolicy =
        Policy.builder()
            .type(Policy.Type.PERCENT)
            .value(50)
            .periodSeconds(Duration.ofSeconds(60))
            .build();
    Behavior behavior =
        Behavior.builder()
            .scaleDown(Scaling.builder().policies(ImmutableList.of(percentPolicy)).build())
            .build();

    ScalingStabilizer scalingStabilizer = new ScalingStabilizer(100);

    Instant now = Instant.now();
    assertThat(scalingStabilizer.getBoundedRecommendation(behavior, now, 100, 10)).isEqualTo(50);
    scalingStabilizer.markScaleEvent(behavior, now, 100, 50);

    assertThat(scalingStabilizer.getBoundedRecommendation(behavior, now.plusSeconds(59), 50, 10))
        .isEqualTo(50);
    assertThat(scalingStabilizer.getBoundedRecommendation(behavior, now.plusSeconds(60), 50, 10))
        .isEqualTo(25);
    scalingStabilizer.markScaleEvent(behavior, now.plusSeconds(60), 50, 25);

    assertThat(scalingStabilizer.getBoundedRecommendation(behavior, now.plusSeconds(140), 25, 0))
        .isEqualTo(12);
  }

  @Test
  public void getBoundedRecommendation_scaleDownInstancesPolicy_returnsInstancesPolicyBound() {
    Policy instancesPolicy =
        Policy.builder()
            .type(Policy.Type.INSTANCES)
            .value(1)
            .periodSeconds(Duration.ofSeconds(60))
            .build();
    Behavior behavior =
        Behavior.builder()
            .scaleDown(Scaling.builder().policies(ImmutableList.of(instancesPolicy)).build())
            .build();
    ScalingStabilizer scalingStabilizer = new ScalingStabilizer(100);

    Instant now = Instant.now();
    assertThat(scalingStabilizer.getBoundedRecommendation(behavior, now, 100, 90)).isEqualTo(99);
    scalingStabilizer.markScaleEvent(behavior, now, 100, 99);

    assertThat(scalingStabilizer.getBoundedRecommendation(behavior, now.plusSeconds(59), 99, 90))
        .isEqualTo(99);
    assertThat(scalingStabilizer.getBoundedRecommendation(behavior, now.plusSeconds(60), 99, 90))
        .isEqualTo(98);
    scalingStabilizer.markScaleEvent(behavior, now.plusSeconds(60), 99, 98);

    assertThat(scalingStabilizer.getBoundedRecommendation(behavior, now.plusSeconds(140), 98, 0))
        .isEqualTo(97);
  }

  @Test
  public void
      getBoundedRecommendation_multipleScaleDownPoliciesUnsetSelectPolicy_returnsMinBound() {
    Policy percentPolicy =
        Policy.builder()
            .type(Policy.Type.PERCENT)
            .value(50)
            .periodSeconds(Duration.ofSeconds(60))
            .build();
    Policy instancesPolicy =
        Policy.builder()
            .type(Policy.Type.INSTANCES)
            .value(1)
            .periodSeconds(Duration.ofSeconds(60))
            .build();

    Behavior behavior =
        Behavior.builder()
            .scaleDown(
                Scaling.builder()
                    .policies(ImmutableList.of(instancesPolicy, percentPolicy))
                    .build())
            .build();

    ScalingStabilizer scalingStabilizer = new ScalingStabilizer(100);

    Instant now = Instant.now();
    assertThat(scalingStabilizer.getBoundedRecommendation(behavior, now, 100, 40)).isEqualTo(50);
    scalingStabilizer.markScaleEvent(behavior, now, 100, 50);

    assertThat(scalingStabilizer.getBoundedRecommendation(behavior, now.plusSeconds(59), 50, 0))
        .isEqualTo(50);
    assertThat(scalingStabilizer.getBoundedRecommendation(behavior, now.plusSeconds(60), 50, 0))
        .isEqualTo(25);
    scalingStabilizer.markScaleEvent(behavior, now.plusSeconds(61), 50, 25);

    assertThat(scalingStabilizer.getBoundedRecommendation(behavior, now.plusSeconds(140), 25, 0))
        .isEqualTo(12);
  }

  @Test
  public void getBoundedRecommendation_multipleScaleDownPoliciesMaxSelectPolicy_returnsMinBound() {
    Policy percentPolicy =
        Policy.builder()
            .type(Policy.Type.PERCENT)
            .value(50)
            .periodSeconds(Duration.ofSeconds(60))
            .build();
    Policy instancesPolicy =
        Policy.builder()
            .type(Policy.Type.INSTANCES)
            .value(1)
            .periodSeconds(Duration.ofSeconds(60))
            .build();

    Behavior behavior =
        Behavior.builder()
            .scaleDown(
                Scaling.builder()
                    .policies(ImmutableList.of(instancesPolicy, percentPolicy))
                    .selectPolicy(Scaling.SelectPolicy.MAX)
                    .build())
            .build();

    ScalingStabilizer scalingStabilizer = new ScalingStabilizer(100);

    Instant now = Instant.now();
    assertThat(scalingStabilizer.getBoundedRecommendation(behavior, now, 100, 40)).isEqualTo(50);
    scalingStabilizer.markScaleEvent(behavior, now, 100, 50);

    assertThat(scalingStabilizer.getBoundedRecommendation(behavior, now.plusSeconds(59), 50, 0))
        .isEqualTo(50);
    assertThat(scalingStabilizer.getBoundedRecommendation(behavior, now.plusSeconds(60), 50, 0))
        .isEqualTo(25);
    scalingStabilizer.markScaleEvent(behavior, now.plusSeconds(61), 50, 25);

    assertThat(scalingStabilizer.getBoundedRecommendation(behavior, now.plusSeconds(140), 25, 0))
        .isEqualTo(12);
  }

  @Test
  public void getBoundedRecommendation_multipleScaleDownPoliciesMinSelectPolicy_returnsMaxBound() {
    Policy percentPolicy =
        Policy.builder()
            .type(Policy.Type.PERCENT)
            .value(50)
            .periodSeconds(Duration.ofSeconds(60))
            .build();
    Policy instancesPolicy =
        Policy.builder()
            .type(Policy.Type.INSTANCES)
            .value(1)
            .periodSeconds(Duration.ofSeconds(60))
            .build();

    Behavior behavior =
        Behavior.builder()
            .scaleDown(
                Scaling.builder()
                    .policies(ImmutableList.of(instancesPolicy, percentPolicy))
                    .selectPolicy(Scaling.SelectPolicy.MIN)
                    .build())
            .build();

    ScalingStabilizer scalingStabilizer = new ScalingStabilizer(100);

    Instant now = Instant.now();
    assertThat(scalingStabilizer.getBoundedRecommendation(behavior, now, 100, 40)).isEqualTo(99);
    scalingStabilizer.markScaleEvent(behavior, now, 100, 99);

    assertThat(scalingStabilizer.getBoundedRecommendation(behavior, now.plusSeconds(59), 99, 98))
        .isEqualTo(99);

    assertThat(scalingStabilizer.getBoundedRecommendation(behavior, now.plusSeconds(60), 99, 90))
        .isEqualTo(98);
    scalingStabilizer.markScaleEvent(behavior, now.plusSeconds(61), 99, 98);

    assertThat(scalingStabilizer.getBoundedRecommendation(behavior, now.plusSeconds(140), 98, 0))
        .isEqualTo(97);
  }

  @Test
  public void getBoundedRecommendation_scaleUpPercentPolicy_returnsPercentPolicyBound() {
    Policy percentPolicy =
        Policy.builder()
            .type(Policy.Type.PERCENT)
            .value(50)
            .periodSeconds(Duration.ofSeconds(60))
            .build();

    Behavior behavior =
        Behavior.builder()
            .scaleUp(Scaling.builder().policies(ImmutableList.of(percentPolicy)).build())
            .build();

    ScalingStabilizer scalingStabilizer = new ScalingStabilizer(100);

    Instant now = Instant.now();
    assertThat(scalingStabilizer.getBoundedRecommendation(behavior, now, 100, 200)).isEqualTo(150);
    scalingStabilizer.markScaleEvent(behavior, now, 100, 150);

    assertThat(scalingStabilizer.getBoundedRecommendation(behavior, now.plusSeconds(59), 150, 200))
        .isEqualTo(150);
    assertThat(scalingStabilizer.getBoundedRecommendation(behavior, now.plusSeconds(60), 150, 250))
        .isEqualTo(225);
    scalingStabilizer.markScaleEvent(behavior, now.plusSeconds(61), 150, 250);

    assertThat(scalingStabilizer.getBoundedRecommendation(behavior, now.plusSeconds(140), 225, 500))
        .isEqualTo(338);
  }

  @Test
  public void getBoundedRecommendation_scaleUpPercentPolicyOnly_cannotScaleUpFromZero() {
    // This test simply demonstrates the fact that a percent policy without an instances policy will
    // prevent scaling up from 0 as is the case with HPA. We'll handle 0->1 scaling the way that
    // Keda does in a future CL.
    Policy percentPolicy =
        Policy.builder()
            .type(Policy.Type.PERCENT)
            .value(50)
            .periodSeconds(Duration.ofSeconds(60))
            .build();

    Behavior behavior =
        Behavior.builder()
            .scaleUp(Scaling.builder().policies(ImmutableList.of(percentPolicy)).build())
            .build();

    ScalingStabilizer scalingStabilizer = new ScalingStabilizer(0);
    Instant now = Instant.now();

    assertThat(scalingStabilizer.getBoundedRecommendation(behavior, now, 0, 10)).isEqualTo(0);
  }

  @Test
  public void getBoundedRecommendation_scaleUpInstancesPolicy_returnsInstancesPolicyBound() {
    Policy instancesPolicy =
        Policy.builder()
            .type(Policy.Type.INSTANCES)
            .value(5)
            .periodSeconds(Duration.ofSeconds(60))
            .build();

    Behavior behavior =
        Behavior.builder()
            .scaleUp(Scaling.builder().policies(ImmutableList.of(instancesPolicy)).build())
            .build();

    ScalingStabilizer scalingStabilizer = new ScalingStabilizer(100);

    Instant now = Instant.now();
    assertThat(scalingStabilizer.getBoundedRecommendation(behavior, now, 100, 200)).isEqualTo(105);
    scalingStabilizer.markScaleEvent(behavior, now, 100, 105);

    assertThat(scalingStabilizer.getBoundedRecommendation(behavior, now.plusSeconds(59), 105, 200))
        .isEqualTo(105);
    assertThat(scalingStabilizer.getBoundedRecommendation(behavior, now.plusSeconds(60), 105, 200))
        .isEqualTo(110);
    scalingStabilizer.markScaleEvent(behavior, now.plusSeconds(60), 105, 110);

    assertThat(scalingStabilizer.getBoundedRecommendation(behavior, now.plusSeconds(140), 110, 500))
        .isEqualTo(115);
  }

  @Test
  public void getBoundedRecommendation_scaleUpMultiplePoliciesUnsetSelectPolicy_returnsMaxBound() {
    Policy percentPolicy =
        Policy.builder()
            .type(Policy.Type.PERCENT)
            .value(50)
            .periodSeconds(Duration.ofSeconds(60))
            .build();
    Policy instancesPolicy =
        Policy.builder()
            .type(Policy.Type.INSTANCES)
            .value(5)
            .periodSeconds(Duration.ofSeconds(60))
            .build();

    Behavior behavior =
        Behavior.builder()
            .scaleUp(
                Scaling.builder()
                    .policies(ImmutableList.of(instancesPolicy, percentPolicy))
                    .build())
            .build();
    ScalingStabilizer scalingStabilizer = new ScalingStabilizer(100);

    Instant now = Instant.now();
    assertThat(scalingStabilizer.getBoundedRecommendation(behavior, now, 100, 200)).isEqualTo(150);
    scalingStabilizer.markScaleEvent(behavior, now, 100, 150);

    assertThat(scalingStabilizer.getBoundedRecommendation(behavior, now.plusSeconds(59), 150, 200))
        .isEqualTo(150);
    assertThat(scalingStabilizer.getBoundedRecommendation(behavior, now.plusSeconds(60), 150, 250))
        .isEqualTo(225);
    scalingStabilizer.markScaleEvent(behavior, now.plusSeconds(61), 150, 250);

    assertThat(scalingStabilizer.getBoundedRecommendation(behavior, now.plusSeconds(140), 225, 500))
        .isEqualTo(338);
  }

  @Test
  public void getBoundedRecommendation_scaleUpMultiplePoliciesMaxSelectPolicy_returnsMaxBound() {
    Policy percentPolicy =
        Policy.builder()
            .type(Policy.Type.PERCENT)
            .value(50)
            .periodSeconds(Duration.ofSeconds(60))
            .build();
    Policy instancesPolicy =
        Policy.builder()
            .type(Policy.Type.INSTANCES)
            .value(5)
            .periodSeconds(Duration.ofSeconds(60))
            .build();

    Behavior behavior =
        Behavior.builder()
            .scaleUp(
                Scaling.builder()
                    .policies(ImmutableList.of(instancesPolicy, percentPolicy))
                    .selectPolicy(Scaling.SelectPolicy.MAX)
                    .build())
            .build();
    ScalingStabilizer scalingStabilizer = new ScalingStabilizer(100);

    Instant now = Instant.now();
    assertThat(scalingStabilizer.getBoundedRecommendation(behavior, now, 100, 200)).isEqualTo(150);
    scalingStabilizer.markScaleEvent(behavior, now, 100, 150);

    assertThat(scalingStabilizer.getBoundedRecommendation(behavior, now.plusSeconds(59), 150, 200))
        .isEqualTo(150);
    assertThat(scalingStabilizer.getBoundedRecommendation(behavior, now.plusSeconds(60), 150, 250))
        .isEqualTo(225);
    scalingStabilizer.markScaleEvent(behavior, now.plusSeconds(61), 150, 250);

    assertThat(scalingStabilizer.getBoundedRecommendation(behavior, now.plusSeconds(140), 225, 500))
        .isEqualTo(338);
  }

  @Test
  public void
      getBoundedRecommendation_scaleUpMultiplePoliciesWithMinSelectPolicy_returnsMinBound() {
    Policy percentPolicy =
        Policy.builder()
            .type(Policy.Type.PERCENT)
            .value(100)
            .periodSeconds(Duration.ofSeconds(90))
            .build();
    Policy instancesPolicy =
        Policy.builder()
            .type(Policy.Type.INSTANCES)
            .value(5)
            .periodSeconds(Duration.ofSeconds(60))
            .build();

    Behavior behavior =
        Behavior.builder()
            .scaleUp(
                Scaling.builder()
                    .policies(ImmutableList.of(instancesPolicy, percentPolicy))
                    .selectPolicy(Scaling.SelectPolicy.MIN)
                    .build())
            .build();

    ScalingStabilizer scalingStabilizer = new ScalingStabilizer(100);

    Instant now = Instant.now();
    assertThat(scalingStabilizer.getBoundedRecommendation(behavior, now, 100, 200)).isEqualTo(105);
    scalingStabilizer.markScaleEvent(behavior, now, 100, 105);

    assertThat(scalingStabilizer.getBoundedRecommendation(behavior, now.plusSeconds(59), 105, 200))
        .isEqualTo(105);
    assertThat(scalingStabilizer.getBoundedRecommendation(behavior, now.plusSeconds(75), 105, 200))
        .isEqualTo(110);
    scalingStabilizer.markScaleEvent(behavior, now.plusSeconds(60), 105, 110);

    assertThat(scalingStabilizer.getBoundedRecommendation(behavior, now.plusSeconds(140), 110, 500))
        .isEqualTo(115);
  }

  @Test
  public void getBoundedRecommendation_scaleUpDisabled_returnsCurrentInstances() {
    Behavior behavior =
        Behavior.builder()
            .scaleUp(Scaling.builder().selectPolicy(Scaling.SelectPolicy.DISABLED).build())
            .build();

    ScalingStabilizer scalingStabilizer = new ScalingStabilizer(100);

    Instant now = Instant.now();
    assertThat(scalingStabilizer.getBoundedRecommendation(behavior, now, 100, 200)).isEqualTo(100);
    scalingStabilizer.markScaleEvent(behavior, now, 100, 200);
    assertThat(scalingStabilizer.getBoundedRecommendation(behavior, now, 200, 400)).isEqualTo(200);
  }

  @Test
  public void getBoundedRecommendation_scaleDownDisabled_returnsCurrentInstances() {
    Behavior behavior =
        Behavior.builder()
            .scaleDown(Scaling.builder().selectPolicy(Scaling.SelectPolicy.DISABLED).build())
            .build();

    ScalingStabilizer scalingStabilizer = new ScalingStabilizer(200);

    Instant now = Instant.now();
    assertThat(scalingStabilizer.getBoundedRecommendation(behavior, now, 200, 100)).isEqualTo(200);
    scalingStabilizer.markScaleEvent(behavior, now, 200, 100);
    assertThat(scalingStabilizer.getBoundedRecommendation(behavior, now, 100, 50)).isEqualTo(100);
  }

  @Test
  public void
      getBoundedRecommendation_scaleUpMultiplePoliciesMaxSelectPolicyAndStabilizationWindow_returnsStabilizationBound() {
    Policy percentPolicy =
        Policy.builder()
            .type(Policy.Type.PERCENT)
            .value(50)
            .periodSeconds(Duration.ofSeconds(60))
            .build();

    Behavior behavior =
        Behavior.builder()
            .scaleUp(
                Scaling.builder()
                    .stabilizationWindowSeconds(Duration.ofSeconds(60))
                    .policies(ImmutableList.of(percentPolicy))
                    .selectPolicy(Scaling.SelectPolicy.MAX)
                    .build())
            .build();

    ScalingStabilizer scalingStabilizer = new ScalingStabilizer(100);

    Instant now = Instant.now();
    // stabilizationWindow dominates for configured seconds after startup
    assertThat(scalingStabilizer.getBoundedRecommendation(behavior, now.plusSeconds(59), 100, 200))
        .isEqualTo(100);
    assertThat(scalingStabilizer.getBoundedRecommendation(behavior, now.plusSeconds(60), 100, 200))
        .isEqualTo(150);
  }

  @Test
  public void
      getBoundedRecommendation_scaleDownMultiplePoliciesMaxSelectPolicyAndStabilizationWindow_returnsStabilizationBound() {
    Policy percentPolicy =
        Policy.builder()
            .type(Policy.Type.PERCENT)
            .value(50)
            .periodSeconds(Duration.ofSeconds(60))
            .build();

    Behavior behavior =
        Behavior.builder()
            .scaleDown(
                Scaling.builder()
                    .stabilizationWindowSeconds(Duration.ofSeconds(60))
                    .policies(ImmutableList.of(percentPolicy))
                    .selectPolicy(Scaling.SelectPolicy.MAX)
                    .build())
            .build();

    ScalingStabilizer scalingStabilizer = new ScalingStabilizer(100);

    Instant now = Instant.now();
    // stabilizationWindow dominates for configured seconds after startup
    assertThat(scalingStabilizer.getBoundedRecommendation(behavior, now.plusSeconds(59), 100, 50))
        .isEqualTo(100);
    assertThat(scalingStabilizer.getBoundedRecommendation(behavior, now.plusSeconds(60), 100, 50))
        .isEqualTo(50);
  }

  @Test
  public void getBoundedRecommendation_decreasingWindowSize_returnsCorrectBound() {
    Scaling scaling = Scaling.builder().stabilizationWindowSeconds(Duration.ofMinutes(5)).build();
    Behavior behavior = Behavior.builder().scaleDown(scaling).build();

    Instant now = Instant.now();
    ScalingStabilizer scalingStabilizer = new ScalingStabilizer(100);

    assertThat(scalingStabilizer.getBoundedRecommendation(behavior, now.plusSeconds(60), 100, 90))
        .isEqualTo(100);

    Behavior decreasedStabilizationWindow =
        Behavior.builder()
            .scaleDown(Scaling.builder().stabilizationWindowSeconds(Duration.ofMinutes(1)).build())
            .build();

    // This original recommendation of 100 is no longer in the window; only the recent
    // recommendation of 90 is.
    assertThat(
            scalingStabilizer.getBoundedRecommendation(
                decreasedStabilizationWindow, now.plusSeconds(119), 100, 80))
        .isEqualTo(90);
  }

  @Test
  public void getBoundedRecommendation_increasingWindowSize_returnsCorrectBound() {
    Scaling scaling = Scaling.builder().stabilizationWindowSeconds(Duration.ofMinutes(5)).build();
    Behavior behavior = Behavior.builder().scaleDown(scaling).build();

    Instant now = Instant.now();
    ScalingStabilizer scalingStabilizer = new ScalingStabilizer(100);

    assertThat(scalingStabilizer.getBoundedRecommendation(behavior, now.plusSeconds(60), 100, 90))
        .isEqualTo(100);

    Behavior increasedStabilizationWindow =
        Behavior.builder()
            .scaleDown(Scaling.builder().stabilizationWindowSeconds(Duration.ofMinutes(6)).build())
            .build();

    // This original recommendation of 100 should no longer be in the window but it's still
    // considered here because of the increased window size.
    assertThat(
            scalingStabilizer.getBoundedRecommendation(
                increasedStabilizationWindow, now.plusSeconds(359), 100, 80))
        .isEqualTo(100);
  }

  @Test
  public void getBoundedRecommendation_percentIncreases_returnsCorrectBound() {
    Behavior behavior =
        Behavior.builder()
            .scaleDown(
                Scaling.builder()
                    .policies(
                        ImmutableList.of(
                            Policy.builder()
                                .type(Policy.Type.PERCENT)
                                .value(50)
                                .periodSeconds(Duration.ofSeconds(60))
                                .build()))
                    .build())
            .build();

    ScalingStabilizer scalingStabilizer = new ScalingStabilizer(100);

    Instant now = Instant.now();
    assertThat(scalingStabilizer.getBoundedRecommendation(behavior, now, 100, 10)).isEqualTo(50);
    scalingStabilizer.markScaleEvent(behavior, now, 100, 50);

    assertThat(scalingStabilizer.getBoundedRecommendation(behavior, now.plusSeconds(59), 50, 10))
        .isEqualTo(50);

    Behavior increasedScaledownPercent =
        Behavior.builder()
            .scaleDown(
                Scaling.builder()
                    .policies(
                        ImmutableList.of(
                            Policy.builder()
                                .type(Policy.Type.PERCENT)
                                .value(80)
                                .periodSeconds(Duration.ofSeconds(60))
                                .build()))
                    .build())
            .build();

    assertThat(
            scalingStabilizer.getBoundedRecommendation(
                increasedScaledownPercent, now.plusSeconds(60), 50, 0))
        .isEqualTo(10);
  }

  @Test
  public void getBoundedRecommendation_percentDecreases_returnsCorrectBound() {
    Behavior behavior =
        Behavior.builder()
            .scaleDown(
                Scaling.builder()
                    .policies(
                        ImmutableList.of(
                            Policy.builder()
                                .type(Policy.Type.PERCENT)
                                .value(50)
                                .periodSeconds(Duration.ofSeconds(60))
                                .build()))
                    .build())
            .build();

    ScalingStabilizer scalingStabilizer = new ScalingStabilizer(100);

    Instant now = Instant.now();
    assertThat(scalingStabilizer.getBoundedRecommendation(behavior, now, 100, 10)).isEqualTo(50);
    scalingStabilizer.markScaleEvent(behavior, now, 100, 50);

    assertThat(scalingStabilizer.getBoundedRecommendation(behavior, now.plusSeconds(59), 50, 10))
        .isEqualTo(50);

    Behavior increasedScaledownPercent =
        Behavior.builder()
            .scaleDown(
                Scaling.builder()
                    .policies(
                        ImmutableList.of(
                            Policy.builder()
                                .type(Policy.Type.PERCENT)
                                .value(20)
                                .periodSeconds(Duration.ofSeconds(60))
                                .build()))
                    .build())
            .build();

    assertThat(
            scalingStabilizer.getBoundedRecommendation(
                increasedScaledownPercent, now.plusSeconds(60), 50, 0))
        .isEqualTo(40);
  }

  @Test
  public void getBoundedRecommendation_rateWindowIncreases_returnsCorrectBound() {
    Behavior behavior =
        Behavior.builder()
            .scaleDown(
                Scaling.builder()
                    .policies(
                        ImmutableList.of(
                            Policy.builder()
                                .type(Policy.Type.PERCENT)
                                .value(50)
                                .periodSeconds(Duration.ofSeconds(60))
                                .build()))
                    .build())
            .build();

    ScalingStabilizer scalingStabilizer = new ScalingStabilizer(100);

    Instant now = Instant.now();
    assertThat(scalingStabilizer.getBoundedRecommendation(behavior, now, 100, 10)).isEqualTo(50);
    scalingStabilizer.markScaleEvent(behavior, now, 100, 50);

    assertThat(scalingStabilizer.getBoundedRecommendation(behavior, now.plusSeconds(59), 50, 10))
        .isEqualTo(50);
    assertThat(scalingStabilizer.getBoundedRecommendation(behavior, now.plusSeconds(60), 50, 10))
        .isEqualTo(25);
    scalingStabilizer.markScaleEvent(behavior, now.plusSeconds(60), 50, 25);

    Behavior increasedScaleDownWindow =
        Behavior.builder()
            .scaleDown(
                Scaling.builder()
                    .policies(
                        ImmutableList.of(
                            Policy.builder()
                                .type(Policy.Type.PERCENT)
                                .value(50)
                                .periodSeconds(Duration.ofMinutes(2))
                                .build()))
                    .build())
            .build();

    // The window increased to include `now` again, so the bound is still 25 when it would have
    // otherwise been 25 with the original window.
    assertThat(
            scalingStabilizer.getBoundedRecommendation(
                increasedScaleDownWindow, now.plusSeconds(120), 25, 0))
        .isEqualTo(25);
  }

  @Test
  public void getBoundedRecommendation_rateWindowDecreases_returnsCorrectBound() {
    Behavior behavior =
        Behavior.builder()
            .scaleDown(
                Scaling.builder()
                    .policies(
                        ImmutableList.of(
                            Policy.builder()
                                .type(Policy.Type.PERCENT)
                                .value(50)
                                .periodSeconds(Duration.ofSeconds(60))
                                .build()))
                    .build())
            .build();

    ScalingStabilizer scalingStabilizer = new ScalingStabilizer(100);

    Instant now = Instant.now();
    assertThat(scalingStabilizer.getBoundedRecommendation(behavior, now, 100, 10)).isEqualTo(50);
    scalingStabilizer.markScaleEvent(behavior, now, 100, 50);

    assertThat(scalingStabilizer.getBoundedRecommendation(behavior, now.plusSeconds(60), 50, 45))
        .isEqualTo(45);
    scalingStabilizer.markScaleEvent(behavior, now.plusSeconds(60), 50, 45);

    assertThat(scalingStabilizer.getBoundedRecommendation(behavior, now.plusSeconds(90), 45, 40))
        .isEqualTo(40);
    scalingStabilizer.markScaleEvent(behavior, now.plusSeconds(90), 45, 40);

    Behavior decreasedScaleDownWindow =
        Behavior.builder()
            .scaleDown(
                Scaling.builder()
                    .policies(
                        ImmutableList.of(
                            Policy.builder()
                                .type(Policy.Type.PERCENT)
                                .value(50)
                                .periodSeconds(Duration.ofSeconds(30))
                                .build()))
                    .build())
            .build();

    // The window decreased to only include `now + 90`; if it included `now + 60`, the bound would
    // have been 22.
    assertThat(
            scalingStabilizer.getBoundedRecommendation(
                decreasedScaleDownWindow, now.plusSeconds(120), 40, 0))
        .isEqualTo(20);
  }
}
