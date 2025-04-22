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
package com.google.cloud.run.kafkascaler.clients;

import com.google.api.client.http.javanet.NetHttpTransport;
import com.google.api.client.json.gson.GsonFactory;
import com.google.api.services.run.v2.CloudRun;
import com.google.api.services.run.v2.model.GoogleCloudRunV2Service;
import com.google.api.services.run.v2.model.GoogleCloudRunV2ServiceScaling;
import com.google.api.services.run.v2.model.GoogleCloudRunV2WorkerPool;
import com.google.api.services.run.v2.model.GoogleCloudRunV2WorkerPoolScaling;
import com.google.api.services.run.v2.model.GoogleLongrunningOperation;
import com.google.auth.http.HttpCredentialsAdapter;
import com.google.auth.oauth2.GoogleCredentials;
import com.google.common.flogger.FluentLogger;
import java.io.IOException;
import java.time.Instant;
import java.util.Arrays;

/** Thin wrapper around Cloud Run API */
public class CloudRunClientWrapper {
  private static final FluentLogger logger = FluentLogger.forEnclosingClass();

  private static final String CLOUD_RUN_ROOT_URL = "https://run.googleapis.com/";
  private final CloudRun cloudRun;
  private final String projectId;
  private final String region;

  private static final String AUTOMATIC_SCALING_MODE = "AUTOMATIC";
  private static final String MANUAL_SCALING_MODE = "MANUAL";

  private static final String SCALING_UPDATE_MASK = String.join(",", Arrays.asList("scaling"));
  private static final String SCALING_AND_LAUNCH_STAGE_UPDATE_MASK =
      String.join(",", Arrays.asList("scaling", "launch_stage"));
  private static final String ALPHA_LAUNCH_STAGE = "ALPHA";

  public static CloudRun cloudRunClient(String applicationName) throws IOException {
    GoogleCredentials credentials = GoogleCredentials.getApplicationDefault();
    CloudRun.Builder builder =
        new CloudRun.Builder(
            new NetHttpTransport(), new GsonFactory(), new HttpCredentialsAdapter(credentials));
    builder.setApplicationName(applicationName);
    builder.setRootUrl(CLOUD_RUN_ROOT_URL);
    return builder.build();
  }

  public CloudRunClientWrapper(CloudRun cloudRun, String projectId, String region) {
    this.cloudRun = cloudRun;
    this.projectId = projectId;
    this.region = region;
  }

  /**
   * Returns the instance count for a given worker pool.
   *
   * <p>Cloud Run Admin API will return null if min instances is 0 with autoscaling. In this case,
   * translate the null to 0.
   *
   * @param workerpoolName The name of the worker pool.
   * @return The instance count.
   * @throws IOException If an error occurs during the API call.
   */
  public int getWorkerPoolInstanceCount(String workerpoolName) throws IOException {
    GoogleCloudRunV2WorkerPool workerpool = getWorkerPool(workerpoolName);
    GoogleCloudRunV2WorkerPoolScaling scaling = workerpool.getScaling();

    if (scaling == null) {
      return 0;
    }

    if (scaling.getScalingMode() != null && scaling.getScalingMode().equals(MANUAL_SCALING_MODE)) {
      return scaling.getManualInstanceCount();
    } else {
      Integer instanceCount = scaling.getMinInstanceCount();
      return instanceCount == null ? 0 : instanceCount;
    }
  }

  /**
   * Returns the last deployment time for a given worker pool.
   *
   * @param workerpoolName The name of the worker pool.
   * @return The last deployment time or EPOC if not set.
   * @throws IOException If an error occurs during the API call.
   */
  public Instant getWorkerPoolLastDeploymentTime(String workerpoolName) throws IOException {
    GoogleCloudRunV2WorkerPool workerpool = getWorkerPool(workerpoolName);
    return workerpool.getUpdateTime() == null
        ? Instant.EPOCH
        : Instant.parse(workerpool.getUpdateTime());
  }

  /**
   * Updates the number of min instances for a given worker pool.
   *
   * @param workerpoolName The name of the worker pool to update.
   * @param instances The desired number of instances.
   * @throws IOException If an error occurs during the API call.
   */
  public void updateWorkerPoolMinInstances(String workerpoolName, int instances)
      throws IOException {
    GoogleCloudRunV2WorkerPool workerpool = getWorkerPool(workerpoolName);

    GoogleCloudRunV2WorkerPoolScaling oldScaling = workerpool.getScaling();
    GoogleCloudRunV2WorkerPoolScaling newScaling = new GoogleCloudRunV2WorkerPoolScaling();
    if (oldScaling != null && oldScaling.getMaxInstanceCount() != null) {
      newScaling.setMaxInstanceCount(oldScaling.getMaxInstanceCount());
    }

    newScaling.setMinInstanceCount(instances);
    newScaling.setScalingMode(AUTOMATIC_SCALING_MODE);

    workerpool.setScaling(newScaling);

    GoogleLongrunningOperation operation =
        this.cloudRun
            .projects()
            .locations()
            .workerPools()
            .patch(
                String.format(
                    "projects/%s/locations/%s/workerPools/%s",
                    this.projectId, this.region, workerpoolName),
                workerpool)
            .setUpdateMask(SCALING_UPDATE_MASK)
            .execute();

    if (operation.getError() != null) {
      throw new IOException(
          "Request failed to Cloud Run to update workerpool instances: " + operation.getError());
    } else {
      logger.atInfo().log(
          "Sent update workerpool request to set instances to %d for workerpool %s",
          instances, workerpoolName);
    }
  }

  /**
   * Updates the number of manual instances for a given worker pool.
   *
   * @param workerpoolName The name of the worker pool to update.
   * @param instances The desired number of instances.
   * @throws IOException If an error occurs during the API call.
   */
  public void updateWorkerPoolManualInstances(String workerpoolName, int instances)
      throws IOException {
    GoogleCloudRunV2WorkerPoolScaling scaling = new GoogleCloudRunV2WorkerPoolScaling();
    scaling.setManualInstanceCount(instances);
    scaling.setScalingMode(MANUAL_SCALING_MODE);

    GoogleCloudRunV2WorkerPool workerpool = getWorkerPool(workerpoolName);
    workerpool.setScaling(scaling);
    workerpool.setLaunchStage(ALPHA_LAUNCH_STAGE);

    GoogleLongrunningOperation operation =
        this.cloudRun
            .projects()
            .locations()
            .workerPools()
            .patch(
                String.format(
                    "projects/%s/locations/%s/workerPools/%s",
                    this.projectId, this.region, workerpoolName),
                workerpool)
            .setUpdateMask(SCALING_AND_LAUNCH_STAGE_UPDATE_MASK)
            .execute();

    if (operation.getError() != null) {
      throw new IOException(
          "Request failed to Cloud Run to update workerpool instances: " + operation.getError());
    } else {
      logger.atInfo().log(
          "Sent update workerpool request to set instances to %d for workerpool %s",
          instances, workerpoolName);
    }
  }

  /**
   * Returns the instance count for a given service.
   *
   * <p>Cloud Run Admin API will return null if min instances is 0 with autoscaling. In this case,
   * translate the null to 0.
   *
   * @param serviceName The name of the service.
   * @return The instance count.
   * @throws IOException If an error occurs during the API call.
   */
  public int getServiceInstanceCount(String serviceName) throws IOException {
    GoogleCloudRunV2Service service = getService(serviceName);
    GoogleCloudRunV2ServiceScaling scaling = service.getScaling();

    if (scaling == null) {
      return 0;
    }

    if (scaling.getScalingMode() != null && scaling.getScalingMode().equals(MANUAL_SCALING_MODE)) {
      return scaling.getManualInstanceCount();
    } else {
      Integer instanceCount = scaling.getMinInstanceCount();
      return instanceCount == null ? 0 : instanceCount;
    }
  }

  /**
   * Returns the last deployment time for a given service.
   *
   * @param serviceName The name of the service.
   * @return The last deployment time or EPOC if not set.
   * @throws IOException If an error occurs during the API call.
   */
  public Instant getServiceLastDeploymentTime(String serviceName) throws IOException {
    GoogleCloudRunV2Service service = getService(serviceName);
    return service.getUpdateTime() == null ? Instant.EPOCH : Instant.parse(service.getUpdateTime());
  }

  /**
   * Updates the number of min instances for a given service.
   *
   * @param serviceName The name of the service to update.
   * @param instances The desired number of instances.
   * @throws IOException If an error occurs during the API call.
   */
  public void updateServiceMinInstances(String serviceName, int instances) throws IOException {
    GoogleCloudRunV2Service service = getService(serviceName);

    GoogleCloudRunV2ServiceScaling oldScaling = service.getScaling();
    GoogleCloudRunV2ServiceScaling newScaling = new GoogleCloudRunV2ServiceScaling();
    if (oldScaling != null && oldScaling.getMaxInstanceCount() != null) {
      newScaling.setMaxInstanceCount(oldScaling.getMaxInstanceCount());
    }

    newScaling.setMinInstanceCount(instances);
    newScaling.setScalingMode(AUTOMATIC_SCALING_MODE);

    service.setScaling(newScaling);

    GoogleLongrunningOperation operation =
        this.cloudRun
            .projects()
            .locations()
            .services()
            .patch(
                String.format(
                    "projects/%s/locations/%s/services/%s",
                    this.projectId, this.region, serviceName),
                service)
            .setUpdateMask(SCALING_UPDATE_MASK)
            .execute();

    if (operation.getError() != null) {
      throw new IOException(
          "Request failed to Cloud Run to update service instances: " + operation.getError());
    } else {
      logger.atInfo().log(
          "Sent update service request to set instances to %d for service %s",
          instances, serviceName);
    }
  }

  /**
   * Updates the number of manual instances for a given service.
   *
   * @param serviceName The name of the service to update.
   * @param instances The desired number of instances.
   * @throws IOException If an error occurs during the API call.
   */
  public void updateServiceManualInstances(String serviceName, int instances) throws IOException {
    GoogleCloudRunV2ServiceScaling scaling = new GoogleCloudRunV2ServiceScaling();
    scaling.setManualInstanceCount(instances);
    scaling.setScalingMode(MANUAL_SCALING_MODE);

    GoogleCloudRunV2Service service = getService(serviceName);

    service.setScaling(scaling);
    service.setLaunchStage(ALPHA_LAUNCH_STAGE);

    GoogleLongrunningOperation operation =
        this.cloudRun
            .projects()
            .locations()
            .services()
            .patch(
                String.format(
                    "projects/%s/locations/%s/services/%s",
                    this.projectId, this.region, serviceName),
                service)
            .setUpdateMask(SCALING_AND_LAUNCH_STAGE_UPDATE_MASK)
            .execute();

    if (operation.getError() != null) {
      throw new IOException(
          "Request failed to Cloud Run to update service instances: " + operation.getError());
    } else {
      logger.atInfo().log(
          "Sent update service request to set instances to %d for service %s",
          instances, serviceName);
    }
  }

  private GoogleCloudRunV2WorkerPool getWorkerPool(String workerpoolName) throws IOException {
    return this.cloudRun
        .projects()
        .locations()
        .workerPools()
        .get(
            String.format(
                "projects/%s/locations/%s/workerPools/%s",
                this.projectId, this.region, workerpoolName))
        .execute();
  }

  private GoogleCloudRunV2Service getService(String serviceName) throws IOException {
    return this.cloudRun
        .projects()
        .locations()
        .services()
        .get(
            String.format(
                "projects/%s/locations/%s/services/%s", this.projectId, this.region, serviceName))
        .execute();
  }
}
