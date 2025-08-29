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



printf "Verify environment variables to be used:\n"
printf "\nDeployed Kafka consumer details:\n"

declare -a missing_vars

if [[ -n "${PROJECT_ID}" ]]; then
  echo "PROJECT_ID: $PROJECT_ID"
else
    missing_vars+=("PROJECT_ID")
fi

if [[ -n "${REGION}" ]]; then
  echo "REGION: $REGION"
else
  missing_vars+=("REGION")
fi

if [[ -n "${CONSUMER_SERVICE_NAME}" ]]; then
  echo "CONSUMER_SERVICE_NAME: $CONSUMER_SERVICE_NAME"
else
  missing_vars+=("CONSUMER_SERVICE_NAME")
fi

if [[ -n "${CONSUMER_SA_EMAIL}" ]]; then
  echo "CONSUMER_SA_EMAIL: $CONSUMER_SA_EMAIL"
else
  missing_vars+=("CONSUMER_SA_EMAIL")
fi

if [[ -n "${TOPIC_ID}" ]]; then
  echo "TOPIC_ID: $TOPIC_ID"
fi

if [[ -n "${CONSUMER_GROUP_ID}" ]]; then
  echo "CONSUMER_GROUP_ID: $CONSUMER_GROUP_ID"
else
  missing_vars+=("CONSUMER_GROUP_ID")
fi

if [[ -n "${NETWORK}" ]]; then
  echo "NETWORK: $NETWORK"
fi

if [[ -n "${SUBNET}" ]]; then
  echo "SUBNET: $SUBNET"
fi

printf "\nKafka auth secret created in previous step:\n"
if [[ -n "${ADMIN_CLIENT_SECRET}" ]]; then
  echo "ADMIN_CLIENT_SECRET is set."
else
  missing_vars+=("ADMIN_CLIENT_SECRET")
fi

if [[ -n "${SCALER_CONFIG_SECRET}" ]]; then
  echo "SCALER_CONFIG_SECRET is set."
else
  missing_vars+=("SCALER_CONFIG_SECRET")
fi

printf "\nNew components that will be created: \n"
if [[ -n "${SCALER_SERVICE_NAME}" ]]; then
  echo "SCALER_SERVICE_NAME: $SCALER_SERVICE_NAME"
  SCALER_SA_NAME=$SCALER_SERVICE_NAME-sa
  CLOUD_SCHEDULER_JOB=$SCALER_SERVICE_NAME-cron
  CLOUD_SCHEDULER_SA=$CLOUD_SCHEDULER_JOB-sa
else
  missing_vars+=("SCALER_SERVICE_NAME")
fi

if [[ -n "${SCALER_IMAGE_PATH}" ]]; then
  echo "SCALER_IMAGE_PATH: $SCALER_IMAGE_PATH"
else
  missing_vars+=("SCALER_IMAGE_PATH")
fi

if [[ -n "${CYCLE_SECONDS}" ]]; then
  printf "\nAutoscaling frequency:\n"
  echo "CYCLE_SECONDS: $CYCLE_SECONDS"

  # If the autoscaling frequency is less than 60 seconds, we need to create a
  # Cloud Tasks queue and a service account to be used by the Cloud Tasks.
  if [[ "$CYCLE_SECONDS" -lt 60 ]]; then
    printf "\nCloud Tasks:\n"
    if [[ -n "${CLOUD_TASKS_QUEUE_NAME}" ]]; then
      echo "CLOUD_TASKS_QUEUE_NAME: $CLOUD_TASKS_QUEUE_NAME"
    else
      missing_vars+=("CLOUD_TASKS_QUEUE_NAME")
    fi

    if [[ -n "${TASKS_SERVICE_ACCOUNT}" ]]; then
      echo "TASKS_SERVICE_ACCOUNT: $TASKS_SERVICE_ACCOUNT"
    else
      missing_vars+=("TASKS_SERVICE_ACCOUNT")
    fi
  fi
else
  missing_vars+=("CYCLE_SECONDS")
fi

if [[ "${#missing_vars[@]}" -gt 0 ]]; then
  printf "\n***** The following variables are required but not set: *****\n"
  for var in "${missing_vars[@]}"; do
    echo "  - $var"
  done
  printf "\n***** Please set the required variables before running the script. *****\n"
  exit 1
fi

read -p "Press any key to continue..."
###################################

###
# Service Accounts setup
###

# Create Kafka autoscaler service account if necessary
SA_EMAIL="$SCALER_SA_NAME@$PROJECT_ID.iam.gserviceaccount.com"
if gcloud iam service-accounts describe "$SA_EMAIL" --project="$PROJECT_ID" &> /dev/null; then
  echo "Service account $SCALER_SA_NAME already exists."
else
  echo "Creating service account $SCALER_SA_NAME..."
  gcloud iam service-accounts create "$SCALER_SA_NAME" --project="$PROJECT_ID"
fi

#Create a service account for Cloud Scheduler if necessary
SA_EMAIL="$CLOUD_SCHEDULER_SA@$PROJECT_ID.iam.gserviceaccount.com"
if gcloud iam service-accounts describe "$SA_EMAIL" --project="$PROJECT_ID" &> /dev/null; then
  echo "Service account $CLOUD_SCHEDULER_SA already exists."
else
  echo "Creating service account $CLOUD_SCHEDULER_SA..."
  gcloud iam service-accounts create "$CLOUD_SCHEDULER_SA" \
      --description="Service Acount for Cloud Scheduler to invoke Cloud Run" \
      --project="$PROJECT_ID"
fi

# Create Tasks service account if necessary
if [[ "$CYCLE_SECONDS" -lt 60 ]]; then
  SA_EMAIL="$TASKS_SERVICE_ACCOUNT@$PROJECT_ID.iam.gserviceaccount.com"
  if gcloud iam service-accounts describe "$SA_EMAIL" --project="$PROJECT_ID" &> /dev/null; then
    echo "Service account $TASKS_SERVICE_ACCOUNT already exists."
  else
    echo "Creating service account $TASKS_SERVICE_ACCOUNT..."
    gcloud iam service-accounts create "$TASKS_SERVICE_ACCOUNT" --project="$PROJECT_ID"
  fi
fi
# Delay to allow time for propagation
sleep 10

####
# Cloud Tasks setup
####

if [[ "$CYCLE_SECONDS" -lt 60 ]]; then
  # Create Cloud Tasks Queue
  gcloud tasks queues create "$CLOUD_TASKS_QUEUE_NAME" --location="$REGION"
  FULLY_QUALIFIED_CLOUD_TASKS_QUEUE_NAME="projects/$PROJECT_ID/locations/$REGION/queues/$CLOUD_TASKS_QUEUE_NAME"
fi

####
# Kafka autoscaler setup
####

# Grant necessary permissions to Kafka autoscaler service account
gcloud iam service-accounts add-iam-policy-binding "$CONSUMER_SA_EMAIL" \
    --member="serviceAccount:$SCALER_SA_NAME@$PROJECT_ID.iam.gserviceaccount.com" \
    --role="roles/iam.serviceAccountUser" \
    --condition=None --quiet

gcloud secrets add-iam-policy-binding "$SCALER_CONFIG_SECRET" \
    --member="serviceAccount:$SCALER_SA_NAME@$PROJECT_ID.iam.gserviceaccount.com" \
    --role="roles/secretmanager.secretAccessor" \
    --condition=None --quiet

gcloud secrets add-iam-policy-binding "$ADMIN_CLIENT_SECRET" \
    --member="serviceAccount:$SCALER_SA_NAME@$PROJECT_ID.iam.gserviceaccount.com" \
    --role="roles/secretmanager.secretAccessor" \
    --condition=None --quiet

gcloud projects add-iam-policy-binding "$PROJECT_ID" \
    --member="serviceAccount:$SCALER_SA_NAME@$PROJECT_ID.iam.gserviceaccount.com" \
    --role="roles/run.admin" \
    --condition=None --quiet

if [[ "$CYCLE_SECONDS" -lt 60 ]]; then
  gcloud tasks queues add-iam-policy-binding "$CLOUD_TASKS_QUEUE_NAME" \
      --member="serviceAccount:$SCALER_SA_NAME@$PROJECT_ID.iam.gserviceaccount.com" \
      --role="roles/cloudtasks.enqueuer" \
      --location="$REGION" \
      --quiet
fi

gcloud projects add-iam-policy-binding "$PROJECT_ID" \
    --member="serviceAccount:$SCALER_SA_NAME@$PROJECT_ID.iam.gserviceaccount.com" \
    --role="roles/monitoring.metricWriter" \
    --condition=None --quiet

gcloud projects add-iam-policy-binding "$PROJECT_ID" \
    --member="serviceAccount:$SCALER_SA_NAME@$PROJECT_ID.iam.gserviceaccount.com" \
    --role="roles/monitoring.viewer" \
    --condition=None --quiet

# Setup Kafka scaler env vars file
echo "CONSUMER_GROUP_ID: ${CONSUMER_GROUP_ID}" > scaler_env_vars.yaml

if [[ -n "${OUTPUT_SCALER_METRICS}" ]]; then
  echo "OUTPUT_SCALER_METRICS: \"${OUTPUT_SCALER_METRICS}\"" >> scaler_env_vars.yaml
fi

if [[ -n "${TOPIC_ID}" ]]; then
  echo "KAFKA_TOPIC_ID: ${TOPIC_ID}" >> scaler_env_vars.yaml
fi

# Only add Cloud Tasks related env vars if they are needed (cycle < 60s)
if [[ "${CYCLE_SECONDS}" -lt 60 ]]; then
  echo "CYCLE_SECONDS: \"${CYCLE_SECONDS}\"
FULLY_QUALIFIED_CLOUD_TASKS_QUEUE_NAME: projects/${PROJECT_ID}/locations/${REGION}/queues/${CLOUD_TASKS_QUEUE_NAME}
INVOKER_SERVICE_ACCOUNT_EMAIL: ${TASKS_SERVICE_ACCOUNT}@${PROJECT_ID}.iam.gserviceaccount.com" >> scaler_env_vars.yaml
fi
####
# Deploy the Kafka autoscaler
####
gcloud run deploy "$SCALER_SERVICE_NAME" \
    --region="$REGION" \
    --image="$SCALER_IMAGE_PATH" \
    --base-image=us-central1-docker.pkg.dev/serverless-runtimes/google-22/runtimes/java17 \
    --no-allow-unauthenticated \
    --vpc-egress=private-ranges-only \
    --network="$NETWORK" \
    --subnet="$SUBNET" \
    --service-account="$SCALER_SA_NAME@$PROJECT_ID.iam.gserviceaccount.com" \
    --max-instances=1 \
    --update-secrets=/kafka-config/kafka-client-properties="$ADMIN_CLIENT_SECRET":latest \
    --update-secrets=/scaler-config/scaling="$SCALER_CONFIG_SECRET":latest \
    --labels=created-by=scaler-kafka \
    --env-vars-file=scaler_env_vars.yaml || exit 1

if [[ "${CYCLE_SECONDS}" -lt 60 ]]; then
# Grant Tasks service account Cloud Run Invoker on the Kafka autoscaler
gcloud run services add-iam-policy-binding "$SCALER_SERVICE_NAME" \
    --member="serviceAccount:$TASKS_SERVICE_ACCOUNT@$PROJECT_ID.iam.gserviceaccount.com" \
    --role="roles/run.invoker"
    --quiet
fi
####
# Create Cloud Scheduler Job to trigger Kafka autoscaler checks
####

#Add necessary permissions
gcloud run services add-iam-policy-binding "$SCALER_SERVICE_NAME" \
    --region="$REGION" \
    --member="serviceAccount:$CLOUD_SCHEDULER_SA@$PROJECT_ID.iam.gserviceaccount.com" \
    --role="roles/run.invoker"

# Create scheduler job if necessary:
if gcloud scheduler jobs describe "$CLOUD_SCHEDULER_JOB" --location="$REGION" &> /dev/null; then
  echo "Cloud Scheduler job $CLOUD_SCHEDULER_JOB already exists."
else
  echo "Creating Cloud Scheduler job $CLOUD_SCHEDULER_JOB..."
  SCALER_URL=$(gcloud run services describe "$SCALER_SERVICE_NAME" --region $REGION --format="value[](status.address.url)")
  gcloud scheduler jobs create http "$CLOUD_SCHEDULER_JOB" \
    --location="$REGION" \
    --schedule="* * * * *" \
    --uri="$SCALER_URL" \
    --oidc-service-account-email="$CLOUD_SCHEDULER_SA@$PROJECT_ID.iam.gserviceaccount.com" \
    --oidc-token-audience="$SCALER_URL"
fi

# Cleanup
rm -f scaler_env_vars.yaml
