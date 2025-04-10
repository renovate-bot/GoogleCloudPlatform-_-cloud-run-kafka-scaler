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

import com.google.cloud.run.kafkascaler.clients.CloudRunClientWrapper;
import com.google.cloud.run.kafkascaler.scalingconfig.Behavior;
import com.google.cloud.run.kafkascaler.scalingconfig.Metric;
import com.google.cloud.run.kafkascaler.scalingconfig.MetricTarget;
import com.google.cloud.run.kafkascaler.scalingconfig.ScalingConfig;
import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import java.io.IOException;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ExecutionException;
import org.apache.kafka.common.TopicPartition;

/**
 * Entry point to the scaler.
 *
 * <p>This class reads metrics from Kafka, applies stabilization logic, and updates the number of
 * consumer instances in Cloud Run.
 *
 * <p>Caller is expected to handle translating exceptions into HTTP responses.
 */
public class Scaler {

  private static final String LAG_METRIC_NAME = "lag";
  private static final String RECOMMENDED_INSTANCE_COUNT_METRIC_NAME = "recommended_instance_count";
  private static final String REQUESTED_INSTANCE_COUNT_METRIC_NAME = "requested_instance_count";

  private static final String SCALING_CONFIG_LAG_METRIC_NAME = "consumer_lag";
  private static final String SCALING_CONFIG_CPU_METRIC_NAME = "cpu";

  private final Kafka kafka;
  private final ScalingStabilizer scalingStabilizer;
  private final CloudRunClientWrapper cloudRunClientWrapper;
  private final MetricsService metricsService;
  private final WorkloadInfoParser.WorkloadInfo workloadInfo;
  private final ConfigurationProvider.StaticConfig staticConfig;
  private final ConfigurationProvider configProvider;
  private final ImmutableMap<String, String> metricLabels;

  public Scaler(
      Kafka kafka,
      ScalingStabilizer scalingStabilizer,
      CloudRunClientWrapper cloudRunClientWrapper,
      MetricsService metricsService,
      WorkloadInfoParser.WorkloadInfo workloadInfo,
      ConfigurationProvider.StaticConfig config,
      ConfigurationProvider configProvider) {
    this.kafka = Preconditions.checkNotNull(kafka, "Kafka cannot be null.");
    this.scalingStabilizer =
        Preconditions.checkNotNull(scalingStabilizer, "Scaling stabilizer cannot be null.");
    this.cloudRunClientWrapper =
        Preconditions.checkNotNull(cloudRunClientWrapper, "Cloud Run client cannot be null.");
    this.metricsService =
        Preconditions.checkNotNull(metricsService, "Metrics service cannot be null.");
    this.workloadInfo = Preconditions.checkNotNull(workloadInfo, "Workload info cannot be null.");
    this.staticConfig = Preconditions.checkNotNull(config, "Static config cannot be null.");
    this.configProvider =
        Preconditions.checkNotNull(configProvider, "Config provider cannot be null.");

    this.metricLabels = ImmutableMap.of("consumer_service", workloadInfo.name());
  }

  /**
   * Updates the number of consumer instances in Cloud Run based on the current lag.
   *
   * <p>Throws an IllegalArgumentException if the topic does not exist.
   *
   * @throws IOException If there is an error communicating with Cloud Run.
   * @throws InterruptedException If the current thread is interrupted.
   * @throws ExecutionException If there is an error during the execution of a task.
   */
  public void scale() throws IOException, InterruptedException, ExecutionException {
    Instant now = Instant.now();

    if (!kafka.doesTopicExist(staticConfig.topicName())) {
      throw new IllegalArgumentException(
          String.format("The specified topic \"%s\" does not exist.", staticConfig.topicName()));
    }

    int currentInstanceCount =
        InstanceCountProvider.getInstanceCount(cloudRunClientWrapper, workloadInfo);
    System.out.printf("[SCALING] Current instances: %d%n", currentInstanceCount);

    // Current lag should never be empty here because we already checked that the topic exists.
    Map<TopicPartition, Long> lagPerPartition =
        kafka
            .getLagPerPartition(staticConfig.topicName(), staticConfig.consumerGroupId())
            .orElseThrow(() -> new AssertionError("Current lag is empty."));

    long currentLag = lagPerPartition.values().stream().mapToLong(Long::longValue).sum();

    ScalingConfig scalingConfig = configProvider.scalingConfig();
    Behavior behavior = scalingConfig.spec().behavior();
    ImmutableList<Metric> metrics = scalingConfig.spec().metrics();

    MetricTarget lagTarget = null;
    MetricTarget cpuTarget = null;

    // TODO: Make this more polymorphic.
    for (Metric metric : metrics) {
      if (metric.type() == Metric.Type.RESOURCE
          && metric.resource().name().equals(SCALING_CONFIG_CPU_METRIC_NAME)) {
        cpuTarget = metric.resource().target();
      } else if (metric.type() == Metric.Type.EXTERNAL
          && metric.external().metric().name().equals(SCALING_CONFIG_LAG_METRIC_NAME)) {
        lagTarget = metric.external().target();
      }
    }

    if (lagTarget == null && cpuTarget == null) {
      System.err.println(
          "[SCALING] No scaling metric configured. At east one scaling metric must be"
              + " configured to enable autoscaling.");
      return;
    }

    LagScaling.Recommendation lagBasedRecommendation = null;
    if (lagTarget != null) {
      lagBasedRecommendation =
          LagScaling.makeRecommendation(lagTarget, currentInstanceCount, currentLag);
    }

    CpuScaling.Recommendation cpuBasedRecommendation = null;
    if (cpuTarget != null) {
      Optional<List<MetricsService.InstanceCountUtilization>> cpuUtilizationData =
          getCpuUtilizationData(cpuTarget.windowSeconds());
      if (cpuUtilizationData.isPresent()) {
        cpuBasedRecommendation = CpuScaling.makeRecommendation(cpuTarget, cpuUtilizationData.get());
      }
    }

    int recommendedInstanceCount =
        max(
            (lagBasedRecommendation != null && lagBasedRecommendation.isActive())
                ? lagBasedRecommendation.recommendedInstanceCount()
                : 0,
            (cpuBasedRecommendation != null && cpuBasedRecommendation.isActive())
                ? cpuBasedRecommendation.recommendedInstanceCount()
                : 0);

    if (recommendedInstanceCount > lagPerPartition.size()) {
      recommendedInstanceCount = lagPerPartition.size();
      // TODO: Make this a WARNING level log when we start using a logging library with that
      // granularity.
      System.out.printf(
          "The recommended number of instances (%d) is greater than the number of partitions"
              + " (%d). The recommendation will be limited to the number of partitions.",
          recommendedInstanceCount, lagPerPartition.size());
    }

    int newInstanceCount =
        scalingStabilizer.getBoundedRecommendation(
            behavior, now, currentInstanceCount, recommendedInstanceCount);

    // Write metrics here to ensure metrics are written even if we skip the update request.
    try {
      metricsService.writeMetric(LAG_METRIC_NAME, (double) currentLag, metricLabels);
      metricsService.writeMetric(
          RECOMMENDED_INSTANCE_COUNT_METRIC_NAME, (double) recommendedInstanceCount, metricLabels);
      metricsService.writeMetric(
          REQUESTED_INSTANCE_COUNT_METRIC_NAME, (double) newInstanceCount, metricLabels);
    } catch (RuntimeException ex) {
      // An exception here is not critical to scaling. Log the exception and continue.
      // TODO: Make this an WARNING level log when we start using a logging library with that
      // granularity.
      System.err.printf(
          "Failed to write metrics to Cloud Monitoring. Ensure that your service account running"
              + " Kafka Scaler has the necessary permissions: %s%n",
          ex.getMessage());
    }

    if (newInstanceCount == currentInstanceCount) {
      // Skip update request if the number of instances is unchanged.
      System.out.printf("[SCALING] No change in recommended instances (%d)%n", newInstanceCount);
      return;
    }

    Instant nextUpdateAllowedTime = getNextUpdateAllowedTime(behavior);
    if (Instant.now().isAfter(nextUpdateAllowedTime)) {
      updateInstanceCount(newInstanceCount);
      System.out.printf("[SCALING] Recommended instances: %d%n", newInstanceCount);
      scalingStabilizer.markScaleEvent(behavior, now, currentInstanceCount, newInstanceCount);
    } else {
      // Rate limited due to cooldown period
      System.out.printf(
          "[SCALING] Within cooldown, no change. Next update allowed at: %s%n",
          nextUpdateAllowedTime.toString());
    }
  }

  private Instant getNextUpdateAllowedTime(Behavior behavior) throws IOException {
    Duration cooldownSeconds = behavior.cooldownSeconds();
    if (workloadInfo.workloadType() == WorkloadInfoParser.WorkloadType.SERVICE) {
      return cloudRunClientWrapper
          .getServiceLastDeploymentTime(workloadInfo.name())
          .plus(cooldownSeconds);
    } else {
      return cloudRunClientWrapper
          .getWorkerPoolLastDeploymentTime(workloadInfo.name())
          .plus(cooldownSeconds);
    }
  }

  private Optional<List<MetricsService.InstanceCountUtilization>> getCpuUtilizationData(
      Duration windowSize) throws IOException {
    if (workloadInfo.workloadType() == WorkloadInfoParser.WorkloadType.SERVICE) {
      return metricsService.getServiceCpuUtilizationData(workloadInfo.name(), windowSize);
    } else {
      return metricsService.getWorkerPoolCpuUtilizationData(workloadInfo.name(), windowSize);
    }
  }

  private void updateInstanceCount(int newInstanceCount) throws IOException {
    if (staticConfig.useMinInstances()) {
      if (workloadInfo.workloadType() == WorkloadInfoParser.WorkloadType.SERVICE) {
        cloudRunClientWrapper.updateServiceMinInstances(workloadInfo.name(), newInstanceCount);
      } else {
        cloudRunClientWrapper.updateWorkerPoolMinInstances(workloadInfo.name(), newInstanceCount);
      }
    } else {
      if (workloadInfo.workloadType() == WorkloadInfoParser.WorkloadType.SERVICE) {
        cloudRunClientWrapper.updateServiceManualInstances(workloadInfo.name(), newInstanceCount);
      } else {
        cloudRunClientWrapper.updateWorkerPoolManualInstances(
            workloadInfo.name(), newInstanceCount);
      }
    }
  }
}
