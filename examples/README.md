# Cloud Run Kafka Autoscaler Example Consumer

This directory contains the source code for a buildable container image of a
example Kafka consumer that works with Google Managed Kafka and is intended to
run on Cloud Run. This consumer subscribes to a specified topic and prints
messages.

## 1. Set Environment Variables
Set the required environment variables below.

```bash
export CONSUMER_WORKER_POOL_NAME=<consumer-worker-pool-name>
export REGION=<region>
export BOOTSTRAP_SERVERS=<bootstrap-servers>
export TOPIC_ID=<topic-id>
export CONSUMER_GROUP_ID=<consumer_group-id>
```

## 2. Deploy the consumer
Use the following command to deploy directly from source in this folder
as a Cloud Run Worker Pool.

```bash
gcloud alpha run worker-pools deploy $CONSUMER_WORKER_POOL_NAME  \
  --source . \
  --region=$REGION \
  --set-env-vars="^:^BOOTSTRAP_SERVERS=$BOOTSTRAP_SERVERS" \
  --set-env-vars="KAFKA_TOPIC_ID=$TOPIC_ID" \
  --set-env-vars="CONSUMER_GROUP_ID=$CONSUMER_GROUP_ID"
```

## Verify the consumer is successfully polling from your Kafka topic
When your consumer's successfully connected to your Kafka topic, you will see
messages of the format "Consumed record with key %s and value %s" in your
Cloud Monitoring logs.

