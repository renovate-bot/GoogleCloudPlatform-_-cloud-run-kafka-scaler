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

import com.google.cloud.run.kafkascaler.clients.CloudMonitoringClientWrapper;
import com.google.cloud.run.kafkascaler.clients.CloudRunClientWrapper;
import com.google.cloud.run.kafkascaler.clients.CloudRunMetadataClient;
import com.google.cloud.run.kafkascaler.clients.CloudTasksClientWrapper;
import com.google.cloud.run.kafkascaler.clients.KafkaAdminClientWrapper;
import com.google.cloud.run.kafkascaler.scalingconfig.ScalingConfig;
import com.google.common.collect.ImmutableMap;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.time.Instant;
import java.util.concurrent.ExecutionException;

/**
 * The main class for the Cloud Run Kafka Scaler application.
 *
 * <p>This application is responsible for scaling a Cloud Run service based on the consumer lag of a
 * Kafka topic. It exposes an HTTP endpoint which triggers the scaling logic. When triggered, the
 * application retrieves the consumer lag from Kafka, calculates the desired number of instances for
 * the consumer workload running on Cloud Run, and updates the Cloud Run instance count if
 * necessary.
 */
final class KafkaScaler {
  private static final String APPLICATION_NAME = "Cloud Run Kafka Scaler";
  private static final int CLOUD_RUN_DEFAULT_PORT = 8080;

  private static final String KAFKA_CLIENT_PROPERTIES_FILE =
      "/kafka-config/kafka-client-properties";
  private static final String SCALING_CONFIG_FILE = "/scaler-config/scaling";

  private final Scaler scaler;
  private final SelfScheduler selfScheduler;

  private KafkaScaler() throws IOException {
    this.selfScheduler = initializeSelfScheduler();
    this.scaler = initializeScaler();
  }

  private Scaler initializeScaler() throws IOException {
    ConfigurationProvider configProvider =
        new ConfigurationProvider(
            new ConfigurationProvider.SystemEnvProvider(), SCALING_CONFIG_FILE);
    ConfigurationProvider.StaticConfig staticConfig = configProvider.staticConfig();
    ScalingConfig scalingConfig = configProvider.scalingConfig();

    KafkaAdminClientWrapper kafkaAdminClient =
        new KafkaAdminClientWrapper(
            configProvider.kafkaClientProperties(KAFKA_CLIENT_PROPERTIES_FILE));
    Kafka kafka = new Kafka(kafkaAdminClient);

    WorkloadInfoParser.WorkloadInfo workloadInfo = configProvider.workloadInfo(scalingConfig);
    CloudRunClientWrapper cloudRunClient =
        new CloudRunClientWrapper(
            CloudRunClientWrapper.cloudRunClient(APPLICATION_NAME),
            workloadInfo.projectId(),
            workloadInfo.location());

    int currentInstanceCount = InstanceCountProvider.getInstanceCount(cloudRunClient, workloadInfo);

    CloudMonitoringClientWrapper cloudMonitoringClient =
        new CloudMonitoringClientWrapper(CloudMonitoringClientWrapper.metricServiceClient());
    MetricsService metricsService =
        new MetricsService(cloudMonitoringClient, workloadInfo.projectId());

    ScalingStabilizer scalingStabilizer = new ScalingStabilizer(currentInstanceCount);

    return new Scaler(
        kafka,
        scalingStabilizer,
        cloudRunClient,
        metricsService,
        workloadInfo,
        staticConfig,
        configProvider);
  }

  private SelfScheduler initializeSelfScheduler() throws IOException {
    CloudRunMetadataClient cloudRunMetadataClient = new CloudRunMetadataClient();

    ConfigurationProvider configProvider =
        new ConfigurationProvider(
            new ConfigurationProvider.SystemEnvProvider(), SCALING_CONFIG_FILE);

    return new SelfScheduler(
        new CloudTasksClientWrapper(CloudTasksClientWrapper.cloudTasksClient()),
        cloudRunMetadataClient,
        configProvider);
  }

  class RequestHandler implements HttpHandler {
    @Override
    public void handle(HttpExchange exchange) {
      Instant now = Instant.now();
      ImmutableMap<String, String> headers = RequestResponseHandler.getRequestHeaderMap(exchange);
      try {
        selfScheduler.scheduleTasks(now, headers);
        scaler.scale();
        RequestResponseHandler.sendSuccessResponse(exchange);
      } catch (IOException | ExecutionException | InterruptedException e) {
        RequestResponseHandler.sendFailureResponse(exchange, e);
      } catch (RuntimeException e) {
        // TODO: Log this as ERROR when we use a logger.
        System.out.println("Caught unchecked RuntimeException: " + e.getMessage());
        e.printStackTrace();
        RequestResponseHandler.sendFailureResponse(exchange, e);
      }
    }
  }

  public static void main(String[] args) throws IOException {
    KafkaScaler app = new KafkaScaler();

    HttpServer server = HttpServer.create(new InetSocketAddress(CLOUD_RUN_DEFAULT_PORT), 0);
    server.createContext("/", app.new RequestHandler());
    server.setExecutor(null); // Use the same thread
    server.start();
  }
}
