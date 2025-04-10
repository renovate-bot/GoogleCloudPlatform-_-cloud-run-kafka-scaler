#!/bin/bash
# Copyright 2025 Google LLC
#
# Licensed under the Apache License, Version 2.0 (the "License");
# you may not use this file except in compliance with the License.
# You may obtain a copy of the License at
#
#      http:#www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing, software
# distributed under the License is distributed on an "AS IS" BASIS,
# WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
# See the License for the specific language governing permissions and
# limitations under the License.



SCALER_SA_NAME=$SCALER_SERVICE_NAME-sa
CLOUD_SCHEDULER_JOB=$SCALER_SERVICE_NAME-cron
CLOUD_SCHEDULER_SA=$CLOUD_SCHEDULER_JOB-sa

gcloud scheduler jobs delete $CLOUD_SCHEDULER_JOB --location=$REGION --quiet
gcloud iam service-accounts delete $CLOUD_SCHEDULER_SA@$PROJECT_ID.iam.gserviceaccount.com --quiet
gcloud run services delete $SCALER_SERVICE_NAME --region=$REGION --quiet
gcloud iam service-accounts delete $SCALER_SA_NAME@$PROJECT_ID.iam.gserviceaccount.com --quiet
gcloud iam service-accounts delete $TASKS_SERVICE_ACCOUNT@$PROJECT_ID.iam.gserviceaccount.com --quiet
gcloud tasks queues delete $CLOUD_TASKS_QUEUE_NAME --location=$REGION --quiet