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

import com.google.cloud.run.kafkascaler.scalingconfig.MetricTarget;
import java.util.List;

/** A utility class for making scaling recommendations based on CPU utilization. */
public final class CpuScaling {

  private CpuScaling() {}

  /**
   * A record containing the scaling recommendation.
   *
   * @param isActive Whether scaling is active. If scaling is inactive, instances should be scaled
   *     down to 0 -- this should supercede any subsequent stabilization.
   * @param recommendedInstanceCount The recommended number of instances.
   */
  public static record Recommendation(boolean isActive, int recommendedInstanceCount) {}

  /**
   * Make a scaling recommendation based on CPU utilization.
   *
   * <p>CPU scaling works by iterating over the provided series of instance count-utilization pairs,
   * computing a recommendation for each point, and averaging the recommendations.
   *
   * @param cpuTarget The target CPU utilization and tolerance.
   * @param instanceCountUtilizations A list of instance counts and their corresponding CPU
   *     utilizations.
   * @return A recommendation for the number of instances to scale to.
   * @throws IllegalArgumentException if the target CPU utilization is not between 0 and 1.
   */
  public static Recommendation makeRecommendation(
      MetricTarget cpuTarget,
      List<MetricsService.InstanceCountUtilization> instanceCountUtilizations) {
    // Convert to fractions to ensure consistent units.
    double targetCpuUtilization = cpuTarget.averageUtilization() / 100.0;
    double activationUtilization = cpuTarget.activationThreshold() / 100.0;

    if (targetCpuUtilization < 0 || targetCpuUtilization > 1) {
      throw new IllegalArgumentException(
          "Target CPU utilization must be between 0 and 1 but was set to " + targetCpuUtilization);
    }
    if (instanceCountUtilizations == null || instanceCountUtilizations.isEmpty()) {
      System.out.printf("[CPU] Scaling inactive, no data points found%n");
      return new Recommendation(false, 0);
    }

    double upperTolerance = (1.0 + cpuTarget.tolerance()) * targetCpuUtilization;
    double lowerTolerance = (1.0 - cpuTarget.tolerance()) * targetCpuUtilization;

    // Track this field solely for logging
    double utilizationSum = 0;

    double recommendationSum = 0;
    int numDataPoints = 0;
    for (MetricsService.InstanceCountUtilization dataPoint : instanceCountUtilizations) {
      if (dataPoint.utilization() > 1
          || dataPoint.utilization() < 0
          || dataPoint.instanceCount() < 0) {
        // TODO: Make this a DEBUG level log when we start using a logging library with that
        // granularity.
        System.out.println("Ignoring invalid instance count-utilization data point: " + dataPoint);
        continue;
      }

      // Sum up recommendations and utilizations to take an average below.
      if (dataPoint.utilization() >= lowerTolerance && dataPoint.utilization() <= upperTolerance) {
        recommendationSum += dataPoint.instanceCount();
      } else {
        recommendationSum +=
            dataPoint.instanceCount() * (dataPoint.utilization() / targetCpuUtilization);
      }
      utilizationSum += dataPoint.utilization();
      numDataPoints++;
    }

    int recommendedInstanceCount = (int) Math.ceil(recommendationSum / numDataPoints);
    double averageUtilization = utilizationSum / numDataPoints;
    System.out.printf(
        "[CPU] Average utilization: %.3f, upper tolerance %.3f, lower tolerance %.3f%n",
        averageUtilization, upperTolerance, lowerTolerance);

    if (averageUtilization > activationUtilization) {
      System.out.printf("[CPU] Recommended instance count: %d%n", recommendedInstanceCount);
      return new Recommendation(true, recommendedInstanceCount);
    } else {
      System.out.printf(
          "[CPU] Scaling inactive. Average utilization: %.3f, activation threshold: %.3f%n",
          averageUtilization, activationUtilization);
      return new Recommendation(false, 0);
    }
  }
}
