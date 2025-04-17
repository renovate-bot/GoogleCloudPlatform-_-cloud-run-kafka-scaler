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

variable "project_id" {
  description = "The Google Cloud project ID where resources will be deployed."
  type        = string
}

variable "region" {
  description = "The Google Cloud region for deployment."
  type        = string
}

variable "consumer_sa_email" {
  description = "The email address of the service account used by the Kafka consumer service or worker pool."
  type        = string
}

variable "topic_id" {
  description = "The Kafka Topic ID the autoscaler will monitor."
  type        = string
}

variable "consumer_group_id" {
  description = "The Kafka Consumer Group ID associated with the topic that the autoscaler will monitor."
  type        = string
}

variable "admin_client_secret_name" {
  description = "The name of the existing Secret Manager secret containing Kafka admin client properties (e.g., kafka-client-properties)."
  type        = string
}

variable "scaler_config_secret_name" {
  description = "The name of the existing Secret Manager secret containing the scaler's YAML configuration (specifying target, metrics, behavior etc.).	"
  type        = string
}

variable "cloud_tasks_queue_name" {
  description = "The desired name for the Cloud Tasks queue."
  type        = string
}

variable "scaler_service_name" {
  description = "The desired name for the Cloud Run Kafka autoscaler service to be created by this module."
  type        = string
}

variable "scaler_image_path" {
  description = "The full path to the container image for the Kafka autoscaler (e.g., 'gcr.io/my-project/kafka-scaler:latest')."
  type        = string
}

variable "network" {
  description = "The self-link or ID of the VPC network for Cloud Run egress configuration. Example: projects/PROJECT_ID/global/networks/NETWORK_NAME."
  type        = string
  default     = null
}

variable "subnet" {
  description = "The self-link or ID of the VPC subnet for Cloud Run egress configuration. Example: projects/PROJECT_ID/regions/REGION/subnetworks/SUBNET_NAME."
  type        = string
  default     = null 
}

variable "scaler_cycle_seconds" {
  description = "The cycle duration in seconds for the autoscaler's core logic loop. Passed as CYCLE_SECONDS env var to the container. If overridden, please set it to at least 5 seconds."
  type        = number
  default     = 60
}

variable "scheduler_schedule" {
  description = "The cron schedule (in standard cron format) for triggering the Cloud Scheduler job which invokes the scaler service."
  type        = string
  default     = "* * * * *"
}

variable "tasks_service_account_name" {
  description = "The short name used to create the Service Account for the Cloud Tasks invoker identity. Email will be <name>@<project_id>.iam.gserviceaccount.com"
  type        = string
  default     = "kafka-tasks-invoker"
}

variable "scaler_sa_name_prefix" {
  description = "A prefix added to the name of the scaler Service Account created by this module. The final SA ID will be <prefix><scaler_service_name>-sa"
  type        = string
  default     = ""
}

variable "scheduler_sa_name_prefix" {
  description = "A prefix added to the name of the scheduler Service Account created by this module. The final SA ID will be <prefix><scaler_service_name>-cron-sa"
  type        = string
  default     = ""
}

variable "additional_labels" {
  description = "A map of additional string labels to apply to created resources like Cloud Run, Cloud Tasks queue, etc., where supported"
  type        = map(string)
  default     = {}
}

variable "scaler_config_secret_version" {
  description = "Version of the scaler_config_secret_name secret to mount in the Cloud Run service. Use 'latest' or a specific version number (e.g., '3')"
  type        = string
  default     = "latest"
}

variable "admin_client_secret_version" {
  description = "Version of the admin_client_secret_name secret to mount in the Cloud Run service. Use 'latest' or a specific version number (e.g., '5')."
  type        = string
  default     = "latest"
}

variable "grant_managed_kafka_client_role" {
  description = "Optional: If set to true, grants the roles/managedkafka.client role on the project to the scaler service account created by this module.	This is required if you want to use Managed Kafka"
  type        = bool
  default     = false
}
