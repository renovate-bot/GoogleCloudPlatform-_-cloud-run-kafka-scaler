# Cloud Run Kafka Autoscaler

This repository contains the source code for a buildable container image of an
autoscaler that horizontally autoscales Kafka consumers, running on Cloud Run,
based on consumer lag and CPU utilization. The autoscaler reads metrics from
your Kafka cluster, and uses [manual
scaling](https://cloud.google.com/run/docs/configuring/services/manual-scaling)
to scale a Kafka consumer workload based on the Kafka consumer lag metric.

Note: This autoscaler can be used with consumers running on Cloud Run Services
or the new Worker Pools resource. [Sign-up here](https://forms.gle/n29krenCST6QP1gKA)
to enroll in the Worker Pools Private Preview.

## Building the Kafka autoscaler

A container image of Kafka Autoscaler can be built from this source code using Cloud Build.

Update the included `cloudbuild.yaml` to specify the output image name by updating `%ARTIFACT_REGISTRY_IMAGE%`. Example: `us-central1-docker.pkg.dev/my-project/my-repo/my_kafka_autoscaler`

```bash
gcloud builds submit --tag us-central1-docker.pkg.dev/my-project/my-repo/my_kafka_autoscaler
```

This will build the container image and push it to Artifact Registry.

Note that the resulting image will not run locally. It is intended to be layered on top of a Java base image. See [Automatic base image updates](https://cloud.google.com/run/docs/configuring/services/automatic-base-image-updates) for more information including how to reassemble the container image to run locally.

## Setting up and deploying the autoscaler

### Required roles

To get the permissions that you need to deploy and run this service, ask your
administrator to grant you the following IAM roles:

*   [Cloud Run Developer](https://cloud.google.com/iam/docs/understanding-roles#run.developer)
    (roles/run.developer)
*   [Service Account User](https://cloud.google.com/iam/docs/understanding-roles#iam.serviceAccountUser)
    (roles/iam.serviceAccountUser)
*   [Artifact Registry Reader](https://cloud.google.com/iam/docs/understanding-roles#artifactregistry.reader)
    (roles/artifactregistry.reader)
*   [Cloud Scheduler Admin](https://cloud.google.com/iam/docs/understanding-roles#cloudscheduler.admin)
(roles/cloudscheduler.admin) to create the Cloud Scheduler job to trigger
    autoscaling checks
*   [Cloud Tasks Queue Admin](https://cloud.google.com/iam/docs/understanding-roles#cloudtasks.queueAdmin)
(roles/cloudtasks.queueAdmin) to create the Cloud Tasks queue for autoscaling
    checks
*   [Security Admin](https://cloud.google.com/iam/docs/understanding-roles#iam.securityAdmin)
(roles/iam.securityAdmin) to grant permissions to service accounts

## Before you begin

To configure and use the Kafka autoscaler, you'll need the following in place:

*   A Kafka cluster must be running in a location accessible via your VPC, or
    the [Managed Service for Apache
    Kafka](https://cloud.google.com/products/managed-service-for-apache-kafka)
    service.
*   A [Kafka Topic
    configured](https://kafka.apache.org/documentation/#basic_ops_add_topic),
    with events that are being published to that topic.
*   A Kafka consumer workload, deployed to Cloud Run, that is connected to your
    Kafka cluster, and can connect to and pull from the configured topic.

## Best practices

*   Your Kafka consumers should specify a [Consumer Group
    ID](https://kafka.apache.org/documentation/#consumerconfigs_group.id) when
    connecting to the cluster.
*   Connect your Kafka consumers to your VPC network using [Direct
    VPC](https://cloud.google.com/run/docs/configuring/vpc-direct-vpc). This
    allows you to connect to your Kafka cluster using private IP addresses, and
    keep traffic on your VPC network.
*   Configure a [liveness healthcheck](https://cloud.google.com/run/docs/configuring/worker-pools/healthchecks)
    for your Kafka consumers that checks the Consumer is pulling events. This
    ensures unhealthy instances automatically restart if they stop processing
    events, even if the container doesn't crash.

## Set up the Kafka autoscaler

To simplify setup, you can set the following environment variables which will be
used in throughout this user guide:

```bash
# Details for already-deployed Kafka consumer
export PROJECT_ID=<project-id>
export PROJECT_NUM=<project-number>
export REGION=<region>
export CONSUMER_SERVICE_NAME=<deployed-kafka-consumer>
export CONSUMER_SA_EMAIL=<kafka-consumer-service-account-email i.e. NAME@PROJECT-ID.iam.gserviceaccount.com>
export TOPIC_ID=<kafka-topic-id>
export CONSUMER_GROUP_ID=<kafka-consumer-group-id>
export NETWORK=<vpc-network>
export SUBNET=<vpc-subnet>

# Details for new items to be created during this setup
export CLOUD_TASKS_QUEUE_NAME=<cloud-tasks-queue-for-scaling-checks>
export TASKS_SERVICE_ACCOUNT=<tasks-service-account-name>

export SCALER_SERVICE_NAME=<kafka-autoscaler-service-name>
export SCALER_IMAGE_PATH=<kafka-autoscaler-image-URI>
export SCALER_CONFIG_SECRET=<kafka-autoscaler-config-secret-name>

export CYCLE_SECONDS=<scaler-check-frequency e.g. 15>
```

### Create the Kafka Admin client secret

The Kafka autoscaler connects to the Kafka cluster using the configuration
provided in a secret that will be mounted as a volume on the Kafka autoscaler
service.

At a minimum, the `bootstrap.servers` property must be configured with the
bootstrap servers as a list of `HOST:PORT`.

```
bootstrap.servers=%BOOTSTRAP-SERVER-LIST%
```

This secret can contain any of the [Kafka Admin
client](https://kafka.apache.org/documentation/#adminclientconfigs) properties.
For example, to connect to a Google [Managed Service for Apache
Kafka](https://github.com/googleapis/managedkafka?tab=readme-ov-file#kafka-java-auth-client-handler)
 cluster using [application default
credentials](https://cloud.google.com/authentication/provide-credentials-adc)
with Google OAuth, this secret should include the following properties,
substituting the `%BOOTSTRAP-SERVER-LIST%` placeholder with the `HOST:PORT`
list:

```
bootstrap.servers=%BOOTSTRAP-SERVER-LIST%
security.protocol=SASL_SSL
sasl.mechanism=OAUTHBEARER
sasl.login.callback.handler.class=com.google.cloud.hosted.kafka.auth.GcpLoginCallbackHandler
sasl.jaas.config=org.apache.kafka.common.security.oauthbearer.OAuthBearerLoginModule required;
```

To create the secret volume, copy the configuration into a file named
`kafka_auth_config.txt`. From the same directory containing the file, run:

```bash
gcloud secrets create $ADMIN_CLIENT_SECRET --data-file=kafka_auth_config.txt
```

### Create the scaling configuration secret

The Kafka autoscaler uses the configuration provided in this secret to specify
the Kafka consumer to autoscale, as well as to adjust scaling behavior. Like the
Kafka Admin client secret, this will be mounted as a volume on the Kafka
autoscaler service.

Create a file `scaler_config.yaml` and copy the configuration below,
substituting the following placeholders:

*   `%FULLY_QUALIFIED_WORKERPOOL_NAME%` - The Kafka consumer workload to be
    autoscaled. (Format:
    *projects/`$PROJECT_ID`/locations/`$REGION`/`[workerpools|services]`/`$CONSUMER_SERVICE_NAME`*)
*   `%TARGET_CPU_UTILIZATION%` - Target CPU utilization for autoscaling
    calculations (e.g. 60)
*   `%LAG_THRESHOLD%` - Threshold for the `consumer_lag` metric to trigger (e.g.
    1000) autoscaling

You may also change any of the settings under `behavior`, as desired, or you may
leave the defaults. This section supports many of the same properties as
Kubernetes [HPA scaling
policies](https://kubernetes.io/docs/tasks/run-application/horizontal-pod-autoscale/#scaling-policies).

#### Scaling metrics

The following elements must be configured for the Kafka autoscaler to function:

```yaml
spec:
  scaleTargetRef:
    name: %FULLY_QUALIFIED_WORKERPOOL_NAME%
 metrics:
  - type: Resource
    resource:
      name: cpu
      target:
        type: Utilization
        averageUtilization: %TARGET_CPU_UTILIZATION%
  - type: External
    external:
      metric:
        name: consumer_lag
      target:
        type: AverageValue
        averageValue: %LAG_THRESHOLD%
```
Note: While you may choose to configure `consumer_lag` only, we recommend
starting with both `consumer_lag` and `cpu`, as just using `consumer_lag` can
have unexpected results.

##### Optional settings and defaults

In addition the the metric targets, you can optionally configure additional
elements to adjust scaling behavior. If not configured, the default values
are used.

*   `%CPU_ACTIVATION_THRESHOLD%` (default: 0) - Metric will be considered "inactive" when below this threshold. When all metrics are "inactive", target consumer will be scaled to zero.
*   `%CPU_TOLERANCE%` (default: 0.1) - Prevent scaling changes if within
    specified range (as a percent of the configured TARGET_CPU_UTILIZATION)
*   `%CPU_METRIC_WINDOW%` (default: 120) - Period, in seconds, over which the average CPU utilization is calculated
*   `%LAG_ACTIVATION_THRESHOLD%` (default: 0) - Metric will be considered "inactive" when below this threshold. When all metrics are "inactive", target consumer will be scaled to zero.
*   `%LAG_TOLERANCE%` (default: 0.1) - Prevent scaling changes if within
    specified range (as a percent of the configured LAG_THRESHOLD)

```yaml
spec:
  scaleTargetRef:
    name: $FULLY_QUALIFIED_WORKERPOOL_NAME
  metrics:
    - type: Resource
      resource:
        name: cpu
        target:
          type: Utilization
          averageUtilization: %TARGET_CPU_UTILIZATION%
          activationThreshold: `%CPU_ACTIVATION_THRESHOLD%`
          tolerance: %CPU_TOLERANCE%`
          windowSeconds: `%CPU_METRIC_WINDOW%`
    - type: External
      external:
        metric:
          name: consumer_lag
        target:
          type: AverageValue
          averageValue: %LAG_THRESHOLD%
          activationThreshold: `%LAG_ACTIVATION_THRESHOLD%`
          tolerance: `%LAG_TOLERANCE%`
```

#### Optional scaling policies

If only the required elements are set, the default scaling policies, below,
are used. Alternatively, you can configure either, or both, `scaleDown` and/or
`scaleUp` policies. If only one of `scaleDown` or `scaleUp` is configured, the
other will use the default configuration.

```yaml
behavior:
  scaleDown:
    stabilizationWindowSeconds: 300
    policies:
    - type: Percent
      value: 50
      periodSeconds: 30
    selectPolicy: Min
  scaleUp:
    stabilizationWindowSeconds: 0
    policies:
    - type: Percent
      value: 100
      periodSeconds: 15
    - type: Instances
      value: 4
      periodSeconds: 15
    selectPolicy: Max
```

#### Full example config using default values

```yaml
spec:
  scaleTargetRef:
    name: projects/PROJECT-ID/locations/us-central1/workerpools/kafka-consumer-worker
 metrics:
  - type: Resource
    resource:
      name: cpu
      target:
        type: Utilization
        averageUtilization: 60
        activationThreshold: 0
        tolerance: 0.1
        windowSeconds: 120
  - type: External
    external:
      metric:
        name: consumer_lag
      target:
        type: AverageValue
        averageValue: 1000
        activationThreshold: 0
        tolerance: 0.1
behavior:
  scaleDown:
    stabilizationWindowSeconds: 300
    policies:
    - type: Percent
      value: 50
      periodSeconds: 30
    selectPolicy: Min
  scaleUp:
    stabilizationWindowSeconds: 0
    policies:
    - type: Percent
      value: 100
      periodSeconds: 15
    - type: Instances
      value: 4
      periodSeconds: 15
    selectPolicy: Max
```
#### Create the secret

To create the secret, from the same directory containing the
`scaler_config.yaml` file, run:

```bash
gcloud secrets create $SCALER_CONFIG_SECRET --data-file=scaler_config.yaml
```

## Set up additional components and deploy Kafka autoscaler

The `setup_kafka_scaler.sh` script does the following:

*   Creates the Cloud Tasks queue used to trigger autoscaling checks
*   Creates the Kafka autoscaler service account, and grants necessary
    permissions
*   Configures and deploys the Kafka autoscaler
*   Create the Cloud Scheduler job that periodically triggers autoscaling checks

When run, `setup_kafka_scaler.sh` will output the configured environment
variables, please verify they are correct before continuing.

### Grant Kafka autoscaler service account additional permissions

In order to change the instance count of the Kafka consumer, the Kafka
autoscaler service account must have view permission on the deployed container
image. For example, if the consumer image was deployed from Artifact Registry,
run:

```bash
gcloud projects add-iam-policy-binding $PROJECT_ID \
  --member="serviceAccount:$SCALER_SA_NAME@$PROJECT_ID.iam.gserviceaccount.com" \
  --role="roles/artifactregistry.reader"
```

## Verify Kafka autoscaling is working

The Kafka autoscaler service's scaling is triggered with a request to the
service URL.

*   `POST` requests will trigger the autoscaling calculation, output to logging,
    AND change the instance count based on the recommendation

In the logs of your Kafka autoscaler service, you should see messages like
*There are currently X consumers and Y total lag. Recommending Z consumers.*

## Reference: Scaling configuration

Note: See [Kubernetes Horizontal Pod
Autoscaling](https://kubernetes.io/docs/reference/kubernetes-api/workload-resources/horizontal-pod-autoscaler-v2/#HorizontalPodAutoscalerSpec)
documentation for detailed explanation of each element.

```yaml
behavior:
  scaleDown:
    stabilizationWindowSeconds: [INT]
    policies:
    - type: [Percent, Instances]
      value: [INT]
      periodSeconds: [INT]
    selectPolicy: [Min, Max]
  scaleUp:
    stabilizationWindowSeconds: [INT]
    policies:
    - type: [Percent, Instances]
      value: [INT]
      periodSeconds: [INT]
    selectPolicy: [Min, Max]
```

*   `scaleDown`: Behavior when reducing instance count (scaling down)
*   `scaleUp`: Behavior when increasing instance count (scaling up)
*   `stabilizationWindowSeconds`: Uses the highest (scaleDown) or lowest
    (scaleUp) calculated instance count over a rolling period. Setting to 0
    means the latest calculated value is used.
*   `selectPolicy`: Which outcome to enforce when multiple policies are
    configured. Min: smallest change, Max: largest change
*   `Percent`: Changes per-period are limited to the configured percent of total
    instances
*   `Instances`: Changes per-period are limited to the configured number of
    instances
*   `periodSeconds`: Length of time over which the policy is enforced