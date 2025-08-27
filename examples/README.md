# Cloud Run Kafka Autoscaler Example Consumer

This directory contains the source code for a buildable container image of a
example Kafka consumer that works with Google Managed Kafka and is intended to
run on Cloud Run.

Follow the steps below to deploy the example consumer which will subscribe to a
specified topic on Managed Kafka and print the message in the topic.

These steps assume that you've already set up a Kafka Topic in Google Managed
Kafka. If you have not already done so, see the
[Google Managed Kafka quickstart guide](https://cloud.google.com/managed-service-for-apache-kafka/docs/quickstart#create_a_cluster).

## 1. Set Environment Variables

Set a name and region for the Cloud Run worker pool which will run your
consumers.

```bash
export CONSUMER_WORKER_POOL_NAME=<consumer-worker-pool-name>
export REGION=<region>
```

Set an id which your consumer will provide to Kafka e.g.
`my-consumer-group`.

```bash
export CONSUMER_GROUP_ID=<consumer_group-id>
```

Provide the following information about your Google Managed Kafka cluster and
topic.

You can retrieve your cluster's `bootstrap-servers` value from the
`Bootstrap URL` setting within the `Configuration` tab for your Google Managed
Kafka console for the cluster.

```bash
export BOOTSTRAP_SERVERS=<bootstrap-servers>
export TOPIC_ID=<topic-id>
```

## 2. Deploy the consumer
Use the following command to deploy directly from source in this folder
as a Cloud Run Worker Pool. This command will deploy a worker pool with exactly
one instance.

```bash
gcloud alpha run worker-pools deploy $CONSUMER_WORKER_POOL_NAME  \
  --source . \
  --region=$REGION \
  --set-env-vars="BOOTSTRAP_SERVERS=$BOOTSTRAP_SERVERS" \
  --set-env-vars="KAFKA_TOPIC_ID=$TOPIC_ID" \
  --set-env-vars="CONSUMER_GROUP_ID=$CONSUMER_GROUP_ID"
```

## Verify the consumer is successfully polling from your Kafka topic
When your consumer is successfully connected to your Kafka topic, you will see
messages of the format "Consumed record with key %s and value %s" in your
Cloud Monitoring logs.

