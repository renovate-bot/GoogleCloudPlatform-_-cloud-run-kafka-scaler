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

import com.google.common.collect.ImmutableList;
import java.time.Duration;

/** The default scaling config policy. */
public final class DefaultBehavior {

  private static final Duration DEFAULT_COOLDOWN = Duration.ZERO;

  private static final Policy SCALE_DOWN_100_PERCENT_WITH_15_SECOND_PERIOD_POLICY =
      Policy.builder()
          .type(Policy.Type.PERCENT)
          .value(100)
          .periodSeconds(Duration.ofSeconds(15))
          .build();
  private static final Policy SCALE_UP_100_PERCENT_WITH_15_SECOND_PERIOD_POLICY =
      Policy.builder()
          .type(Policy.Type.PERCENT)
          .value(100)
          .periodSeconds(Duration.ofSeconds(15))
          .build();
  private static final Policy SCALE_UP_4_INSTANCES_WITH_15_SECOND_PERIOD_POLICY =
      Policy.builder()
          .type(Policy.Type.INSTANCES)
          .value(4)
          .periodSeconds(Duration.ofSeconds(15))
          .build();
  private static final Scaling SCALEDOWN =
      Scaling.builder()
          .stabilizationWindowSeconds(Duration.ofMinutes(5))
          .policies(ImmutableList.of(SCALE_DOWN_100_PERCENT_WITH_15_SECOND_PERIOD_POLICY))
          .build();
  private static final Scaling SCALEUP =
      Scaling.builder()
          .stabilizationWindowSeconds(Duration.ofMinutes(5))
          .policies(
              ImmutableList.of(
                  SCALE_UP_100_PERCENT_WITH_15_SECOND_PERIOD_POLICY,
                  SCALE_UP_4_INSTANCES_WITH_15_SECOND_PERIOD_POLICY))
          .selectPolicy(Scaling.SelectPolicy.MAX)
          .build();

  public static final Behavior VALUE =
      Behavior.builder()
          .scaleDown(SCALEDOWN)
          .scaleUp(SCALEUP)
          .cooldownSeconds(DEFAULT_COOLDOWN)
          .build();

  private DefaultBehavior() {}
}
