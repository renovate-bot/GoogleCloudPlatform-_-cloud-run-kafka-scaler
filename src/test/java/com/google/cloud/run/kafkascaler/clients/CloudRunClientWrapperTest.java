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

import static com.google.common.truth.Truth.assertThat;
import static org.junit.Assert.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

import com.google.api.services.run.v2.CloudRun;
import com.google.api.services.run.v2.model.GoogleCloudRunV2Service;
import com.google.api.services.run.v2.model.GoogleCloudRunV2ServiceScaling;
import com.google.api.services.run.v2.model.GoogleCloudRunV2WorkerPool;
import com.google.api.services.run.v2.model.GoogleCloudRunV2WorkerPoolScaling;
import com.google.api.services.run.v2.model.GoogleLongrunningOperation;
import com.google.api.services.run.v2.model.GoogleRpcStatus;
import java.io.IOException;
import java.time.Instant;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;

@RunWith(JUnit4.class)
public final class CloudRunClientWrapperTest {

  @Rule public final MockitoRule mockito = MockitoJUnit.rule();

  @Mock private CloudRun cloudRun;
  @Mock private CloudRun.Projects projects;
  @Mock private CloudRun.Projects.Locations locations;
  @Mock private CloudRun.Projects.Locations.WorkerPools workerPools;
  @Mock private CloudRun.Projects.Locations.WorkerPools.Get getWorkerPool;
  @Mock private CloudRun.Projects.Locations.WorkerPools.Patch patchWorkerPool;
  @Mock private CloudRun.Projects.Locations.Services services;
  @Mock private CloudRun.Projects.Locations.Services.Get getService;
  @Mock private CloudRun.Projects.Locations.Services.Patch patchService;

  @Captor private ArgumentCaptor<String> updateMaskCaptor;
  @Captor private ArgumentCaptor<GoogleCloudRunV2WorkerPool> workerPoolCaptor;
  @Captor private ArgumentCaptor<GoogleCloudRunV2Service> serviceCaptor;

  private CloudRunClientWrapper cloudRunClientWrapper;

  private static final String PROJECT_ID = "projectId";
  private static final String REGION = "global";
  private static final String WORKERPOOL_NAME = "workerpool-name";
  private static final String SERVICE_NAME = "service-name";

  private static final String WORKERPOOL_RESOURCE_NAME =
      String.format("projects/%s/locations/%s/workerPools/%s", PROJECT_ID, REGION, WORKERPOOL_NAME);
  private static final String SERVICE_RESOURCE_NAME =
      String.format("projects/%s/locations/%s/services/%s", PROJECT_ID, REGION, SERVICE_NAME);

  @Before
  public void setUp() throws Exception {
    when(cloudRun.projects()).thenReturn(projects);
    when(projects.locations()).thenReturn(locations);
    when(locations.workerPools()).thenReturn(workerPools);
    when(workerPools.get(WORKERPOOL_RESOURCE_NAME)).thenReturn(getWorkerPool);
    when(services.get(SERVICE_RESOURCE_NAME)).thenReturn(getService);
    when(locations.services()).thenReturn(services);

    cloudRunClientWrapper = new CloudRunClientWrapper(cloudRun, PROJECT_ID, REGION);
  }

  @Test
  public void getWorkerPoolInstanceCount_withNullScaling_returnsZero() throws IOException {
    when(getWorkerPool.execute()).thenReturn(new GoogleCloudRunV2WorkerPool().setScaling(null));

    assertThat(cloudRunClientWrapper.getWorkerPoolInstanceCount(WORKERPOOL_NAME)).isEqualTo(0);
  }

  @Test
  public void getWorkerPoolInstanceCount_withNullScalingMode_returnsMinInstances()
      throws IOException {
    int numInstances = 20;

    GoogleCloudRunV2WorkerPool workerPool = new GoogleCloudRunV2WorkerPool();
    workerPool.setScaling(
        new GoogleCloudRunV2WorkerPoolScaling()
            .setScalingMode(null)
            .setMinInstanceCount(numInstances));

    when(getWorkerPool.execute()).thenReturn(workerPool);

    assertThat(cloudRunClientWrapper.getWorkerPoolInstanceCount(WORKERPOOL_NAME))
        .isEqualTo(numInstances);
  }

  @Test
  public void getWorkerPoolInstanceCount_withManualScaling_returnsManualInstances()
      throws IOException {
    int numInstances = 20;
    GoogleCloudRunV2WorkerPool workerPool = new GoogleCloudRunV2WorkerPool();
    workerPool.setScaling(
        new GoogleCloudRunV2WorkerPoolScaling()
            .setScalingMode("MANUAL")
            .setManualInstanceCount(numInstances));
    when(getWorkerPool.execute()).thenReturn(workerPool);

    assertThat(cloudRunClientWrapper.getWorkerPoolInstanceCount(WORKERPOOL_NAME))
        .isEqualTo(numInstances);
  }

  @Test
  public void getWorkerPoolInstanceCount_withAutoScaling_returnsMinInstances() throws IOException {
    int numInstances = 20;
    GoogleCloudRunV2WorkerPool workerPool = new GoogleCloudRunV2WorkerPool();
    workerPool.setScaling(
        new GoogleCloudRunV2WorkerPoolScaling()
            .setScalingMode("AUTOMATIC")
            .setMinInstanceCount(numInstances));
    when(getWorkerPool.execute()).thenReturn(workerPool);

    assertThat(cloudRunClientWrapper.getWorkerPoolInstanceCount(WORKERPOOL_NAME))
        .isEqualTo(numInstances);
  }

  @Test
  public void getWorkerPoolInstanceCount_withAutoScalingAndNullMinInstances_returnsZero()
      throws IOException {
    GoogleCloudRunV2WorkerPool workerPool = new GoogleCloudRunV2WorkerPool();
    workerPool.setScaling(
        new GoogleCloudRunV2WorkerPoolScaling()
            .setScalingMode("AUTOMATIC")
            .setMinInstanceCount(null));
    when(getWorkerPool.execute()).thenReturn(workerPool);

    assertThat(cloudRunClientWrapper.getWorkerPoolInstanceCount(WORKERPOOL_NAME)).isEqualTo(0);
  }

  @Test
  public void getWorkerPoolInstanceCount_withUnknownScalingMode_returnsMinInstanceCount()
      throws IOException {
    int numInstances = 20;

    GoogleCloudRunV2WorkerPool workerPool = new GoogleCloudRunV2WorkerPool();
    workerPool.setScaling(
        new GoogleCloudRunV2WorkerPoolScaling()
            .setScalingMode("RANDOM_SCALING_MODE")
            .setMinInstanceCount(numInstances));
    when(getWorkerPool.execute()).thenReturn(workerPool);

    assertThat(cloudRunClientWrapper.getWorkerPoolInstanceCount(WORKERPOOL_NAME)).isEqualTo(numInstances);
  }

  @Test
  public void updateWorkerPoolManualInstances_succeeds() throws IOException {
    GoogleCloudRunV2WorkerPool workerPool = new GoogleCloudRunV2WorkerPool();
    when(getWorkerPool.execute()).thenReturn(workerPool);

    when(workerPools.patch(eq(WORKERPOOL_RESOURCE_NAME), workerPoolCaptor.capture()))
        .thenReturn(patchWorkerPool);
    when(patchWorkerPool.setUpdateMask(updateMaskCaptor.capture())).thenReturn(patchWorkerPool);

    GoogleLongrunningOperation operation = new GoogleLongrunningOperation();
    when(patchWorkerPool.execute()).thenReturn(operation);

    cloudRunClientWrapper.updateWorkerPoolManualInstances(WORKERPOOL_NAME, 10);

    GoogleCloudRunV2WorkerPool actual = workerPoolCaptor.getValue();
    assertThat(actual.getScaling().getManualInstanceCount()).isEqualTo(10);
    assertThat(actual.getScaling().getScalingMode()).isEqualTo("MANUAL");
    assertThat(actual.getLaunchStage()).isEqualTo("ALPHA");
    assertThat(updateMaskCaptor.getValue()).isEqualTo("scaling,launch_stage");
  }

  @Test
  public void updateWorkerPoolManualInstances_operationError_throwsIOException()
      throws IOException {
    GoogleCloudRunV2WorkerPool workerPool = new GoogleCloudRunV2WorkerPool();
    when(getWorkerPool.execute()).thenReturn(workerPool);
    when(workerPools.patch(any(), any())).thenReturn(patchWorkerPool);
    when(patchWorkerPool.setUpdateMask(any())).thenReturn(patchWorkerPool);

    GoogleLongrunningOperation operation = new GoogleLongrunningOperation();
    when(patchWorkerPool.execute()).thenReturn(operation);

    operation.setError(new GoogleRpcStatus().setMessage("error"));

    assertThrows(
        IOException.class,
        () -> cloudRunClientWrapper.updateWorkerPoolManualInstances(WORKERPOOL_NAME, 10));
  }

  @Test
  public void updateWorkerPoolMinInstances_succeeds() throws IOException {
    GoogleCloudRunV2WorkerPool workerPool = new GoogleCloudRunV2WorkerPool();
    workerPool.setScaling(
        new GoogleCloudRunV2WorkerPoolScaling().setMinInstanceCount(3).setMaxInstanceCount(111));
    when(getWorkerPool.execute()).thenReturn(workerPool);

    when(workerPools.patch(eq(WORKERPOOL_RESOURCE_NAME), workerPoolCaptor.capture()))
        .thenReturn(patchWorkerPool);
    when(patchWorkerPool.setUpdateMask(updateMaskCaptor.capture())).thenReturn(patchWorkerPool);

    GoogleLongrunningOperation operation = new GoogleLongrunningOperation();
    when(patchWorkerPool.execute()).thenReturn(operation);

    cloudRunClientWrapper.updateWorkerPoolMinInstances(WORKERPOOL_NAME, 10);

    GoogleCloudRunV2WorkerPool actual = workerPoolCaptor.getValue();
    assertThat(actual.getScaling().getMinInstanceCount()).isEqualTo(10);
    assertThat(actual.getScaling().getMaxInstanceCount()).isEqualTo(111);
    assertThat(actual.getScaling().getScalingMode()).isEqualTo("AUTOMATIC");
    assertThat(updateMaskCaptor.getValue()).isEqualTo("scaling");
  }

  @Test
  public void updateWorkerPoolMinInstances_operationError_throwsIOException() throws IOException {
    GoogleCloudRunV2WorkerPool workerPool = new GoogleCloudRunV2WorkerPool();
    when(getWorkerPool.execute()).thenReturn(workerPool);
    when(workerPools.patch(any(), any())).thenReturn(patchWorkerPool);
    when(patchWorkerPool.setUpdateMask(any())).thenReturn(patchWorkerPool);

    GoogleLongrunningOperation operation = new GoogleLongrunningOperation();
    when(patchWorkerPool.execute()).thenReturn(operation);

    operation.setError(new GoogleRpcStatus().setMessage("error"));

    assertThrows(
        IOException.class,
        () -> cloudRunClientWrapper.updateWorkerPoolMinInstances(WORKERPOOL_NAME, 10));
  }

  @Test
  public void getServiceInstanceCount_withNullScaling_returnsZero() throws IOException {
    when(getService.execute()).thenReturn(new GoogleCloudRunV2Service().setScaling(null));

    assertThat(cloudRunClientWrapper.getServiceInstanceCount(SERVICE_NAME)).isEqualTo(0);
  }

  @Test
  public void getServiceInstanceCount_withNullScalingMode_returnsMinInstances() throws IOException {
    int numInstances = 20;
    GoogleCloudRunV2Service service = new GoogleCloudRunV2Service();
    service.setScaling(
        new GoogleCloudRunV2ServiceScaling()
            .setScalingMode(null)
            .setMinInstanceCount(numInstances));

    when(getService.execute()).thenReturn(service);

    assertThat(cloudRunClientWrapper.getServiceInstanceCount(SERVICE_NAME)).isEqualTo(numInstances);
  }

  @Test
  public void getServiceInstanceCount_withManualScaling_returnsManualInstances()
      throws IOException {
    int numInstances = 20;
    GoogleCloudRunV2Service service = new GoogleCloudRunV2Service();
    service.setScaling(
        new GoogleCloudRunV2ServiceScaling()
            .setScalingMode("MANUAL")
            .setManualInstanceCount(numInstances));
    when(getService.execute()).thenReturn(service);

    assertThat(cloudRunClientWrapper.getServiceInstanceCount(SERVICE_NAME)).isEqualTo(numInstances);
  }

  @Test
  public void getServiceInstanceCount_withAutoScaling_returnsMinInstances() throws IOException {
    int numInstances = 20;
    GoogleCloudRunV2Service service = new GoogleCloudRunV2Service();
    service.setScaling(
        new GoogleCloudRunV2ServiceScaling()
            .setScalingMode("AUTOMATIC")
            .setMinInstanceCount(numInstances));
    when(getService.execute()).thenReturn(service);

    assertThat(cloudRunClientWrapper.getServiceInstanceCount(SERVICE_NAME)).isEqualTo(numInstances);
  }

  @Test
  public void getServiceInstanceCount_withAutoScalingAndNullMinInstances_returnsZero()
      throws IOException {
    GoogleCloudRunV2Service service = new GoogleCloudRunV2Service();
    service.setScaling(
        new GoogleCloudRunV2ServiceScaling().setScalingMode("AUTOMATIC").setMinInstanceCount(null));
    when(getService.execute()).thenReturn(service);

    assertThat(cloudRunClientWrapper.getServiceInstanceCount(SERVICE_NAME)).isEqualTo(0);
  }

  @Test
  public void getServiceInstanceCount_withUnknownScalingMode_returnsMinInstanceCount()
      throws IOException {
    int numInstances = 20;

    GoogleCloudRunV2Service service = new GoogleCloudRunV2Service();
    service.setScaling(
        new GoogleCloudRunV2ServiceScaling()
            .setScalingMode("RANDOM_SCALING_MODE")
            .setMinInstanceCount(numInstances));
    when(getService.execute()).thenReturn(service);

    assertThat(cloudRunClientWrapper.getServiceInstanceCount(SERVICE_NAME)).isEqualTo(numInstances);
  }

  @Test
  public void getServiceLastDeploymentTime_withUpdateTime_returnsInstant() throws IOException {
    String updateTimeString = "2025-04-01T18:05:07.879813Z";

    GoogleCloudRunV2Service service = new GoogleCloudRunV2Service();
    service.setUpdateTime(updateTimeString);
    when(getService.execute()).thenReturn(service);

    assertThat(cloudRunClientWrapper.getServiceLastDeploymentTime(SERVICE_NAME))
        .isEqualTo(Instant.parse(updateTimeString));
  }

  @Test
  public void getServiceLastDeploymentTime_withNullUpdateTime_returnsEpoch() throws IOException {
    GoogleCloudRunV2Service service = new GoogleCloudRunV2Service();
    service.setUpdateTime(null);
    when(getService.execute()).thenReturn(service);

    assertThat(cloudRunClientWrapper.getServiceLastDeploymentTime(SERVICE_NAME))
        .isEqualTo(Instant.EPOCH);
  }

  @Test
  public void getWorkerPoolLastDeploymentTime_withUpdateTime_returnsInstant() throws IOException {
    String updateTimeString = "2025-04-01T18:05:07.879813Z";

    GoogleCloudRunV2WorkerPool workerPool = new GoogleCloudRunV2WorkerPool();
    workerPool.setUpdateTime(updateTimeString);
    when(getWorkerPool.execute()).thenReturn(workerPool);

    assertThat(cloudRunClientWrapper.getWorkerPoolLastDeploymentTime(WORKERPOOL_NAME))
        .isEqualTo(Instant.parse(updateTimeString));
  }

  @Test
  public void getWorkerPoolLastDeploymentTime_withNullUpdateTime_returnsEpoch() throws IOException {
    GoogleCloudRunV2WorkerPool workerPool = new GoogleCloudRunV2WorkerPool();
    workerPool.setUpdateTime(null);
    when(getWorkerPool.execute()).thenReturn(workerPool);

    assertThat(cloudRunClientWrapper.getWorkerPoolLastDeploymentTime(WORKERPOOL_NAME))
        .isEqualTo(Instant.EPOCH);
  }

  @Test
  public void updateServiceManualInstances_succeeds() throws IOException {
    GoogleCloudRunV2Service service = new GoogleCloudRunV2Service();
    when(getService.execute()).thenReturn(service);

    when(services.patch(eq(SERVICE_RESOURCE_NAME), serviceCaptor.capture()))
        .thenReturn(patchService);
    when(patchService.setUpdateMask(updateMaskCaptor.capture())).thenReturn(patchService);

    GoogleLongrunningOperation operation = new GoogleLongrunningOperation();
    when(patchService.execute()).thenReturn(operation);

    cloudRunClientWrapper.updateServiceManualInstances(SERVICE_NAME, 10);

    GoogleCloudRunV2Service actual = serviceCaptor.getValue();
    assertThat(actual.getScaling().getManualInstanceCount()).isEqualTo(10);
    assertThat(actual.getScaling().getScalingMode()).isEqualTo("MANUAL");
    assertThat(actual.getLaunchStage()).isEqualTo("ALPHA");
    assertThat(updateMaskCaptor.getValue()).isEqualTo("scaling,launch_stage");
  }

  @Test
  public void updateServiceManualInstances_operationError_throwsIOException() throws IOException {
    GoogleCloudRunV2Service service = new GoogleCloudRunV2Service();
    when(getService.execute()).thenReturn(service);
    when(services.patch(any(), any())).thenReturn(patchService);
    when(patchService.setUpdateMask(any())).thenReturn(patchService);

    GoogleLongrunningOperation operation = new GoogleLongrunningOperation();
    when(patchService.execute()).thenReturn(operation);

    operation.setError(new GoogleRpcStatus().setMessage("error"));

    assertThrows(
        IOException.class, () -> cloudRunClientWrapper.updateServiceManualInstances(SERVICE_NAME, 10));
  }

  @Test
  public void updateServiceMinInstances_succeeds() throws IOException {
    GoogleCloudRunV2Service service = new GoogleCloudRunV2Service();
    service.setScaling(
        new GoogleCloudRunV2ServiceScaling().setMinInstanceCount(5).setMaxInstanceCount(222));
    when(getService.execute()).thenReturn(service);

    when(services.patch(eq(SERVICE_RESOURCE_NAME), serviceCaptor.capture()))
        .thenReturn(patchService);
    when(patchService.setUpdateMask(updateMaskCaptor.capture())).thenReturn(patchService);

    GoogleLongrunningOperation operation = new GoogleLongrunningOperation();
    when(patchService.execute()).thenReturn(operation);

    cloudRunClientWrapper.updateServiceMinInstances(SERVICE_NAME, 10);

    GoogleCloudRunV2Service actual = serviceCaptor.getValue();
    assertThat(actual.getScaling().getMinInstanceCount()).isEqualTo(10);
    assertThat(actual.getScaling().getMaxInstanceCount()).isEqualTo(222);
    assertThat(actual.getScaling().getScalingMode()).isEqualTo("AUTOMATIC");
    assertThat(updateMaskCaptor.getValue()).isEqualTo("scaling");
  }

  @Test
  public void updateServiceMinInstances_operationError_throwsIOException() throws IOException {
    GoogleCloudRunV2Service service = new GoogleCloudRunV2Service();
    when(getService.execute()).thenReturn(service);
    when(services.patch(any(), any())).thenReturn(patchService);
    when(patchService.setUpdateMask(any())).thenReturn(patchService);

    GoogleLongrunningOperation operation = new GoogleLongrunningOperation();
    when(patchService.execute()).thenReturn(operation);

    operation.setError(new GoogleRpcStatus().setMessage("error"));

    assertThrows(
        IOException.class, () -> cloudRunClientWrapper.updateServiceMinInstances(SERVICE_NAME, 10));
  }
}
