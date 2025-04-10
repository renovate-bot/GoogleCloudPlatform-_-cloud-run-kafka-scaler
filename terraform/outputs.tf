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

# terraform/outputs.tf

output "scaler_cloud_run_service_url" {
  description = "The URL of the deployed Kafka autoscaler Cloud Run service."
  # Value comes from the 'uri' attribute of the service created in the module's main.tf
  value       = google_cloud_run_v2_service.scaler_service.uri
  sensitive   = false
}

output "tasks_queue_name" {
  description = "The fully qualified name of the created Cloud Tasks queue (projects/...)."
  value       = "projects/${google_cloud_tasks_queue.queue.project}/locations/${google_cloud_tasks_queue.queue.location}/queues/${google_cloud_tasks_queue.queue.name}"
}

output "scaler_service_account_email" {
  description = "The email address of the Service Account created for the scaler Cloud Run service."
  value       = local.scaler_sa_email
}