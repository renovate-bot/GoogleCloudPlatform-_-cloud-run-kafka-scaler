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
import static org.junit.Assert.assertThrows;

import com.google.common.collect.ImmutableMap;
import java.time.Duration;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public final class PolicyTest {

  private static final String RESOURCE_PATH = "resourcePath";

  @Test
  public void fromYamlMap_missingType_throwsIllegalArgumentException() {
    ImmutableMap<String, Object> input = ImmutableMap.of("value", 100, "periodSeconds", 120);

    assertThrows(IllegalArgumentException.class, () -> Policy.fromYamlMap(input, RESOURCE_PATH));
  }

  @Test
  public void fromYamlMap_missingValue_throwsIllegalArgumentException() {
    ImmutableMap<String, Object> input = ImmutableMap.of("type", "Percent", "periodSeconds", 120);
    assertThrows(IllegalArgumentException.class, () -> Policy.fromYamlMap(input, RESOURCE_PATH));
  }

  @Test
  public void fromYamlMap_missingPeriodSeconds_throwsIllegalArgumentException() {
    ImmutableMap<String, Object> input = ImmutableMap.of("type", "Percent", "value", 100);
    assertThrows(IllegalArgumentException.class, () -> Policy.fromYamlMap(input, RESOURCE_PATH));
  }

  @Test
  public void fromYamlMap_returnsValidPercentPolicy() {
    ImmutableMap<String, Object> input =
        ImmutableMap.of("type", "Percent", "value", 100, "periodSeconds", 120);

    Policy actual = Policy.fromYamlMap(input, RESOURCE_PATH);
    assertThat(actual.type()).isEqualTo(Policy.Type.PERCENT);
    assertThat(actual.value()).isEqualTo(100);
    assertThat(actual.periodSeconds()).isEqualTo(Duration.ofMinutes(2));
  }

  @Test
  public void fromYamlMap_returnsValidInstancesPolicy() {
    ImmutableMap<String, Object> input =
        ImmutableMap.of("type", "Instances", "value", 100, "periodSeconds", 120);

    Policy actual = Policy.fromYamlMap(input, RESOURCE_PATH);
    assertThat(actual.type()).isEqualTo(Policy.Type.INSTANCES);
    assertThat(actual.value()).isEqualTo(100);
    assertThat(actual.periodSeconds()).isEqualTo(Duration.ofMinutes(2));
  }
}
