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

import static com.google.common.base.Strings.isNullOrEmpty;
import static java.nio.charset.StandardCharsets.UTF_8;

import com.google.cloud.run.kafkascaler.scalingconfig.Behavior;
import com.google.cloud.run.kafkascaler.scalingconfig.DefaultBehavior;
import com.google.cloud.run.kafkascaler.scalingconfig.Merger;
import com.google.cloud.run.kafkascaler.scalingconfig.Parser;
import com.google.cloud.run.kafkascaler.scalingconfig.ScalingConfig;
import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableSet;
import java.io.BufferedReader;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.time.Duration;
import java.util.HashSet;
import java.util.Properties;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.apache.kafka.clients.admin.AdminClientConfig;

/**
 * Collection of configuration providers for Kafka Scaler.
 *
 * <p>These methods read and validate the user-provided configuration for various components of
 * Kafka Scaler.
 */
class ConfigurationProvider {

  private static final boolean OUTPUT_SCALER_METRICS_DEFAULT = false;
  private static final boolean USE_MIN_INSTANCES_DEFAULT = false;

  // A cycle time greater than or equal to this will be ignored.
  public static final Duration MAX_CYCLE_DURATION = Duration.ofMinutes(1);

  // Scheduling config that disables self-scheduling.
  public static final SchedulingConfig SELF_SCHEDULING_DISABLED_CONFIG =
      new ConfigurationProvider.SchedulingConfig("", "", MAX_CYCLE_DURATION);

  /**
   * Static configuration for Kafka Scaler. These fields should only be read at startup, and should
   * not be changed during the lifetime of the application.
   */
  public record StaticConfig(
      String topicName,
      String consumerGroupId,
      boolean useMinInstances,
      boolean outputScalerMetrics) {}

  /** Configuration for self-scheduling. */
  public record SchedulingConfig(
      String fullyQualifiedCloudTaskQueueName,
      String invokerServiceAccountEmail,
      Duration cycleDuration) {}

  public static interface EnvProvider {
    String getEnv(String name);
  }

  /**
   * Provides access to the system environment.
   *
   * <p>This interface allows mocking the system environment in tests.
   */
  public static class SystemEnvProvider implements ConfigurationProvider.EnvProvider {
    @Override
    public String getEnv(String name) {
      return System.getenv(name);
    }
  }

  private final EnvProvider envProvider;
  private final String scalingConfigFile;

  public ConfigurationProvider(EnvProvider envProvider, String scalingConfigFile) {
    this.envProvider = Preconditions.checkNotNull(envProvider, "Env provider cannot be null.");
    this.scalingConfigFile =
        Preconditions.checkNotNull(scalingConfigFile, "Scaling config file cannot be null.");
  }

  WorkloadInfoParser.WorkloadInfo workloadInfo(ScalingConfig scalingConfig) {
    return WorkloadInfoParser.parse(scalingConfig.spec().scaleTargetRef().name());
  }

  private ImmutableSet<String> getMissingEnvVars(Set<String> requiredEnvVars) {
    Set<String> missingEnvVars = new HashSet<>();

    for (String requiredEnvVar : requiredEnvVars) {
      String value = envProvider.getEnv(requiredEnvVar);
      if (isNullOrEmpty(value)) {
        missingEnvVars.add(requiredEnvVar);
      }
    }

    return ImmutableSet.copyOf(missingEnvVars);
  }

  /**
   * Provides scaling configuration for Kafka Scaler
   *
   * <p>Reads from environment variables and the {@code SCALING_CONFIG_FILE} to create a {@code
   * Scaler.Config} object.
   *
   * @return Scaling configuration.
   * @throws IOException If an I/O error occurs while reading the scaling configuration file.
   * @throws IllegalArgumentException If required environment variables are missing.
   */
  StaticConfig staticConfig() throws IOException {
    ImmutableSet<String> missingEnvVars =
        getMissingEnvVars(ImmutableSet.of("KAFKA_TOPIC_ID", "CONSUMER_GROUP_ID"));

    if (!missingEnvVars.isEmpty()) {
      throw new IllegalArgumentException(
          "Environment variables "
              + String.join(",", missingEnvVars)
              + " are required but not set.");
    }
    String topicName = envProvider.getEnv("KAFKA_TOPIC_ID");
    String consumerGroupId = envProvider.getEnv("CONSUMER_GROUP_ID");

    boolean useMinInstances = USE_MIN_INSTANCES_DEFAULT;
    if (!isNullOrEmpty(envProvider.getEnv("USE_MIN_INSTANCES"))) {
      useMinInstances = Boolean.parseBoolean(envProvider.getEnv("USE_MIN_INSTANCES"));
    }

    boolean outputScalerMetrics = OUTPUT_SCALER_METRICS_DEFAULT;
    if (!isNullOrEmpty(envProvider.getEnv("OUTPUT_SCALER_METRICS"))) {
      outputScalerMetrics = Boolean.parseBoolean(envProvider.getEnv("OUTPUT_SCALER_METRICS"));
    }

    return new StaticConfig(topicName, consumerGroupId, useMinInstances, outputScalerMetrics);
  }

  /**
   * Reads the Kafka client properties from {@code KAFKA_CLIENT_PROPERTIES_FILE}.
   *
   * @return Kafka client properties.
   * @throws IOException If an I/O error occurs while reading the Kafka client properties file.
   * @throws IllegalArgumentException If the Kafka bootstrap servers property is not set in the
   *     Kafka client properties file.
   */
  Properties kafkaClientProperties(String kafkaClientPropertiesFile) throws IOException {
    try (InputStream inputStream = new FileInputStream(kafkaClientPropertiesFile)) {
      return kafkaClientProperties(inputStream);
    } catch (IOException e) {
      System.err.println("Unable to load client properties file " + kafkaClientPropertiesFile);
      throw e;
    }
  }

  Properties kafkaClientProperties(InputStream inputStream) throws IOException {
    Properties config = new Properties();

    try (BufferedReader bufferedReader =
        new BufferedReader(new InputStreamReader(inputStream, UTF_8))) {
      config.load(bufferedReader);
    }

    if (isNullOrEmpty(config.getProperty(AdminClientConfig.BOOTSTRAP_SERVERS_CONFIG))) {
      throw new IllegalArgumentException(
          "Kafka client property "
              + AdminClientConfig.BOOTSTRAP_SERVERS_CONFIG
              + " is required but not set in the Kafka client properties file.");
    }

    return config;
  }

  /**
   * Reads the self-scheduling configuration from environment variables.
   *
   * <p>If CYCLE_SECONDS is not set, returns a scheduling config that disables self-scheduling.
   *
   * @return The scheduling configuration.
   * @throws IllegalArgumentException If the CYCLE_SECONDS environment variable is set but not a
   *     number, or if required environment variables are missing when CYCLE_SECONDS is set.
   */
  SchedulingConfig selfSchedulingConfig() {
    SchedulingConfig schedulingConfig = SELF_SCHEDULING_DISABLED_CONFIG;

    String cycleSecondsEnvVar = envProvider.getEnv("CYCLE_SECONDS");
    int cycleSeconds;
    if (cycleSecondsEnvVar != null && !cycleSecondsEnvVar.isEmpty()) {
      try {
        cycleSeconds = Integer.parseInt(cycleSecondsEnvVar);
      } catch (NumberFormatException e) {
        throw new IllegalArgumentException("CYCLE_SECONDS is specified but not a number.", e);
      }

      ImmutableSet<String> missingEnvVars =
          getMissingEnvVars(
              ImmutableSet.of(
                  "FULLY_QUALIFIED_CLOUD_TASKS_QUEUE_NAME", "INVOKER_SERVICE_ACCOUNT_EMAIL"));

      if (!missingEnvVars.isEmpty()) {
        throw new IllegalArgumentException(
            "Environment variables "
                + String.join(",", missingEnvVars)
                + " are required when setting CYCLE_SECONDS.");
      }

      schedulingConfig =
          new SchedulingConfig(
              envProvider.getEnv("FULLY_QUALIFIED_CLOUD_TASKS_QUEUE_NAME"),
              envProvider.getEnv("INVOKER_SERVICE_ACCOUNT_EMAIL"),
              Duration.ofSeconds(cycleSeconds));
    }

    return schedulingConfig;
  }

  /**
   * Provides scaling configuration for Kafka Scaler
   *
   * <p>Reads from {@code SCALING_CONFIG_FILE} and merges the result result with the default scaling
   * config.
   *
   * @return Scaling configuration.
   * @throws IOException If an I/O error occurs while reading the scaling configuration file.
   */
  public ScalingConfig scalingConfig() throws IOException {
    try (InputStream inputStream = new FileInputStream(scalingConfigFile)) {
      return scalingConfig(inputStream);
    } catch (IOException e) {
      throw new IOException("Failed to read scaling config file " + scalingConfigFile, e);
    }
  }

  public ScalingConfig scalingConfig(InputStream inputStream) throws IOException {
    ScalingConfig scalingConfig = Parser.load(inputStream);
    Behavior behavior = Merger.merge(DefaultBehavior.VALUE, scalingConfig.spec().behavior());

    // Patch the merged behavior into the scaling config
    ScalingConfig finalScalingConfig =
        scalingConfig.toBuilder()
            .spec(scalingConfig.spec().toBuilder().behavior(behavior).build())
            .build();

    System.out.println("Current scaling config: " + finalScalingConfig);
    return finalScalingConfig;
  }

  /**
   * Provides the URL of the current service.
   *
   * @param projectNumberRegion The project number and region in the format
   *     projects/PROJECT_NUMBER/regions/REGION.
   * @return The URL of the current service.
   * @throws IllegalArgumentException If the project number and region is not in the correct format.
   * @throws IllegalStateException If the K_SERVICE environment variable is not set.
   */
  public String scalerUrl(String projectNumberRegion) throws IOException {
    Matcher projectNumberRegionMatcher = PROJECT_NUMBER_REGION_PATTERN.matcher(projectNumberRegion);

    if (!projectNumberRegionMatcher.matches()) {
      // This should only ever happen if Cloud Run metadata server is returning data in a new,
      // unexpected format.
      throw new IllegalArgumentException(
          "Failed to parse project number and region from: " + projectNumberRegion);
    }

    if (isNullOrEmpty(envProvider.getEnv("K_SERVICE"))) {
      // This is set by Cloud Run so this should never actually be null or empty.
      throw new IllegalStateException("K_SERVICE is null or empty.");
    }

    String projectNumber = projectNumberRegionMatcher.group(1);
    String region = projectNumberRegionMatcher.group(2);
    String serviceName = envProvider.getEnv("K_SERVICE");

    return String.format("https://%s-%s.%s.run.app", serviceName, projectNumber, region);
  }

  private static final Pattern PROJECT_NUMBER_REGION_PATTERN =
      Pattern.compile("projects/([0-9]+)/regions/([^/]+)");
}
