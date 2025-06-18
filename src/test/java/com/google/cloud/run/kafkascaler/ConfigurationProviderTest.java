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

import com.google.cloud.run.kafkascaler.scalingconfig.Behavior;
import com.google.cloud.run.kafkascaler.scalingconfig.MetricTarget;
import com.google.cloud.run.kafkascaler.scalingconfig.Policy;
import com.google.cloud.run.kafkascaler.scalingconfig.ScaleTargetRef;
import com.google.cloud.run.kafkascaler.scalingconfig.ScalingConfig;
import com.google.cloud.run.kafkascaler.scalingconfig.Spec;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.net.URLConnection;
import java.time.Duration;
import java.util.Map;
import java.util.Properties;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public final class ConfigurationProviderTest {
  private static final String TOPIC_NAME = "test-topic";
  private static final String CONSUMER_GROUP_ID = "test-consumer-group";
  private static final String FULLY_QUALIFIED_CLOUD_TASKS_QUEUE_NAME =
      "projects/test-project/locations/test-region/queues/test-queue-name";
  private static final String INVOKER_SERVICE_ACCOUNT_EMAIL =
      "test-invoker-service-account-email@domain.com";
  private static final String SCALING_CONFIG_FILE = "scaling.yaml";

  private static class FakeEnvProvider implements ConfigurationProvider.EnvProvider {
    private final Map<String, String> envVars;

    FakeEnvProvider(Map<String, String> envVars) {
      this.envVars = envVars;
    }

    @Override
    public String getEnv(String name) {
      return envVars.get(name);
    }
  }

  private InputStream getInputStreamFromResource(String resourceName) throws IOException {
    URLConnection urlConn = getClass().getResource(resourceName).openConnection();
    // Turn off caching to ensure we get the latest version of the test file.
    urlConn.setUseCaches(false);
    return urlConn.getInputStream();
  }

  @Test
  public void workloadInfo_validConfig_returnsWorkloadInfo() throws IOException {
    ConfigurationProvider configuration =
        new ConfigurationProvider(new FakeEnvProvider(ImmutableMap.of()), SCALING_CONFIG_FILE);
    ScalingConfig scalingConfig =
        ScalingConfig.builder()
            .spec(
                Spec.builder()
                    .scaleTargetRef(
                        ScaleTargetRef.builder()
                            .name(
                                "projects/test-project/locations/test-location/services/test-service")
                            .build())
                    .metrics(ImmutableList.of())
                    .build())
            .build();

    WorkloadInfoParser.WorkloadInfo workloadInfo = configuration.workloadInfo(scalingConfig);
    assertThat(workloadInfo.projectId()).isEqualTo("test-project");
    assertThat(workloadInfo.location()).isEqualTo("test-location");
    assertThat(workloadInfo.name()).isEqualTo("test-service");
  }

  @Test
  public void staticConfig_missingKafkaTopicId_returnsEmptyOptionalTopicName() throws IOException {
    ConfigurationProvider configuration =
        new ConfigurationProvider(
            new FakeEnvProvider(ImmutableMap.of("CONSUMER_GROUP_ID", CONSUMER_GROUP_ID)),
            SCALING_CONFIG_FILE);

    ConfigurationProvider.StaticConfig staticConfig = configuration.staticConfig();
    assertThat(staticConfig.topicName()).isEmpty();
  }

  @Test
  public void staticConfig_missingconsumerGroupId_throwsIllegalArgumentException() {
    ConfigurationProvider configuration =
        new ConfigurationProvider(
            new FakeEnvProvider(ImmutableMap.of("KAFKA_TOPIC_ID", TOPIC_NAME)),
            SCALING_CONFIG_FILE);

    assertThrows(IllegalArgumentException.class, configuration::staticConfig);
  }

  @Test
  public void staticConfig_withoutUseMinInstancesFlag_returnsUseMinInstancesFalse()
      throws IOException {
    ConfigurationProvider configuration =
        new ConfigurationProvider(
            new FakeEnvProvider(
                ImmutableMap.of(
                    "KAFKA_TOPIC_ID", TOPIC_NAME, "CONSUMER_GROUP_ID", CONSUMER_GROUP_ID)),
            SCALING_CONFIG_FILE);

    ConfigurationProvider.StaticConfig staticConfig = configuration.staticConfig();
    assertThat(staticConfig.useMinInstances()).isFalse();
  }

  @Test
  public void staticConfig_withUseMinInstancesFlagFalse_useMinInstancesFalse() throws IOException {
    ConfigurationProvider configuration =
        new ConfigurationProvider(
            new FakeEnvProvider(
                ImmutableMap.of(
                    "KAFKA_TOPIC_ID", TOPIC_NAME, "CONSUMER_GROUP_ID", CONSUMER_GROUP_ID)),
            SCALING_CONFIG_FILE);

    ConfigurationProvider.StaticConfig staticConfig = configuration.staticConfig();
    assertThat(staticConfig.useMinInstances()).isFalse();
  }

  @Test
  public void staticConfig_withUseMinInstancesFlagTrue_useMinInstancesTrue() throws IOException {
    ConfigurationProvider configuration =
        new ConfigurationProvider(
            new FakeEnvProvider(
                ImmutableMap.of(
                    "KAFKA_TOPIC_ID",
                    TOPIC_NAME,
                    "CONSUMER_GROUP_ID",
                    CONSUMER_GROUP_ID,
                    "USE_MIN_INSTANCES",
                    "true")),
            SCALING_CONFIG_FILE);

    ConfigurationProvider.StaticConfig staticConfig = configuration.staticConfig();
    assertThat(staticConfig.useMinInstances()).isTrue();
  }

  @Test
  public void staticConfig_withoutOutputScalerMetricsFlag_returnsOutputScalerMetricsFalse()
      throws IOException {
    ConfigurationProvider configuration =
        new ConfigurationProvider(
            new FakeEnvProvider(
                ImmutableMap.of(
                    "KAFKA_TOPIC_ID", TOPIC_NAME, "CONSUMER_GROUP_ID", CONSUMER_GROUP_ID)),
            SCALING_CONFIG_FILE);

    ConfigurationProvider.StaticConfig staticConfig = configuration.staticConfig();
    assertThat(staticConfig.outputScalerMetrics()).isFalse();
  }

  @Test
  public void staticConfig_withOutputScalerMetricsFlag_returnsOutputScalerMetricsTrue()
      throws IOException {
    ConfigurationProvider configuration =
        new ConfigurationProvider(
            new FakeEnvProvider(
                ImmutableMap.of(
                    "KAFKA_TOPIC_ID",
                    TOPIC_NAME,
                    "CONSUMER_GROUP_ID",
                    CONSUMER_GROUP_ID,
                    "OUTPUT_SCALER_METRICS",
                    "TRUe")), // case-insensitive
            SCALING_CONFIG_FILE);

    ConfigurationProvider.StaticConfig staticConfig = configuration.staticConfig();
    assertThat(staticConfig.outputScalerMetrics()).isTrue();
  }

  @Test
  public void kafkaClientProperties_missingFile_throwsFileNotFoundException() {
    ConfigurationProvider configuration =
        new ConfigurationProvider(new FakeEnvProvider(ImmutableMap.of()), SCALING_CONFIG_FILE);
    assertThrows(
        FileNotFoundException.class, () -> configuration.kafkaClientProperties("/missing/file"));
  }

  @Test
  public void kafkaClientProperties_missingBootstrapServers_throwsIllegalArgumentException() {
    ConfigurationProvider configuration =
        new ConfigurationProvider(new FakeEnvProvider(ImmutableMap.of()), SCALING_CONFIG_FILE);
    assertThrows(
        IllegalArgumentException.class,
        () ->
            configuration.kafkaClientProperties(
                getInputStreamFromResource("bad_client_properties.yaml")));
  }

  @Test
  public void kafkaClientProperties_returnsKafkaClientProperties() throws IOException {
    ConfigurationProvider configuration =
        new ConfigurationProvider(new FakeEnvProvider(ImmutableMap.of()), SCALING_CONFIG_FILE);

    Properties properties =
        configuration.kafkaClientProperties(getInputStreamFromResource("client_properties.yaml"));

    assertThat(properties.getProperty("bootstrap.servers")).isEqualTo("my.broker:9092");
    assertThat(properties.getProperty("security.protocol")).isEqualTo("SASL_SSL");
    assertThat(properties.getProperty("sasl.mechanism")).isEqualTo("OAUTHBEARER");
  }

  @Test
  public void selfSchedulingConfig_cycleSecondsNotSet_returnsSelfSchedulingDisabledConfig() {
    // All self-scheduling env vars are set except cycle seconds.
    ConfigurationProvider configuration =
        new ConfigurationProvider(
            new FakeEnvProvider(
                ImmutableMap.of(
                    "FULLY_QUALIFIED_CLOUD_TASKS_QUEUE_NAME",
                    FULLY_QUALIFIED_CLOUD_TASKS_QUEUE_NAME,
                    "INVOKER_SERVICE_ACCOUNT_EMAIL",
                    INVOKER_SERVICE_ACCOUNT_EMAIL)),
            SCALING_CONFIG_FILE);

    ConfigurationProvider.SchedulingConfig schedulingConfig = configuration.selfSchedulingConfig();
    assertThat(schedulingConfig.cycleDuration()).isEqualTo(Duration.ofMinutes(1));
  }

  @Test
  public void selfSchedulingConfig_cycleSecondsSetButNotANumber_throwsIllegalArgumentException() {
    ConfigurationProvider configuration =
        new ConfigurationProvider(
            new FakeEnvProvider(
                ImmutableMap.of(
                    "CYCLE_SECONDS",
                    "NotANumber",
                    "FULLY_QUALIFIED_CLOUD_TASKS_QUEUE_NAME",
                    FULLY_QUALIFIED_CLOUD_TASKS_QUEUE_NAME,
                    "INVOKER_SERVICE_ACCOUNT_EMAIL",
                    INVOKER_SERVICE_ACCOUNT_EMAIL)),
            SCALING_CONFIG_FILE);

    assertThrows(IllegalArgumentException.class, configuration::selfSchedulingConfig);
  }

  @Test
  public void
      selfSchedulingConfig_missingMissingCloudTasksQueueName_throwsIllegalArgumentException() {
    // All self-scheduling env vars are set except cycle seconds.
    ConfigurationProvider configuration =
        new ConfigurationProvider(
            new FakeEnvProvider(
                ImmutableMap.of(
                    "CYCLE_SECONDS",
                    "20",
                    "INVOKER_SERVICE_ACCOUNT_EMAIL",
                    INVOKER_SERVICE_ACCOUNT_EMAIL)),
            SCALING_CONFIG_FILE);

    assertThrows(IllegalArgumentException.class, configuration::selfSchedulingConfig);
  }

  @Test
  public void
      selfSchedulingConfig_missingInvokerServiceAccountEmail_throwsIllegalArgumentException() {
    ConfigurationProvider configuration =
        new ConfigurationProvider(
            new FakeEnvProvider(
                ImmutableMap.of(
                    "CYCLE_SECONDS",
                    "20",
                    "FULLY_QUALIFIED_CLOUD_TASKS_QUEUE_NAME",
                    FULLY_QUALIFIED_CLOUD_TASKS_QUEUE_NAME)),
            SCALING_CONFIG_FILE);

    assertThrows(IllegalArgumentException.class, configuration::selfSchedulingConfig);
  }

  @Test
  public void selfSchedulingConfig_returnsSelfSchedulingConfig() {
    ConfigurationProvider configuration =
        new ConfigurationProvider(
            new FakeEnvProvider(
                ImmutableMap.of(
                    "CYCLE_SECONDS",
                    "20",
                    "FULLY_QUALIFIED_CLOUD_TASKS_QUEUE_NAME",
                    FULLY_QUALIFIED_CLOUD_TASKS_QUEUE_NAME,
                    "INVOKER_SERVICE_ACCOUNT_EMAIL",
                    INVOKER_SERVICE_ACCOUNT_EMAIL)),
            SCALING_CONFIG_FILE);

    ConfigurationProvider.SchedulingConfig schedulingConfig = configuration.selfSchedulingConfig();
    assertThat(schedulingConfig.fullyQualifiedCloudTaskQueueName())
        .isEqualTo(FULLY_QUALIFIED_CLOUD_TASKS_QUEUE_NAME);
    assertThat(schedulingConfig.invokerServiceAccountEmail())
        .isEqualTo(INVOKER_SERVICE_ACCOUNT_EMAIL);
    assertThat(schedulingConfig.cycleDuration()).isEqualTo(Duration.ofSeconds(20));
  }

  @Test
  public void scalingConfig_missingScalingConfigFile_throwsIOException() throws IOException {
    ConfigurationProvider configuration =
        new ConfigurationProvider(
            new FakeEnvProvider(
                ImmutableMap.of(
                    "KAFKA_TOPIC_ID", TOPIC_NAME, "CONSUMER_GROUP_ID", CONSUMER_GROUP_ID)),
            "/missing/file");
    assertThrows(IOException.class, configuration::scalingConfig);
  }

  @Test
  public void scalingConfig_validConfig_returnsScalingConfig() throws IOException {
    ConfigurationProvider configuration =
        new ConfigurationProvider(
            new FakeEnvProvider(
                ImmutableMap.of(
                    "KAFKA_TOPIC_ID", TOPIC_NAME, "CONSUMER_GROUP_ID", CONSUMER_GROUP_ID)),
            SCALING_CONFIG_FILE);

    ScalingConfig actual = configuration.scalingConfig(getInputStreamFromResource("scaling.yaml"));
    MetricTarget cpuTarget = actual.spec().metrics().get(0).resource().target();
    assertThat(cpuTarget.averageUtilization()).isEqualTo(50);
    assertThat(cpuTarget.tolerance()).isEqualTo(0.1);

    Behavior behavior = actual.spec().behavior();
    assertThat(behavior.scaleDown().stabilizationWindowSeconds()).isEqualTo(Duration.ofMinutes(1));
    assertThat(behavior.scaleDown().policies().get(0).type()).isEqualTo(Policy.Type.PERCENT);
    assertThat(behavior.scaleDown().policies().get(0).value()).isEqualTo(50);
    assertThat(behavior.scaleDown().policies().get(0).periodSeconds())
        .isEqualTo(Duration.ofSeconds(90));
  }

  @Test
  public void scalerUrl_returnsScalerUrl() throws IOException {
    ConfigurationProvider configuration =
        new ConfigurationProvider(
            new FakeEnvProvider(
                ImmutableMap.of(
                    "KAFKA_TOPIC_ID",
                    TOPIC_NAME,
                    "CONSUMER_GROUP_ID",
                    CONSUMER_GROUP_ID,
                    "K_SERVICE",
                    "scaler-service")),
            SCALING_CONFIG_FILE);

    String scalerUrl = configuration.scalerUrl("projects/12345/regions/us-central1");
    assertThat(scalerUrl).isEqualTo("https://scaler-service-12345.us-central1.run.app");
  }

  @Test
  public void scalerUrl_missingKService_throwsIllegalStateException() throws IOException {
    ConfigurationProvider configuration =
        new ConfigurationProvider(
            new FakeEnvProvider(
                ImmutableMap.of(
                    "KAFKA_TOPIC_ID", TOPIC_NAME, "CONSUMER_GROUP_ID", CONSUMER_GROUP_ID)),
            SCALING_CONFIG_FILE);

    assertThrows(
        IllegalStateException.class,
        () -> configuration.scalerUrl("projects/12345/regions/us-central1"));
  }

  @Test
  public void scalerUrl_withMalformedProjectNumberRegion_throwsIllegalArgumentException()
      throws IOException {
    ConfigurationProvider configuration =
        new ConfigurationProvider(
            new FakeEnvProvider(
                ImmutableMap.of(
                    "KAFKA_TOPIC_ID",
                    TOPIC_NAME,
                    "CONSUMER_GROUP_ID",
                    CONSUMER_GROUP_ID,
                    "K_SERVICE",
                    "scaler-service")),
            SCALING_CONFIG_FILE);

    assertThrows(
        IllegalArgumentException.class,
        () -> configuration.scalerUrl("projects/not-a-number/regions/us-central1"));
  }
}
