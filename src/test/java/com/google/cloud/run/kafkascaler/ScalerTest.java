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

import static com.google.common.collect.ImmutableMap.toImmutableMap;
import static org.junit.Assert.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.google.cloud.run.kafkascaler.clients.CloudRunClientWrapper;
import com.google.cloud.run.kafkascaler.scalingconfig.Behavior;
import com.google.cloud.run.kafkascaler.scalingconfig.DefaultBehavior;
import com.google.cloud.run.kafkascaler.scalingconfig.External;
import com.google.cloud.run.kafkascaler.scalingconfig.Metric;
import com.google.cloud.run.kafkascaler.scalingconfig.MetricName;
import com.google.cloud.run.kafkascaler.scalingconfig.MetricTarget;
import com.google.cloud.run.kafkascaler.scalingconfig.Resource;
import com.google.cloud.run.kafkascaler.scalingconfig.ScaleTargetRef;
import com.google.cloud.run.kafkascaler.scalingconfig.ScalingConfig;
import com.google.cloud.run.kafkascaler.scalingconfig.Spec;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import java.io.IOException;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ExecutionException;
import org.apache.kafka.common.TopicPartition;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public final class ScalerTest {

  private Kafka kafka;
  private ScalingStabilizer scalingStabilizer;
  private CloudRunClientWrapper cloudRunClientWrapper;
  private MetricsService metricsService;
  private ConfigurationProvider configurationProvider;

  private static final String SCALING_CONFIG_LAG_METRIC_NAME = "consumer_lag";
  private static final String SCALING_CONFIG_CPU_METRIC_NAME = "cpu";

  private static final String TOPIC_NAME = "test-topic";
  private static final String CONSUMER_GROUP_ID = "test-consumer-group";
  private static final String PROJECT_ID = "test-project";
  private static final String LOCATION = "test-location";
  private static final String SERVICE_NAME = "test-service";
  private static final String WORKERPOOL_NAME = "test-workerpool";
  private static final int LAG_THREHSOLD = 1000;
  private static final int ACTIVATION_LAG_THRESHOLD = 500;
  private static final WorkloadInfoParser.WorkloadInfo SERVICE_WORKLOAD_INFO =
      new WorkloadInfoParser.WorkloadInfo(
          WorkloadInfoParser.WorkloadType.SERVICE, PROJECT_ID, LOCATION, SERVICE_NAME);
  private static final WorkloadInfoParser.WorkloadInfo WORKERPOOL_WORKLOAD_INFO =
      new WorkloadInfoParser.WorkloadInfo(
          WorkloadInfoParser.WorkloadType.WORKERPOOL, PROJECT_ID, LOCATION, WORKERPOOL_NAME);

  private static final MetricTarget LAG_TARGET =
      MetricTarget.builder()
          .type(MetricTarget.Type.AVERAGE)
          .averageValue(LAG_THREHSOLD)
          .activationThreshold(-1)
          .tolerance(0.1)
          .build();
  private static final Metric LAG_METRIC =
      Metric.builder()
          .type(Metric.Type.EXTERNAL)
          .external(
              External.builder()
                  .metric(MetricName.builder().name(SCALING_CONFIG_LAG_METRIC_NAME).build())
                  .target(LAG_TARGET)
                  .build())
          .build();

  private static final MetricTarget CPU_TARGET =
      MetricTarget.builder()
          .type(MetricTarget.Type.UTILIZATION)
          .averageUtilization(60)
          .tolerance(0.1)
          .build();
  private static final Metric CPU_METRIC =
      Metric.builder()
          .type(Metric.Type.RESOURCE)
          .resource(
              Resource.builder().name(SCALING_CONFIG_CPU_METRIC_NAME).target(CPU_TARGET).build())
          .build();

  private static final Behavior BEHAVIOR = DefaultBehavior.VALUE;
  private static final ScalingConfig SCALING_CONFIG =
      ScalingConfig.builder()
          .spec(
              Spec.builder()
                  .scaleTargetRef(
                      ScaleTargetRef.builder()
                          .name(
                              "projects/test-project/locations/test-location/services/test-service")
                          .build())
                  .metrics(ImmutableList.of(LAG_METRIC))
                  .behavior(BEHAVIOR)
                  .build())
          .build();

  private static final ConfigurationProvider.StaticConfig MANUAL_SCALING_STATIC_CONFIG =
      new ConfigurationProvider.StaticConfig(
          TOPIC_NAME,
          CONSUMER_GROUP_ID,
          /* useMinInstances= */ false,
          /* outputScalerMetrics= */ false);
  private static final ConfigurationProvider.StaticConfig AUTO_SCALING_STATIC_CONFIG =
      new ConfigurationProvider.StaticConfig(
          TOPIC_NAME,
          CONSUMER_GROUP_ID,
          /* useMinInstances= */ true,
          /* outputScalerMetrics= */ false);

  private static Optional<Map<TopicPartition, Long>> makeLagPerPartitionMap(
      Map<Integer, Long> lagPerPartition) {

    return Optional.of(
        lagPerPartition.entrySet().stream()
            .collect(
                toImmutableMap(
                    entry -> new TopicPartition(TOPIC_NAME, entry.getKey()), Map.Entry::getValue)));
  }

  @Before
  public void setUp() throws IOException, InterruptedException, ExecutionException {
    kafka = mock(Kafka.class);
    scalingStabilizer = mock(ScalingStabilizer.class);
    cloudRunClientWrapper = mock(CloudRunClientWrapper.class);
    metricsService = mock(MetricsService.class);
    configurationProvider = mock(ConfigurationProvider.class);

    when(kafka.doesTopicExist(TOPIC_NAME)).thenReturn(true);
    when(cloudRunClientWrapper.getServiceLastDeploymentTime(SERVICE_NAME))
        .thenReturn(Instant.now());
    when(cloudRunClientWrapper.getWorkerPoolLastDeploymentTime(WORKERPOOL_NAME))
        .thenReturn(Instant.now());
    when(configurationProvider.scalingConfig()).thenReturn(SCALING_CONFIG);
  }

  @Test
  public void constructor_minInstancesForWorkerPool_throwsIllegalArgumentException() {
    assertThrows(
        IllegalArgumentException.class,
        () ->
            new Scaler(
                kafka,
                scalingStabilizer,
                cloudRunClientWrapper,
                metricsService,
                WORKERPOOL_WORKLOAD_INFO,
                AUTO_SCALING_STATIC_CONFIG,
                configurationProvider));
  }

  @Test
  public void scale_noMetricsConfigured_doesNotChangeInstanceCount()
      throws IOException, InterruptedException, ExecutionException {
    Scaler scaler =
        new Scaler(
            kafka,
            scalingStabilizer,
            cloudRunClientWrapper,
            metricsService,
            WORKERPOOL_WORKLOAD_INFO,
            MANUAL_SCALING_STATIC_CONFIG,
            configurationProvider);

    ScalingConfig scalingConfig =
        SCALING_CONFIG.toBuilder()
            .spec(SCALING_CONFIG.spec().toBuilder().metrics(ImmutableList.of()).build())
            .build();

    when(configurationProvider.scalingConfig()).thenReturn(scalingConfig);

    int currentInstanceCount = 5;

    when(kafka.getCurrentConsumerCount(CONSUMER_GROUP_ID)).thenReturn(currentInstanceCount);
    when(cloudRunClientWrapper.getServiceInstanceCount(SERVICE_NAME))
        .thenReturn(currentInstanceCount);
    when(kafka.getLagPerPartition(TOPIC_NAME, CONSUMER_GROUP_ID))
        .thenReturn(makeLagPerPartitionMap(ImmutableMap.of(1, 1000L)));

    scaler.scale();

    verify(cloudRunClientWrapper, never()).updateServiceManualInstances(any(), anyInt());
    verify(cloudRunClientWrapper, never()).updateWorkerPoolManualInstances(any(), anyInt());
    verify(cloudRunClientWrapper, never()).updateServiceMinInstances(any(), anyInt());

    verify(scalingStabilizer, never()).markScaleEvent(any(), any(), anyInt(), anyInt());
  }

  @Test
  public void scale_topicDoesNotExist_doesNotCallAnyDependencies()
      throws IOException, InterruptedException, ExecutionException {
    ConfigurationProvider.StaticConfig nonExistentTopicConfig =
        new ConfigurationProvider.StaticConfig(
            "non-existent-topic",
            CONSUMER_GROUP_ID,
            /* useMinInstances= */ false,
            /* outputScalerMetrics= */ true);
    Scaler scaler =
        new Scaler(
            kafka,
            scalingStabilizer,
            cloudRunClientWrapper,
            metricsService,
            SERVICE_WORKLOAD_INFO,
            nonExistentTopicConfig,
            configurationProvider);

    assertThrows(IllegalArgumentException.class, scaler::scale);

    verify(cloudRunClientWrapper, never()).getServiceInstanceCount(any());
    verify(cloudRunClientWrapper, never()).getWorkerPoolInstanceCount(any());
    verify(cloudRunClientWrapper, never()).updateServiceManualInstances(any(), anyInt());
    verify(cloudRunClientWrapper, never()).updateWorkerPoolManualInstances(any(), anyInt());
    verify(cloudRunClientWrapper, never()).updateServiceMinInstances(any(), anyInt());

    verify(kafka, never()).getLagPerPartition(any(), any());

    verify(scalingStabilizer, never()).markScaleEvent(any(), any(), anyInt(), anyInt());
  }

  @Test
  public void
      scale_currentInstanceCountDoesNotMatchRequestedInstanceCount_doesNotCallAnyDependencies()
          throws IOException, InterruptedException, ExecutionException {
    Scaler scaler =
        new Scaler(
            kafka,
            scalingStabilizer,
            cloudRunClientWrapper,
            metricsService,
            SERVICE_WORKLOAD_INFO,
            MANUAL_SCALING_STATIC_CONFIG,
            configurationProvider);


    when(kafka.getCurrentConsumerCount(CONSUMER_GROUP_ID)).thenReturn(0);
    when(cloudRunClientWrapper.getServiceInstanceCount(SERVICE_NAME))
        .thenReturn(5);

    scaler.scale();

    verify(cloudRunClientWrapper, never()).updateServiceManualInstances(any(), anyInt());
    verify(cloudRunClientWrapper, never()).updateWorkerPoolManualInstances(any(), anyInt());
    verify(cloudRunClientWrapper, never()).updateServiceMinInstances(any(), anyInt());

    verify(kafka, never()).getLagPerPartition(any(), any());

    verify(scalingStabilizer, never()).markScaleEvent(any(), any(), anyInt(), anyInt());
  }

  @Test
  public void scale_topicExistsButCurrentLagIsEmpty_throwsAssertionError()
      throws IOException, InterruptedException, ExecutionException {
    Scaler scaler =
        new Scaler(
            kafka,
            scalingStabilizer,
            cloudRunClientWrapper,
            metricsService,
            SERVICE_WORKLOAD_INFO,
            MANUAL_SCALING_STATIC_CONFIG,
            configurationProvider);

    when(kafka.getLagPerPartition(any(), any())).thenReturn(Optional.empty());

    assertThrows(AssertionError.class, scaler::scale);
    verify(cloudRunClientWrapper, never()).updateServiceManualInstances(any(), anyInt());
    verify(cloudRunClientWrapper, never()).updateWorkerPoolManualInstances(any(), anyInt());
    verify(cloudRunClientWrapper, never()).updateServiceMinInstances(any(), anyInt());
    verify(scalingStabilizer, never()).markScaleEvent(any(), any(), anyInt(), anyInt());
  }

  @Test
  public void scale_getServiceInstanceCountException_doesNotUpdateConsumerCount()
      throws IOException, InterruptedException, ExecutionException {
    Scaler scaler =
        new Scaler(
            kafka,
            scalingStabilizer,
            cloudRunClientWrapper,
            metricsService,
            SERVICE_WORKLOAD_INFO,
            MANUAL_SCALING_STATIC_CONFIG,
            configurationProvider);

    when(cloudRunClientWrapper.getServiceInstanceCount(any()))
        .thenThrow(new IOException("test exception"));

    assertThrows(IOException.class, scaler::scale);

    verify(kafka, never()).getLagPerPartition(any(), any());
    verify(cloudRunClientWrapper, never()).updateServiceManualInstances(any(), anyInt());
    verify(cloudRunClientWrapper, never()).updateWorkerPoolManualInstances(any(), anyInt());
    verify(cloudRunClientWrapper, never()).updateServiceMinInstances(any(), anyInt());
    verify(scalingStabilizer, never()).markScaleEvent(any(), any(), anyInt(), anyInt());
  }

  @Test
  public void scale_getWorkerPoolInstanceCountException_doesNotUpdateConsumerCount()
      throws IOException, InterruptedException, ExecutionException {
    Scaler scaler =
        new Scaler(
            kafka,
            scalingStabilizer,
            cloudRunClientWrapper,
            metricsService,
            WORKERPOOL_WORKLOAD_INFO,
            MANUAL_SCALING_STATIC_CONFIG,
            configurationProvider);

    when(cloudRunClientWrapper.getWorkerPoolInstanceCount(any()))
        .thenThrow(new IOException("test exception"));

    assertThrows(IOException.class, scaler::scale);

    verify(kafka, never()).getLagPerPartition(any(), any());
    verify(cloudRunClientWrapper, never()).updateServiceManualInstances(any(), anyInt());
    verify(cloudRunClientWrapper, never()).updateWorkerPoolManualInstances(any(), anyInt());
    verify(cloudRunClientWrapper, never()).updateServiceMinInstances(any(), anyInt());
    verify(scalingStabilizer, never()).markScaleEvent(any(), any(), anyInt(), anyInt());
  }

  @Test
  public void scale_getLagPerPartitionConsumerCountException_doesNotUpdateConsumerCount()
      throws IOException, InterruptedException, ExecutionException {
    Scaler scaler =
        new Scaler(
            kafka,
            scalingStabilizer,
            cloudRunClientWrapper,
            metricsService,
            WORKERPOOL_WORKLOAD_INFO,
            MANUAL_SCALING_STATIC_CONFIG,
            configurationProvider);

    when(kafka.getLagPerPartition(any(), any()))
        .thenThrow(new InterruptedException("test exception"))
        .thenThrow(new ExecutionException(new Exception()));

    assertThrows(InterruptedException.class, scaler::scale);
    assertThrows(ExecutionException.class, scaler::scale);

    verify(cloudRunClientWrapper, never()).updateServiceManualInstances(any(), anyInt());
    verify(cloudRunClientWrapper, never()).updateWorkerPoolManualInstances(any(), anyInt());
    verify(cloudRunClientWrapper, never()).updateServiceMinInstances(any(), anyInt());
    verify(scalingStabilizer, never()).markScaleEvent(any(), any(), anyInt(), anyInt());
  }

  @Test
  public void scale_unchangedRecommendation_doesNotUpdateInstances()
      throws IOException, InterruptedException, ExecutionException {
    Scaler scaler =
        new Scaler(
            kafka,
            scalingStabilizer,
            cloudRunClientWrapper,
            metricsService,
            SERVICE_WORKLOAD_INFO,
            MANUAL_SCALING_STATIC_CONFIG,
            configurationProvider);

    int currentInstanceCount = 5;

    when(kafka.getCurrentConsumerCount(CONSUMER_GROUP_ID)).thenReturn(currentInstanceCount);
    when(cloudRunClientWrapper.getServiceInstanceCount(SERVICE_NAME))
        .thenReturn(currentInstanceCount);
    when(kafka.getLagPerPartition(TOPIC_NAME, CONSUMER_GROUP_ID))
        .thenReturn(makeLagPerPartitionMap(ImmutableMap.of(1, 1000L)));
    when(scalingStabilizer.getBoundedRecommendation(
            eq(BEHAVIOR), any(), eq(currentInstanceCount), anyInt()))
        .thenReturn(currentInstanceCount);

    scaler.scale();

    verify(cloudRunClientWrapper, never()).updateServiceManualInstances(any(), anyInt());
    verify(cloudRunClientWrapper, never()).updateWorkerPoolManualInstances(any(), anyInt());
    verify(cloudRunClientWrapper, never()).updateServiceMinInstances(any(), anyInt());
    verify(scalingStabilizer, never()).markScaleEvent(any(), any(), anyInt(), anyInt());
  }

  @Test
  public void scale_withBoundedRecommendation_updatesServiceManualInstanceCount()
      throws IOException, InterruptedException, ExecutionException {
    Scaler scaler =
        new Scaler(
            kafka,
            scalingStabilizer,
            cloudRunClientWrapper,
            metricsService,
            SERVICE_WORKLOAD_INFO,
            MANUAL_SCALING_STATIC_CONFIG,
            configurationProvider);

    int currentInstanceCount = 5;
    int newInstanceCount = 10;

    when(kafka.getCurrentConsumerCount(CONSUMER_GROUP_ID)).thenReturn(currentInstanceCount);
    when(cloudRunClientWrapper.getServiceInstanceCount(SERVICE_NAME))
        .thenReturn(currentInstanceCount);
    when(kafka.getLagPerPartition(TOPIC_NAME, CONSUMER_GROUP_ID))
        .thenReturn(makeLagPerPartitionMap(ImmutableMap.of(1, 1000L)));
    when(scalingStabilizer.getBoundedRecommendation(
            eq(BEHAVIOR), any(), eq(currentInstanceCount), anyInt()))
        .thenReturn(newInstanceCount);

    scaler.scale();

    verify(cloudRunClientWrapper)
        .updateServiceManualInstances(SERVICE_WORKLOAD_INFO.name(), newInstanceCount);
    verify(scalingStabilizer).markScaleEvent(eq(BEHAVIOR), any(), anyInt(), eq(newInstanceCount));
  }

  @Test
  public void scale_withOutputMetricsEnabled_outputsMetrics()
      throws IOException, InterruptedException, ExecutionException {

    ConfigurationProvider.StaticConfig outputMetricsStaticConfig =
        new ConfigurationProvider.StaticConfig(
            TOPIC_NAME,
            CONSUMER_GROUP_ID,
            /* useMinInstances= */ false,
            /* outputScalerMetrics= */ true);
    Scaler scaler =
        new Scaler(
            kafka,
            scalingStabilizer,
            cloudRunClientWrapper,
            metricsService,
            SERVICE_WORKLOAD_INFO,
            outputMetricsStaticConfig,
            configurationProvider);

    int currentInstanceCount = 5;
    int newInstanceCount = 10;

    when(kafka.getCurrentConsumerCount(CONSUMER_GROUP_ID)).thenReturn(currentInstanceCount);
    when(cloudRunClientWrapper.getServiceInstanceCount(SERVICE_NAME))
        .thenReturn(currentInstanceCount);
    when(kafka.getLagPerPartition(TOPIC_NAME, CONSUMER_GROUP_ID))
        .thenReturn(makeLagPerPartitionMap(ImmutableMap.of(1, 1000L)));
    when(scalingStabilizer.getBoundedRecommendation(
            eq(BEHAVIOR), any(), eq(currentInstanceCount), anyInt()))
        .thenReturn(newInstanceCount);

    scaler.scale();

    verify(metricsService, times(3)).writeMetricIgnoreFailure(any(), anyDouble(), any());
  }

  @Test
  public void scale_withOutputMetricsDisabled_doesNotOutputMetrics()
      throws IOException, InterruptedException, ExecutionException {

    ConfigurationProvider.StaticConfig outputMetricsStaticConfig =
        new ConfigurationProvider.StaticConfig(
            TOPIC_NAME,
            CONSUMER_GROUP_ID,
            /* useMinInstances= */ false,
            /* outputScalerMetrics= */ false);
    Scaler scaler =
        new Scaler(
            kafka,
            scalingStabilizer,
            cloudRunClientWrapper,
            metricsService,
            SERVICE_WORKLOAD_INFO,
            outputMetricsStaticConfig,
            configurationProvider);

    int currentInstanceCount = 5;
    int newInstanceCount = 10;

    when(kafka.getCurrentConsumerCount(CONSUMER_GROUP_ID)).thenReturn(currentInstanceCount);
    when(cloudRunClientWrapper.getServiceInstanceCount(SERVICE_NAME))
        .thenReturn(currentInstanceCount);
    when(kafka.getLagPerPartition(TOPIC_NAME, CONSUMER_GROUP_ID))
        .thenReturn(makeLagPerPartitionMap(ImmutableMap.of(1, 1000L)));
    when(scalingStabilizer.getBoundedRecommendation(
            eq(BEHAVIOR), any(), eq(currentInstanceCount), anyInt()))
        .thenReturn(newInstanceCount);

    scaler.scale();

    verify(metricsService, never()).writeMetricIgnoreFailure(any(), anyDouble(), any());
  }

  @Test
  public void scale_withBoundedRecommendation_updatesWorkerpoolManualInstanceCount()
      throws IOException, InterruptedException, ExecutionException {
    Scaler scaler =
        new Scaler(
            kafka,
            scalingStabilizer,
            cloudRunClientWrapper,
            metricsService,
            WORKERPOOL_WORKLOAD_INFO,
            MANUAL_SCALING_STATIC_CONFIG,
            configurationProvider);

    int currentInstanceCount = 25;
    int newInstanceCount = 10;

    when(kafka.getCurrentConsumerCount(CONSUMER_GROUP_ID)).thenReturn(currentInstanceCount);
    when(cloudRunClientWrapper.getWorkerPoolInstanceCount(WORKERPOOL_NAME))
        .thenReturn(currentInstanceCount);
    when(kafka.getLagPerPartition(TOPIC_NAME, CONSUMER_GROUP_ID))
        .thenReturn(makeLagPerPartitionMap(ImmutableMap.of(1, 1000L)));
    when(scalingStabilizer.getBoundedRecommendation(
            eq(BEHAVIOR), any(), eq(currentInstanceCount), anyInt()))
        .thenReturn(newInstanceCount);

    scaler.scale();

    verify(cloudRunClientWrapper)
        .updateWorkerPoolManualInstances(WORKERPOOL_WORKLOAD_INFO.name(), newInstanceCount);
    verify(scalingStabilizer).markScaleEvent(eq(BEHAVIOR), any(), anyInt(), eq(newInstanceCount));
  }

  @Test
  public void scale_withBoundedRecommendation_updatesServiceMinInstanceCount()
      throws IOException, InterruptedException, ExecutionException {
    Scaler scaler =
        new Scaler(
            kafka,
            scalingStabilizer,
            cloudRunClientWrapper,
            metricsService,
            SERVICE_WORKLOAD_INFO,
            AUTO_SCALING_STATIC_CONFIG,
            configurationProvider);

    int currentInstanceCount = 5;
    int newInstanceCount = 10;

    when(kafka.getCurrentConsumerCount(CONSUMER_GROUP_ID)).thenReturn(currentInstanceCount);
    when(cloudRunClientWrapper.getServiceInstanceCount(SERVICE_NAME))
        .thenReturn(currentInstanceCount);
    when(kafka.getLagPerPartition(TOPIC_NAME, CONSUMER_GROUP_ID))
        .thenReturn(makeLagPerPartitionMap(ImmutableMap.of(1, 1000L)));
    when(scalingStabilizer.getBoundedRecommendation(
            eq(BEHAVIOR), any(), eq(currentInstanceCount), anyInt()))
        .thenReturn(newInstanceCount);

    scaler.scale();

    verify(cloudRunClientWrapper)
        .updateServiceMinInstances(SERVICE_WORKLOAD_INFO.name(), newInstanceCount);
    verify(scalingStabilizer).markScaleEvent(eq(BEHAVIOR), any(), anyInt(), eq(newInstanceCount));
  }

  @Test
  public void scale_recommendationGreaterThanPartitions_updatesWorkerPoolToNumberOfPartitions()
      throws IOException, InterruptedException, ExecutionException {
    Scaler scaler =
        new Scaler(
            kafka,
            scalingStabilizer,
            cloudRunClientWrapper,
            metricsService,
            WORKERPOOL_WORKLOAD_INFO,
            MANUAL_SCALING_STATIC_CONFIG,
            configurationProvider);

    int currentInstanceCount = 25;

    // 3000 lag across 2 partitions
    Optional<Map<TopicPartition, Long>> lagPerPartition =
        makeLagPerPartitionMap(ImmutableMap.of(1, 1000L, 2, 2000L));

    when(kafka.getCurrentConsumerCount(CONSUMER_GROUP_ID)).thenReturn(currentInstanceCount);
    when(cloudRunClientWrapper.getWorkerPoolInstanceCount(WORKERPOOL_NAME))
        .thenReturn(currentInstanceCount);
    when(kafka.getLagPerPartition(TOPIC_NAME, CONSUMER_GROUP_ID)).thenReturn(lagPerPartition);

    int newInstanceCount = 1;
    // Recommendation should be 3000/1000 = 3 but we verify that this is limited to the number of
    // partitions (2) before being passed to the stabilizer.
    when(scalingStabilizer.getBoundedRecommendation(
            eq(BEHAVIOR), any(), eq(currentInstanceCount), eq(2)))
        .thenReturn(newInstanceCount);

    scaler.scale();

    verify(cloudRunClientWrapper)
        .updateWorkerPoolManualInstances(WORKERPOOL_WORKLOAD_INFO.name(), newInstanceCount);
    verify(scalingStabilizer).markScaleEvent(eq(BEHAVIOR), any(), anyInt(), eq(newInstanceCount));
  }

  @Test
  public void scale_lagIsLowerThanActivationLagThreshold_respectsBoundedRecommendation()
      throws IOException, InterruptedException, ExecutionException {
    MetricTarget lagTarget =
        MetricTarget.builder()
            .type(MetricTarget.Type.AVERAGE)
            .averageValue(LAG_THREHSOLD)
            .activationThreshold(ACTIVATION_LAG_THRESHOLD)
            .tolerance(0.1)
            .build();
    Metric lagMetric =
        Metric.builder()
            .type(Metric.Type.EXTERNAL)
            .external(
                External.builder()
                    .metric(MetricName.builder().name(SCALING_CONFIG_LAG_METRIC_NAME).build())
                    .target(lagTarget)
                    .build())
            .build();
    ScalingConfig scalingConfig =
        SCALING_CONFIG.toBuilder()
            .spec(SCALING_CONFIG.spec().toBuilder().metrics(ImmutableList.of(lagMetric)).build())
            .build();

    when(configurationProvider.scalingConfig()).thenReturn(scalingConfig);
    Scaler scaler =
        new Scaler(
            kafka,
            scalingStabilizer,
            cloudRunClientWrapper,
            metricsService,
            WORKERPOOL_WORKLOAD_INFO,
            MANUAL_SCALING_STATIC_CONFIG,
            configurationProvider);

    int currentInstanceCount = 25;
    Optional<Map<TopicPartition, Long>> lagPerPartition =
        makeLagPerPartitionMap(ImmutableMap.of(1, 0L));
    when(kafka.getCurrentConsumerCount(CONSUMER_GROUP_ID)).thenReturn(currentInstanceCount);
    when(cloudRunClientWrapper.getWorkerPoolInstanceCount(WORKERPOOL_NAME))
        .thenReturn(currentInstanceCount);
    when(kafka.getLagPerPartition(TOPIC_NAME, CONSUMER_GROUP_ID)).thenReturn(lagPerPartition);

    int newInstanceCount = 999;
    when(scalingStabilizer.getBoundedRecommendation(any(), any(), anyInt(), eq(0)))
        .thenReturn(newInstanceCount);

    scaler.scale();

    verify(cloudRunClientWrapper)
        .updateWorkerPoolManualInstances(WORKERPOOL_WORKLOAD_INFO.name(), newInstanceCount);
    verify(scalingStabilizer).markScaleEvent(any(), any(), anyInt(), eq(newInstanceCount));
  }

  @Test
  public void scale_lastDeploymentTimeIsRecent_doesNotUpdateService()
      throws IOException, InterruptedException, ExecutionException {
    Duration cooldownSeconds = Duration.ofSeconds(60);

    ScalingConfig scalingConfig =
        SCALING_CONFIG.toBuilder()
            .spec(
                SCALING_CONFIG.spec().toBuilder()
                    .behavior(Behavior.builder().cooldownSeconds(cooldownSeconds).build())
                    .build())
            .build();
    when(configurationProvider.scalingConfig()).thenReturn(scalingConfig);

    Scaler scaler =
        new Scaler(
            kafka,
            scalingStabilizer,
            cloudRunClientWrapper,
            metricsService,
            SERVICE_WORKLOAD_INFO,
            MANUAL_SCALING_STATIC_CONFIG,
            configurationProvider);

    Optional<Map<TopicPartition, Long>> lagPerPartition =
        makeLagPerPartitionMap(ImmutableMap.of(1, 0L));
    when(kafka.getLagPerPartition(TOPIC_NAME, CONSUMER_GROUP_ID)).thenReturn(lagPerPartition);

    int newInstanceCount = 1;
    when(scalingStabilizer.getBoundedRecommendation(eq(BEHAVIOR), any(), anyInt(), anyInt()))
        .thenReturn(newInstanceCount);

    when(cloudRunClientWrapper.getServiceLastDeploymentTime(SERVICE_NAME))
        .thenReturn(Instant.now());

    scaler.scale();
    verify(cloudRunClientWrapper, never()).updateServiceManualInstances(any(), anyInt());
  }

  @Test
  public void scale_lastDeploymentTimeIsRecent_doesNotUpdateWorkerPool()
      throws IOException, InterruptedException, ExecutionException {
    Duration cooldownSeconds = Duration.ofSeconds(60);

    ScalingConfig scalingConfig =
        SCALING_CONFIG.toBuilder()
            .spec(
                SCALING_CONFIG.spec().toBuilder()
                    .behavior(Behavior.builder().cooldownSeconds(cooldownSeconds).build())
                    .build())
            .build();
    when(configurationProvider.scalingConfig()).thenReturn(scalingConfig);

    Scaler scaler =
        new Scaler(
            kafka,
            scalingStabilizer,
            cloudRunClientWrapper,
            metricsService,
            WORKERPOOL_WORKLOAD_INFO,
            MANUAL_SCALING_STATIC_CONFIG,
            configurationProvider);

    Optional<Map<TopicPartition, Long>> lagPerPartition =
        makeLagPerPartitionMap(ImmutableMap.of(1, 0L));
    when(kafka.getLagPerPartition(TOPIC_NAME, CONSUMER_GROUP_ID)).thenReturn(lagPerPartition);

    int newInstanceCount = 1;
    when(scalingStabilizer.getBoundedRecommendation(any(), any(), anyInt(), anyInt()))
        .thenReturn(newInstanceCount);

    when(cloudRunClientWrapper.getWorkerPoolLastDeploymentTime(WORKERPOOL_NAME))
        .thenReturn(Instant.now());

    scaler.scale();
    verify(cloudRunClientWrapper, never()).updateWorkerPoolManualInstances(any(), anyInt());
  }

  @Test
  public void scale_lastDeploymentIsOldEnough_updatesService()
      throws IOException, InterruptedException, ExecutionException {
    Duration cooldownSeconds = Duration.ofSeconds(60);
    Instant lastUpdateTime = Instant.now().minus(cooldownSeconds.plusSeconds(1));

    Scaler scaler =
        new Scaler(
            kafka,
            scalingStabilizer,
            cloudRunClientWrapper,
            metricsService,
            SERVICE_WORKLOAD_INFO,
            MANUAL_SCALING_STATIC_CONFIG,
            configurationProvider);

    Optional<Map<TopicPartition, Long>> lagPerPartition =
        makeLagPerPartitionMap(ImmutableMap.of(1, 0L));
    when(kafka.getLagPerPartition(TOPIC_NAME, CONSUMER_GROUP_ID)).thenReturn(lagPerPartition);

    int newInstanceCount = 1;
    when(scalingStabilizer.getBoundedRecommendation(eq(BEHAVIOR), any(), anyInt(), anyInt()))
        .thenReturn(newInstanceCount);

    when(cloudRunClientWrapper.getServiceLastDeploymentTime(SERVICE_NAME))
        .thenReturn(lastUpdateTime);

    scaler.scale();
    verify(cloudRunClientWrapper)
        .updateServiceManualInstances(eq(SERVICE_NAME), eq(newInstanceCount));
  }

  @Test
  public void scale_lastDeploymentIsOldEnough_updatesWorkerPool()
      throws IOException, InterruptedException, ExecutionException {
    Duration cooldownSeconds = Duration.ofSeconds(60);
    Instant lastUpdateTime = Instant.now().minus(cooldownSeconds.plusSeconds(1));

    Scaler scaler =
        new Scaler(
            kafka,
            scalingStabilizer,
            cloudRunClientWrapper,
            metricsService,
            WORKERPOOL_WORKLOAD_INFO,
            MANUAL_SCALING_STATIC_CONFIG,
            configurationProvider);

    Optional<Map<TopicPartition, Long>> lagPerPartition =
        makeLagPerPartitionMap(ImmutableMap.of(1, 0L));
    when(kafka.getLagPerPartition(TOPIC_NAME, CONSUMER_GROUP_ID)).thenReturn(lagPerPartition);

    int newInstanceCount = 1;
    when(scalingStabilizer.getBoundedRecommendation(eq(BEHAVIOR), any(), anyInt(), anyInt()))
        .thenReturn(newInstanceCount);

    when(cloudRunClientWrapper.getWorkerPoolLastDeploymentTime(WORKERPOOL_NAME))
        .thenReturn(lastUpdateTime);

    scaler.scale();
    verify(cloudRunClientWrapper)
        .updateWorkerPoolManualInstances(eq(WORKERPOOL_NAME), eq(newInstanceCount));
  }

  @Test
  public void scale_withCpuScaling_updatesService()
      throws IOException, InterruptedException, ExecutionException {

    MetricTarget cpuTarget =
        MetricTarget.builder()
            .type(MetricTarget.Type.UTILIZATION)
            .averageUtilization(50)
            .activationThreshold(-1)
            .tolerance(0.1)
            .build();
    Metric cpuMetric =
        Metric.builder()
            .type(Metric.Type.RESOURCE)
            .resource(
                Resource.builder().name(SCALING_CONFIG_CPU_METRIC_NAME).target(cpuTarget).build())
            .build();
    ScalingConfig scalingConfig =
        SCALING_CONFIG.toBuilder()
            .spec(
                SCALING_CONFIG.spec().toBuilder()
                    .metrics(ImmutableList.of(LAG_METRIC, cpuMetric))
                    .build())
            .build();

    when(configurationProvider.scalingConfig()).thenReturn(scalingConfig);

    Scaler scaler =
        new Scaler(
            kafka,
            scalingStabilizer,
            cloudRunClientWrapper,
            metricsService,
            SERVICE_WORKLOAD_INFO,
            MANUAL_SCALING_STATIC_CONFIG,
            configurationProvider);

    when(metricsService.getServiceCpuUtilizationData(any(), any()))
        .thenReturn(
            Optional.of(
                ImmutableList.of(
                    new MetricsService.InstanceCountUtilization(1, .9),
                    new MetricsService.InstanceCountUtilization(2, .5),
                    new MetricsService.InstanceCountUtilization(3, .4))));

    int currentInstanceCount = 5;
    when(kafka.getCurrentConsumerCount(CONSUMER_GROUP_ID)).thenReturn(currentInstanceCount);
    when(cloudRunClientWrapper.getServiceInstanceCount(SERVICE_NAME))
        .thenReturn(currentInstanceCount);
    when(kafka.getLagPerPartition(TOPIC_NAME, CONSUMER_GROUP_ID))
        .thenReturn(makeLagPerPartitionMap(ImmutableMap.of(1, 0L, 2, 0L, 3, 0L)));

    int newInstanceCount = 10;
    when(scalingStabilizer.getBoundedRecommendation(
            eq(BEHAVIOR), any(), eq(currentInstanceCount), eq(3)))
        .thenReturn(newInstanceCount);

    scaler.scale();

    verify(cloudRunClientWrapper)
        .updateServiceManualInstances(SERVICE_WORKLOAD_INFO.name(), newInstanceCount);
    verify(scalingStabilizer).markScaleEvent(eq(BEHAVIOR), any(), anyInt(), eq(newInstanceCount));
  }

  @Test
  public void scale_withCpuScalingOnly_updatesWorkerPool()
      throws IOException, InterruptedException, ExecutionException {
    MetricTarget cpuTarget =
        MetricTarget.builder()
            .type(MetricTarget.Type.UTILIZATION)
            .averageUtilization(50)
            .activationThreshold(-1)
            .tolerance(0.1)
            .build();
    Metric cpuMetric =
        Metric.builder()
            .type(Metric.Type.RESOURCE)
            .resource(
                Resource.builder().name(SCALING_CONFIG_CPU_METRIC_NAME).target(cpuTarget).build())
            .build();
    ScalingConfig scalingConfig =
        SCALING_CONFIG.toBuilder()
            .spec(SCALING_CONFIG.spec().toBuilder().metrics(ImmutableList.of(cpuMetric)).build())
            .build();
    when(configurationProvider.scalingConfig()).thenReturn(scalingConfig);

    Scaler scaler =
        new Scaler(
            kafka,
            scalingStabilizer,
            cloudRunClientWrapper,
            metricsService,
            WORKERPOOL_WORKLOAD_INFO,
            MANUAL_SCALING_STATIC_CONFIG,
            configurationProvider);

    when(metricsService.getWorkerPoolCpuUtilizationData(any(), any()))
        .thenReturn(
            Optional.of(
                ImmutableList.of(
                    new MetricsService.InstanceCountUtilization(1, .9),
                    new MetricsService.InstanceCountUtilization(2, .5),
                    new MetricsService.InstanceCountUtilization(3, .4))));

    int currentInstanceCount = 5;
    when(kafka.getCurrentConsumerCount(CONSUMER_GROUP_ID)).thenReturn(currentInstanceCount);
    when(cloudRunClientWrapper.getWorkerPoolInstanceCount(WORKERPOOL_NAME))
        .thenReturn(currentInstanceCount);
    when(kafka.getLagPerPartition(TOPIC_NAME, CONSUMER_GROUP_ID))
        .thenReturn(makeLagPerPartitionMap(ImmutableMap.of(1, 0L, 2, 0L, 3, 0L)));

    int newInstanceCount = 10;
    when(scalingStabilizer.getBoundedRecommendation(
            eq(BEHAVIOR), any(), eq(currentInstanceCount), eq(3)))
        .thenReturn(newInstanceCount);

    scaler.scale();

    verify(cloudRunClientWrapper)
        .updateWorkerPoolManualInstances(WORKERPOOL_WORKLOAD_INFO.name(), newInstanceCount);
    verify(scalingStabilizer).markScaleEvent(eq(BEHAVIOR), any(), anyInt(), eq(newInstanceCount));
  }

  @Test
  public void scale_withCpuScaling_updatesWorkerPool()
      throws IOException, InterruptedException, ExecutionException {
    MetricTarget cpuTarget =
        MetricTarget.builder()
            .type(MetricTarget.Type.UTILIZATION)
            .averageUtilization(50)
            .activationThreshold(-1)
            .tolerance(0.1)
            .build();
    Metric cpuMetric =
        Metric.builder()
            .type(Metric.Type.RESOURCE)
            .resource(
                Resource.builder().name(SCALING_CONFIG_CPU_METRIC_NAME).target(cpuTarget).build())
            .build();
    ScalingConfig scalingConfig =
        SCALING_CONFIG.toBuilder()
            .spec(
                SCALING_CONFIG.spec().toBuilder()
                    .metrics(ImmutableList.of(cpuMetric, LAG_METRIC))
                    .build())
            .build();
    when(configurationProvider.scalingConfig()).thenReturn(scalingConfig);

    Scaler scaler =
        new Scaler(
            kafka,
            scalingStabilizer,
            cloudRunClientWrapper,
            metricsService,
            WORKERPOOL_WORKLOAD_INFO,
            MANUAL_SCALING_STATIC_CONFIG,
            configurationProvider);

    when(metricsService.getWorkerPoolCpuUtilizationData(any(), any()))
        .thenReturn(
            Optional.of(
                ImmutableList.of(
                    new MetricsService.InstanceCountUtilization(1, .9),
                    new MetricsService.InstanceCountUtilization(2, .5),
                    new MetricsService.InstanceCountUtilization(3, .4))));

    int currentInstanceCount = 5;
    when(kafka.getCurrentConsumerCount(CONSUMER_GROUP_ID)).thenReturn(currentInstanceCount);
    when(cloudRunClientWrapper.getWorkerPoolInstanceCount(WORKERPOOL_NAME))
        .thenReturn(currentInstanceCount);
    when(kafka.getLagPerPartition(TOPIC_NAME, CONSUMER_GROUP_ID))
        .thenReturn(makeLagPerPartitionMap(ImmutableMap.of(1, 0L, 2, 0L, 3, 0L)));

    int newInstanceCount = 10;
    when(scalingStabilizer.getBoundedRecommendation(
            eq(BEHAVIOR), any(), eq(currentInstanceCount), eq(3)))
        .thenReturn(newInstanceCount);

    scaler.scale();

    verify(cloudRunClientWrapper)
        .updateWorkerPoolManualInstances(WORKERPOOL_WORKLOAD_INFO.name(), newInstanceCount);
    verify(scalingStabilizer).markScaleEvent(eq(BEHAVIOR), any(), anyInt(), eq(newInstanceCount));
  }

  @Test
  public void scale_cpuUtilizationDataIsNull_scalesBasedOnLag()
      throws IOException, InterruptedException, ExecutionException {
    Scaler scaler =
        new Scaler(
            kafka,
            scalingStabilizer,
            cloudRunClientWrapper,
            metricsService,
            WORKERPOOL_WORKLOAD_INFO,
            MANUAL_SCALING_STATIC_CONFIG,
            configurationProvider);

    when(metricsService.getWorkerPoolCpuUtilizationData(any(), any())).thenReturn(Optional.empty());

    int currentInstanceCount = 25;

    Optional<Map<TopicPartition, Long>> lagPerPartition =
        makeLagPerPartitionMap(ImmutableMap.of(1, 1000L, 2, 2000L));

    when(kafka.getCurrentConsumerCount(CONSUMER_GROUP_ID)).thenReturn(currentInstanceCount);
    when(cloudRunClientWrapper.getWorkerPoolInstanceCount(WORKERPOOL_NAME))
        .thenReturn(currentInstanceCount);
    when(kafka.getLagPerPartition(TOPIC_NAME, CONSUMER_GROUP_ID)).thenReturn(lagPerPartition);

    int newInstanceCount = 1;
    when(scalingStabilizer.getBoundedRecommendation(
            eq(BEHAVIOR), any(), eq(currentInstanceCount), eq(2)))
        .thenReturn(newInstanceCount);

    scaler.scale();

    verify(cloudRunClientWrapper)
        .updateWorkerPoolManualInstances(WORKERPOOL_WORKLOAD_INFO.name(), newInstanceCount);
  }

  @Test
  public void scale_bothCpuAndLagScalingInactive_respectsBoundedRecommendation()
      throws IOException, InterruptedException, ExecutionException {
    MetricTarget lagTargetWithActivationThreshold =
        MetricTarget.builder()
            .type(MetricTarget.Type.AVERAGE)
            .averageValue(LAG_THREHSOLD)
            .activationThreshold(0)
            .tolerance(0.1)
            .build();
    Metric lagMetric =
        Metric.builder()
            .type(Metric.Type.EXTERNAL)
            .external(
                External.builder()
                    .metric(MetricName.builder().name(SCALING_CONFIG_LAG_METRIC_NAME).build())
                    .target(lagTargetWithActivationThreshold)
                    .build())
            .build();

    ScalingConfig scalingConfig =
        SCALING_CONFIG.toBuilder()
            .spec(
                SCALING_CONFIG.spec().toBuilder()
                    .metrics(ImmutableList.of(lagMetric, CPU_METRIC))
                    .build())
            .build();

    when(configurationProvider.scalingConfig()).thenReturn(scalingConfig);

    Scaler scaler =
        new Scaler(
            kafka,
            scalingStabilizer,
            cloudRunClientWrapper,
            metricsService,
            WORKERPOOL_WORKLOAD_INFO,
            MANUAL_SCALING_STATIC_CONFIG,
            configurationProvider);

    // Return an empty list. This makes CPU scaling inactive.
    when(metricsService.getWorkerPoolCpuUtilizationData(any(), any()))
        .thenReturn(Optional.of(ImmutableList.of()));

    int currentInstanceCount = 5;
    when(kafka.getCurrentConsumerCount(CONSUMER_GROUP_ID)).thenReturn(currentInstanceCount);
    when(cloudRunClientWrapper.getWorkerPoolInstanceCount(WORKERPOOL_NAME))
        .thenReturn(currentInstanceCount);
    // Return 0 lag. This makes lag scaling inactive.
    when(kafka.getLagPerPartition(TOPIC_NAME, CONSUMER_GROUP_ID))
        .thenReturn(makeLagPerPartitionMap(ImmutableMap.of(1, 0L)));

    int newInstanceCount = 999;
    when(scalingStabilizer.getBoundedRecommendation(any(), any(), anyInt(), eq(0)))
        .thenReturn(newInstanceCount);

    scaler.scale();

    verify(cloudRunClientWrapper)
        .updateWorkerPoolManualInstances(WORKERPOOL_WORKLOAD_INFO.name(), newInstanceCount);
    verify(scalingStabilizer).markScaleEvent(any(), any(), anyInt(), eq(newInstanceCount));
  }
}
