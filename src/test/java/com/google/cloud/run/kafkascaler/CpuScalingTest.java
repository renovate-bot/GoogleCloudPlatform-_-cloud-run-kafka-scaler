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
import static org.junit.Assert.assertThrows;

import com.google.cloud.run.kafkascaler.scalingconfig.MetricTarget;
import com.google.common.collect.ImmutableList;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public final class CpuScalingTest {

  @Test
  public void makeRecommendation_negativeTargetCpuUtilization_throwsIllegalArgumentException() {
    MetricTarget cpuTarget =
        MetricTarget.builder()
            .type(MetricTarget.Type.UTILIZATION)
            .averageUtilization(-1)
            .tolerance(0.1)
            .build();
    ImmutableList<MetricsService.InstanceCountUtilization> instanceCountUtilizations =
        ImmutableList.of(
            new MetricsService.InstanceCountUtilization(100, 0.5),
            new MetricsService.InstanceCountUtilization(200, 0.6));

    assertThrows(
        IllegalArgumentException.class,
        () -> CpuScaling.makeRecommendation(cpuTarget, instanceCountUtilizations));
  }

  @Test
  public void
      makeRecommendation_targetCpuUtilizationGreaterThanOne_throwsIllegalArgumentException() {
    MetricTarget cpuTarget =
        MetricTarget.builder()
            .type(MetricTarget.Type.UTILIZATION)
            .averageUtilization(101)
            .tolerance(0.1)
            .build();
    ImmutableList<MetricsService.InstanceCountUtilization> instanceCountUtilizations =
        ImmutableList.of(
            new MetricsService.InstanceCountUtilization(100, 1.1),
            new MetricsService.InstanceCountUtilization(200, 1.2));

    assertThrows(
        IllegalArgumentException.class,
        () -> CpuScaling.makeRecommendation(cpuTarget, instanceCountUtilizations));
  }

  @Test
  public void makeRecommendation_negativeCurrentCpuUtilization_ignoresDataPoint() {
    MetricTarget cpuTarget =
        MetricTarget.builder()
            .type(MetricTarget.Type.UTILIZATION)
            .averageUtilization(60)
            .tolerance(0.1)
            .build();
    ImmutableList<MetricsService.InstanceCountUtilization> instanceCountUtilizations =
        ImmutableList.of(
            new MetricsService.InstanceCountUtilization(100, .9),
            new MetricsService.InstanceCountUtilization(200, -0.6));

    assertThat(CpuScaling.makeRecommendation(cpuTarget, instanceCountUtilizations))
        .isEqualTo(new CpuScaling.Recommendation(true, 150));
  }

  @Test
  public void makeRecommendation_currentCpuUtilizationGreaterThanOne_ignoresDataPoint() {
    MetricTarget cpuTarget =
        MetricTarget.builder()
            .type(MetricTarget.Type.UTILIZATION)
            .averageUtilization(60)
            .tolerance(0.1)
            .build();
    ImmutableList<MetricsService.InstanceCountUtilization> instanceCountUtilizations =
        ImmutableList.of(
            new MetricsService.InstanceCountUtilization(200, .9),
            new MetricsService.InstanceCountUtilization(200, 1.5));

    assertThat(CpuScaling.makeRecommendation(cpuTarget, instanceCountUtilizations))
        .isEqualTo(new CpuScaling.Recommendation(true, 300));
  }

  @Test
  public void makeRecommendation_negativeInstanceCount_ignoresDataPoint() {
    MetricTarget cpuTarget =
        MetricTarget.builder()
            .type(MetricTarget.Type.UTILIZATION)
            .averageUtilization(60)
            .tolerance(0.1)
            .build();
    ImmutableList<MetricsService.InstanceCountUtilization> instanceCountUtilizations =
        ImmutableList.of(
            new MetricsService.InstanceCountUtilization(300, .9),
            new MetricsService.InstanceCountUtilization(-200, .5));

    assertThat(CpuScaling.makeRecommendation(cpuTarget, instanceCountUtilizations))
        .isEqualTo(new CpuScaling.Recommendation(true, 450));
  }

  @Test
  public void makeRecommendation_dataPointAtLowerTolerance() {
    MetricTarget cpuTarget =
        MetricTarget.builder()
            .type(MetricTarget.Type.UTILIZATION)
            .averageUtilization(60)
            .tolerance(0.1)
            .build();
    double utilizationWithinTolerance = .55;
    ImmutableList<MetricsService.InstanceCountUtilization> instanceCountUtilizations =
        ImmutableList.of(
            new MetricsService.InstanceCountUtilization(100, .9),
            new MetricsService.InstanceCountUtilization(200, utilizationWithinTolerance));

    assertThat(CpuScaling.makeRecommendation(cpuTarget, instanceCountUtilizations))
        .isEqualTo(new CpuScaling.Recommendation(true, 175));
  }

  @Test
  public void makeRecommendation_dataPointAtUpperTolerance() {
    MetricTarget cpuTarget =
        MetricTarget.builder()
            .type(MetricTarget.Type.UTILIZATION)
            .averageUtilization(60)
            .tolerance(0.1)
            .build();
    double utilizationWithinTolerance = .65;
    ImmutableList<MetricsService.InstanceCountUtilization> instanceCountUtilizations =
        ImmutableList.of(
            new MetricsService.InstanceCountUtilization(100, .9),
            new MetricsService.InstanceCountUtilization(200, utilizationWithinTolerance));

    assertThat(CpuScaling.makeRecommendation(cpuTarget, instanceCountUtilizations))
        .isEqualTo(new CpuScaling.Recommendation(true, 175));
  }

  @Test
  public void makeRecommendation_dataPointsAboveTolerance() {
    MetricTarget cpuTarget =
        MetricTarget.builder()
            .type(MetricTarget.Type.UTILIZATION)
            .averageUtilization(60)
            .tolerance(0.1)
            .build();
    ImmutableList<MetricsService.InstanceCountUtilization> instanceCountUtilizations =
        ImmutableList.of(
            new MetricsService.InstanceCountUtilization(100, .9),
            new MetricsService.InstanceCountUtilization(200, .7));

    assertThat(CpuScaling.makeRecommendation(cpuTarget, instanceCountUtilizations))
        .isEqualTo(new CpuScaling.Recommendation(true, 192));
  }

  @Test
  public void makeRecommendation_dataPointsBelowTolerance() {
    MetricTarget cpuTarget =
        MetricTarget.builder()
            .type(MetricTarget.Type.UTILIZATION)
            .averageUtilization(60)
            .tolerance(0.1)
            .build();
    ImmutableList<MetricsService.InstanceCountUtilization> instanceCountUtilizations =
        ImmutableList.of(
            new MetricsService.InstanceCountUtilization(300, .2),
            new MetricsService.InstanceCountUtilization(300, .3));

    assertThat(CpuScaling.makeRecommendation(cpuTarget, instanceCountUtilizations))
        .isEqualTo(new CpuScaling.Recommendation(true, 125));
  }

  @Test
  public void makeRecommnedation_averageUtilizationBelowActivationThreshold_returnsInactive() {
    MetricTarget cpuTarget =
        MetricTarget.builder()
            .type(MetricTarget.Type.UTILIZATION)
            .tolerance(0.1)
            .averageUtilization(60)
            .activationThreshold(10)
            .build();

    ImmutableList<MetricsService.InstanceCountUtilization> instanceCountUtilizations =
        ImmutableList.of(
            new MetricsService.InstanceCountUtilization(300, .1),
            new MetricsService.InstanceCountUtilization(300, .09));

    // Average utilization is 9.5%
    assertThat(CpuScaling.makeRecommendation(cpuTarget, instanceCountUtilizations))
        .isEqualTo(new CpuScaling.Recommendation(false, 0));

    ImmutableList<MetricsService.InstanceCountUtilization> activeInstanceCountUtilizations =
        ImmutableList.of(
            new MetricsService.InstanceCountUtilization(300, .1),
            new MetricsService.InstanceCountUtilization(300, .11));

    CpuScaling.Recommendation recommendation =
        CpuScaling.makeRecommendation(cpuTarget, activeInstanceCountUtilizations);
    assertThat(recommendation.isActive()).isTrue();
    assertThat(recommendation.recommendedInstanceCount()).isGreaterThan(0);
    assertThat(recommendation.recommendedInstanceCount()).isLessThan(300);
  }
}
