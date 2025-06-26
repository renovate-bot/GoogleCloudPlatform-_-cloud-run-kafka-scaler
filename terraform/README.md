# Terraform Module: Cloud Run Kafka Autoscaler Deployment

## Overview

This Terraform module deploys the **Cloud Run Kafka Autoscaler** described in the [main project README](../README.md). 

It automates the setup of the necessary GCP infrastructure, including the Scaler Cloud Run service, supporting service accounts, IAM bindings, Cloud Tasks queue, and Cloud Scheduler job.

Refer to the [main project README](../../README.md) for:

* Conceptual details about the autoscaler.
* Instructions on building the required container image.
* Detailed prerequisites, including necessary GCP APIs, Kafka setup, and how to create the required Secret Manager secrets (`admin_client_secret_name`, `scaler_config_secret_name`).
* Manual setup steps using `gcloud` and shell scripts (if you prefer to not use Terraform).

## Prerequisites (Terraform Specific)

Ensure you have met the prerequisites outlined in the main project README, specifically:

1.  **Enabled GCP APIs.**
2.  **Existing Kafka Consumer SA** (`consumer_sa_email`) with permissions to read Kafka offsets.
3.  **Existing Secret Manager secrets** (`admin_client_secret_name`, `scaler_config_secret_name`).
4.  **Built and pushed container image** (`scaler_image_path`).
5.  **Terraform and Google Provider** installed and configured (see Requirements below).

## Usage Example (also included in examples/)

```terraform
provider "google" {
  # Configure your GCP provider as needed
  project = "your-gcp-project-id"
  region  = "us-central1"
}

module "kafka_autoscaler" {
  # Use relative path for local module or remote source like Git for shared module
  source = "terraform/"

  # --- Required Variables (Values based on your environment) ---
  project_id                  = "your-gcp-project-id"
  region                      = "us-central1"
  consumer_sa_email           = "your-consumer-sa@your-gcp-project-id.iam.gserviceaccount.com"
  consumer_group_id           = "your-kafka-consumer-group"
  admin_client_secret_name    = "kafka-client-secret-name"
  scaler_config_secret_name   = "scaler-config-secret-name"
  cloud_tasks_queue_name      = "kafka-scaler-task-queue"
  scaler_service_name         = "kafka-scaler-service"
  scaler_image_path           = "gcr.io/your-gcp-project-id/kafka-scaler:latest"

  # --- Optional Inputs (uncomment and customize as needed – each of these has a default) ---

  # Set a single topic for your consumer to scale on.
  # topic_id                      = "your-kafka-topic"

  # VPC Access (Requires existing network/subnet)
  # network                       = "your-vpc-network"
  # subnet                        = "your-subnet"

  # Timing & Naming
  # scaler_cycle_seconds          = 120
  # scheduler_schedule            = "*/5 * * * *"
  # tasks_service_account_name    = "custom-tasks-invoker"
  # scaler_sa_name_prefix         = "prod-"
  # scheduler_sa_name_prefix      = "prod-"

  # Labels & Versions
  # additional_labels             = { environment = "production", component = "kafka-scaler" }
  # scaler_config_secret_version  = "5"
  # admin_client_secret_version   = "2"

  # Roles & Protection
  # grant_managed_kafka_client_role = true
}
```

## Inputs

| Name                              | Description                                                                                                                                                              | Type          | Default                 | Required |
| --------------------------------- | ------------------------------------------------------------------------------------------------------------------------------------------------------------------------ | ------------- | ----------------------- | :------: |
| `project_id`                      | The Google Cloud project ID where resources will be deployed.                                                                                                            | `string`      | -                       | Yes      |
| `region`                          | The Google Cloud region for deployment (e.g., `us-central1`).                                                                                                            | `string`      | -                       | Yes      |
| `consumer_sa_email`               | The email address of the existing Service Account used by the Kafka consumer workload. **This SA requires permissions to read Kafka consumer group offsets.**             | `string`      | -                       | Yes      |
| `consumer_group_id`               | The Kafka Consumer Group ID associated with the topic that the autoscaler will monitor.                                                                                  | `string`      | -                       | Yes      |
| `admin_client_secret_name`        | The name of the existing Secret Manager secret containing Kafka admin client connection properties (e.g., `bootstrap.servers`).                                            | `string`      | -                       | Yes      |
| `scaler_config_secret_name`       | The name of the existing Secret Manager secret containing the scaler's YAML configuration (specifying target, metrics, behavior etc.).                                     | `string`      | -                       | Yes      |
| `cloud_tasks_queue_name`          | The desired name for the Cloud Tasks queue to be created by this module.                                                                                                  | `string`      | -                       | Yes      |
| `scaler_service_name`             | The desired name for the Cloud Run Kafka autoscaler service to be created by this module.                                                                                 | `string`      | -                       | Yes      |
| `scaler_image_path`               | The full path to the pre-built container image for the Kafka autoscaler (e.g., `gcr.io/my-project/kafka-scaler:latest`).                                                  | `string`      | -                       | Yes      |
| `topic_id`                        | Optional: A single Kafka Topic ID the autoscaler will monitor. If unspecified, the consumer group will be autoscaled on all topics to which it's subscribed.             | `string`      | -                       | No       |
| `network`                         | Optional: The self-link or ID of the VPC network for Cloud Run egress configuration. Example: `projects/PROJECT_ID/global/networks/NETWORK_NAME`.                        | `string`      | `null`                  | No       |
| `subnet`                          | Optional: The self-link or ID of the VPC subnet for Cloud Run egress configuration. Example: `projects/PROJECT_ID/regions/REGION/subnetworks/SUBNET_NAME`.              | `string`      | `null`                  | No       |
| `scaler_cycle_seconds`            | Optional: The cycle duration in seconds for the autoscaler's core logic loop. Passed as `CYCLE_SECONDS` env var to the container.                                         | `number`      | `60`                    | No       |
| `scheduler_schedule`              | Optional: The cron schedule (in standard cron format) for triggering the Cloud Scheduler job which invokes the scaler service.                                           | `string`      | `"* * * * *"`           | No       |
| `tasks_service_account_name`      | Optional: The short name used to create the Service Account for the Cloud Tasks invoker identity. Email will be `<name>@<project_id>.iam.gserviceaccount.com`.           | `string`      | `"kafka-tasks-invoker"` | No       |
| `scaler_sa_name_prefix`           | Optional: A prefix added to the name of the scaler Service Account created by this module. The final SA ID will be `<prefix><scaler_service_name>-sa`.                | `string`      | `""`                    | No       |
| `scheduler_sa_name_prefix`        | Optional: A prefix added to the name of the scheduler Service Account created by this module. The final SA ID will be `<prefix><scaler_service_name>-cron-sa`.          | `string`      | `""`                    | No       |
| `additional_labels`               | Optional: A map of additional string labels to apply to created resources like Cloud Run, Cloud Tasks queue, etc., where supported.                                      | `map(string)` | `{}`                    | No       |
| `scaler_config_secret_version`    | Optional: Version of the `scaler_config_secret_name` secret to mount in the Cloud Run service. Use 'latest' or a specific version number (e.g., '3').                    | `string`      | `"latest"`              | No       |
| `admin_client_secret_version`     | Optional: Version of the `admin_client_secret_name` secret to mount in the Cloud Run service. Use 'latest' or a specific version number (e.g., '5').                     | `string`      | `"latest"`              | No       |
| `grant_managed_kafka_client_role` | Optional: If set to `true`, grants the `roles/managedkafka.client` role on the project to the scaler service account created by this module. This is required if you want to use Managed Kafka. | `bool`        | `false`                 | No       |

## Outputs

| Name                           | Description                                                                   |
| ------------------------------ | ----------------------------------------------------------------------------- |
| `scaler_cloud_run_service_url` | The HTTPS URL of the deployed Kafka autoscaler Cloud Run service.             |
| `tasks_queue_name`             | The fully qualified name of the created Cloud Tasks queue (`projects/...`).   |
| `scaler_service_account_email` | The email address of the Service Account created for the scaler Cloud Run service. |
