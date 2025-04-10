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

import com.google.cloud.run.kafkascaler.scalingconfig.MetricTarget;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public final class LagScalingTest {

  @Test
  public void makeRecommendation_belowActivationLagThreshold_returnsNotActive() {
    MetricTarget lagTarget =
        MetricTarget.builder()
            .type(MetricTarget.Type.AVERAGE)
            .averageValue(1000)
            .activationThreshold(2000)
            .tolerance(0.1)
            .build();
    int currentInstanceCount = 100;
    long currentLag = 1050;

    LagScaling.Recommendation actual =
        LagScaling.makeRecommendation(lagTarget, currentInstanceCount, currentLag);
    assertThat(actual.isActive()).isFalse();
    assertThat(actual.recommendedInstanceCount()).isEqualTo(0);
  }

  @Test
  public void makeRecommendation_withinTolerance_returnsCurrentInstanceCount() {
    MetricTarget lagTarget =
        MetricTarget.builder()
            .type(MetricTarget.Type.AVERAGE)
            .averageValue(1000)
            .activationThreshold(0)
            .tolerance(0.1)
            .build();
    int currentInstanceCount = 100;
    long currentLag = 1050;

    LagScaling.Recommendation actual =
        LagScaling.makeRecommendation(lagTarget, currentInstanceCount, currentLag);
    assertThat(actual.isActive()).isTrue();
    assertThat(actual.recommendedInstanceCount()).isEqualTo(100);
  }

  @Test
  public void makeRecommendation_atUpperTolerance_returnsCurrentInstanceCount() {
    MetricTarget lagTarget =
        MetricTarget.builder()
            .type(MetricTarget.Type.AVERAGE)
            .averageValue(1000)
            .activationThreshold(0)
            .tolerance(0.1)
            .build();
    int currentInstanceCount = 100;
    long currentLag = 1100;

    LagScaling.Recommendation actual =
        LagScaling.makeRecommendation(lagTarget, currentInstanceCount, currentLag);
    assertThat(actual.isActive()).isTrue();
    assertThat(actual.recommendedInstanceCount()).isEqualTo(100);
  }

  @Test
  public void makeRecommendation_atLowerTolerance_returnsCurrentInstanceCount() {
    MetricTarget lagTarget =
        MetricTarget.builder()
            .type(MetricTarget.Type.AVERAGE)
            .averageValue(1000)
            .activationThreshold(0)
            .tolerance(0.1)
            .build();
    int currentInstanceCount = 100;
    long currentLag = 900;

    LagScaling.Recommendation actual =
        LagScaling.makeRecommendation(lagTarget, currentInstanceCount, currentLag);
    assertThat(actual.isActive()).isTrue();
    assertThat(actual.recommendedInstanceCount()).isEqualTo(100);
  }

  @Test
  public void makeRecommendation_aboveTolerance_returnsScaledUpInstanceCount() {
    MetricTarget lagTarget =
        MetricTarget.builder()
            .type(MetricTarget.Type.AVERAGE)
            .averageValue(1000)
            .activationThreshold(0)
            .tolerance(0.1)
            .build();
    int currentInstanceCount = 100;
    long currentLag = 1101;

    LagScaling.Recommendation actual =
        LagScaling.makeRecommendation(lagTarget, currentInstanceCount, currentLag);
    assertThat(actual.isActive()).isTrue();
    assertThat(actual.recommendedInstanceCount()).isEqualTo(111);
  }

  @Test
  public void makeRecommendation_belowTolerance_returnsScaledDownInstanceCount() {
    MetricTarget lagTarget =
        MetricTarget.builder()
            .type(MetricTarget.Type.AVERAGE)
            .averageValue(1000)
            .activationThreshold(0)
            .tolerance(0.1)
            .build();
    int currentInstanceCount = 100;
    long currentLag = 899;

    LagScaling.Recommendation actual =
        LagScaling.makeRecommendation(lagTarget, currentInstanceCount, currentLag);
    assertThat(actual.isActive()).isTrue();
    assertThat(actual.recommendedInstanceCount()).isEqualTo(90);
  }

  @Test
  public void makeRecommendation_withZeroInstances_returnsScaledUpInstanceCount() {
    MetricTarget lagTarget =
        MetricTarget.builder()
            .type(MetricTarget.Type.AVERAGE)
            .averageValue(1000)
            .activationThreshold(0)
            .tolerance(0.1)
            .build();
    int currentInstanceCount = 0;
    long currentLag = 1999;

    LagScaling.Recommendation actual =
        LagScaling.makeRecommendation(lagTarget, currentInstanceCount, currentLag);
    assertThat(actual.isActive()).isTrue();
    assertThat(actual.recommendedInstanceCount()).isEqualTo(2);
  }
}
