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

import static java.lang.Math.max;

import com.google.cloud.run.kafkascaler.scalingconfig.MetricTarget;

/**
 * Scaling based on lag and the lag threshold.
 *
 * <p>This scaling logic takes into account the scaling tolerance if one is configured. That is, if
 * the current lag is similar enough to the lag threshold, the recommendation will be the same as
 * the current instance count.
 */
public final class LagScaling {

  private LagScaling() {}

  /**
   * A record containing the scaling recommendation.
   *
   * @param isActive Whether scaling is active. If scaling is inactive, instances should be scaled
   *     down to 0 -- this should supercede any subsequent stabilization.
   * @param recommendedInstanceCount The recommended number of instances.
   */
  public static record Recommendation(boolean isActive, int recommendedInstanceCount) {}

  /**
   * Make a scaling recommendation based on the current lag and the configured lag target.
   *
   * @param lagTarget The configured lag target.
   * @param currentInstanceCount The current number of instances.
   * @param currentLag The current lag.
   * @return A recommendation for the number of instances.
   */
  public static Recommendation makeRecommendation(
      MetricTarget lagTarget, int currentInstanceCount, long currentLag) {
    if (currentLag <= lagTarget.activationThreshold()) {
      System.out.printf(
          "[LAG] Scaling inactive, current lag: %d, activation lag threshold: %d%n",
          currentLag, lagTarget.activationThreshold());
      return new Recommendation(false, 0);
    }

    int recommendedInstanceCount = currentInstanceCount;
    double scalingFactor = currentLag / (double) lagTarget.averageValue();
    double upperTolerance = (1 + lagTarget.tolerance()) * lagTarget.averageValue();
    double lowerTolerance = (1 - lagTarget.tolerance()) * lagTarget.averageValue();
    System.out.printf(
        "[LAG] Current Lag: %d, upper tolerance: %.2f, lower tolerance: %.2f\n",
        currentLag, upperTolerance, lowerTolerance);

    if (currentLag <= upperTolerance && currentLag >= lowerTolerance) {
      System.out.printf("[LAG] Within tolerance, no change%n");
    } else {
      recommendedInstanceCount = (int) Math.ceil(max(currentInstanceCount, 1) * scalingFactor);
      System.out.printf("[LAG] Recommended instance count: %d%n", recommendedInstanceCount);
    }

    return new Recommendation(true, recommendedInstanceCount);
  }
}
