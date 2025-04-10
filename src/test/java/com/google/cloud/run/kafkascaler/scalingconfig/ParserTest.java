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
import java.io.IOException;
import java.net.URLConnection;
import java.time.Duration;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public final class ParserTest {

  @Test
  public void loadsFromYaml() throws IOException {
    URLConnection urlConn = getClass().getResource("scaling.yaml").openConnection();
    // Turn off caching to ensure we get the latest version of the test file.
    urlConn.setUseCaches(false);
    ScalingConfig scalingConfig = Parser.load(urlConn.getInputStream());

    assertThat(scalingConfig.spec().scaleTargetRef().name())
        .isEqualTo("projects/my-project/locations/us-central1/workerpools/my-worker-pool");

    ImmutableList<Metric> metrics = scalingConfig.spec().metrics();
    assertThat(metrics).hasSize(2);

    Metric cpuMetric = metrics.get(0);
    assertThat(cpuMetric.type()).isEqualTo(Metric.Type.RESOURCE);
    assertThat(cpuMetric.resource().name()).isEqualTo("cpu");
    assertThat(cpuMetric.resource().target().type()).isEqualTo(MetricTarget.Type.UTILIZATION);
    assertThat(cpuMetric.resource().target().averageUtilization()).isEqualTo(50);
    assertThat(cpuMetric.resource().target().activationThreshold()).isEqualTo(40);
    assertThat(cpuMetric.resource().target().tolerance()).isEqualTo(.1);
    assertThat(cpuMetric.resource().target().windowSeconds()).isEqualTo(Duration.ofMinutes(1));

    Metric consumerLagMetric = metrics.get(1);

    assertThat(consumerLagMetric.type()).isEqualTo(Metric.Type.EXTERNAL);
    assertThat(consumerLagMetric.external().metric().name()).isEqualTo("consumer_lag");
    assertThat(consumerLagMetric.external().target().type()).isEqualTo(MetricTarget.Type.AVERAGE);
    assertThat(consumerLagMetric.external().target().averageValue()).isEqualTo(300);
    assertThat(consumerLagMetric.external().target().activationThreshold()).isEqualTo(100);
    assertThat(consumerLagMetric.external().target().tolerance()).isEqualTo(.1);

    Behavior behavior = scalingConfig.spec().behavior();

    assertThat(behavior.cooldownSeconds()).isEqualTo(Duration.ofMinutes(1));

    assertThat(behavior.scaleDown().stabilizationWindowSeconds()).isEqualTo(Duration.ofMinutes(5));

    assertThat(behavior.scaleDown().policies()).hasSize(1);
    assertThat(behavior.scaleDown().policies().get(0).type()).isEqualTo(Policy.Type.PERCENT);
    assertThat(behavior.scaleDown().policies().get(0).value()).isEqualTo(100);
    assertThat(behavior.scaleDown().policies().get(0).periodSeconds())
        .isEqualTo(Duration.ofSeconds(15));
    assertThat(behavior.scaleDown().selectPolicy()).isEqualTo(Scaling.SelectPolicy.MIN);

    assertThat(behavior.scaleUp().policies()).hasSize(2);
    assertThat(behavior.scaleUp().policies().get(0).type()).isEqualTo(Policy.Type.PERCENT);
    assertThat(behavior.scaleUp().policies().get(0).value()).isEqualTo(100);
    assertThat(behavior.scaleUp().policies().get(0).periodSeconds())
        .isEqualTo(Duration.ofSeconds(10));

    assertThat(behavior.scaleUp().policies().get(1).type()).isEqualTo(Policy.Type.INSTANCES);
    assertThat(behavior.scaleUp().policies().get(1).value()).isEqualTo(4);
    assertThat(behavior.scaleUp().policies().get(1).periodSeconds())
        .isEqualTo(Duration.ofSeconds(20));
    assertThat(behavior.scaleUp().selectPolicy()).isEqualTo(Scaling.SelectPolicy.MAX);
  }
}
