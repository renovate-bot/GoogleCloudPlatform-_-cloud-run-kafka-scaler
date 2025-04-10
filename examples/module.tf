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

provider "google" {
  # Configure your GCP provider as needed
  project = "your-gcp-project-id"
  region  = "us-central1"
}

module "kafka_autoscaler" {
  # Use relative path for local module or remote source like Git for shared module
  source = "../terraform" # Example if running from within the terraform/ directory

  # --- Required Variables (Values based on your environment) ---
  project_id                  = "your-gcp-project-id"
  region                      = "us-central1"
  consumer_sa_email           = "your-consumer-sa@your-gcp-project-id.iam.gserviceaccount.com"
  topic_id                    = "your-kafka-topic"
  consumer_group_id           = "your-kafka-consumer-group"
  admin_client_secret_name    = "kafka-client-secret-name"
  scaler_config_secret_name   = "scaler-config-secret-name"
  cloud_tasks_queue_name      = "kafka-scaler-task-queue"
  scaler_service_name         = "kafka-scaler-service"
  scaler_image_path           = "gcr.io/your-gcp-project-id/kafka-scaler:latest"

  # --- Optional Inputs (uncomment and customize as needed – each of these has a default) ---

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