#
# Copyright 2025 Google LLC
#
# Licensed under the Apache License, Version 2.0 (the "License");
# you may not use this file except in compliance with the License.
# You may obtain a copy of the License at
#
#     http://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing, software
# distributed under the License is distributed on an "AS IS" BASIS,
# WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
# See the License for the specific language governing permissions and
# limitations under the License.
#

load("@bazel_tools//tools/build_defs/repo:http.bzl", "http_archive")

http_archive(
    name = "rules_license",
    urls = [
        "https://github.com/bazelbuild/rules_license/releases/download/0.0.4/rules_license-0.0.4.tar.gz",
        "https://mirror.bazel.build/github.com/bazelbuild/rules_license/releases/download/0.0.4/rules_license-0.0.4.tar.gz",
    ],
    sha256 = "6157e1e68378532d0241ecd15d3c45f6e5cfd98fc10846045509fb2a7cc9e381",
)

RULES_JVM_EXTERNAL_TAG = "4.3"
RULES_JVM_EXTERNAL_SHA = "6274687f6fc5783b589f56a2f1ed60de3ce1f99bc4e8f9edef3de43bdf7c6e74"

http_archive(
    name = "rules_jvm_external",
    sha256 = RULES_JVM_EXTERNAL_SHA,
    strip_prefix = "rules_jvm_external-%s" % RULES_JVM_EXTERNAL_TAG,
    url = "https://github.com/bazelbuild/rules_jvm_external/archive/%s.zip" % RULES_JVM_EXTERNAL_TAG,
)

load("@rules_jvm_external//:defs.bzl", "maven_install")

# Versions need to match otherwise strict deps complains about indirect dependencies
CLOUD_TASKS_PROTO_VERSION = "2.58.0"
CLOUD_MONITORING_PROTO_VERSION = "3.61.0"
PROTOBUF_VERSION = "4.29.3"

MAVEN_ARTIFACTS = [
    "com.google.api.grpc:proto-google-cloud-monitoring-v3:" + CLOUD_MONITORING_PROTO_VERSION,
    "com.google.api.grpc:proto-google-cloud-tasks-v2:" + CLOUD_TASKS_PROTO_VERSION,
    "com.google.api.grpc:proto-google-common-protos:2.54.1",
    "com.google.api-client:google-api-client-gson:2.7.2",
    "com.google.apis:google-api-services-run:v2-rev20250223-2.0.0", # Cloud Run API client library
    "com.google.auth:google-auth-library-oauth2-http:1.33.1",
    "com.google.auto.value:auto-value:1.11.0",
    "com.google.auto.value:auto-value-annotations:1.11.0",
    "com.google.cloud:google-cloud-monitoring:" + CLOUD_MONITORING_PROTO_VERSION,
    "com.google.cloud:google-cloud-tasks:" + CLOUD_TASKS_PROTO_VERSION,
    "com.google.cloud.hosted.kafka:managed-kafka-auth-login-handler:1.0.5",
    "com.google.code.findbugs:jsr305:3.0.2",
    "com.google.guava:guava:32.1.3-jre",
    "com.google.http-client:google-http-client:1.46.3",
    "com.google.http-client:google-http-client-gson:1.46.3", # We need this otherwise strict deps complains about using an indirect dependency for GsonFactory
    "com.google.protobuf:protobuf-java:" + PROTOBUF_VERSION,
    "com.google.protobuf:protobuf-java-util:" + PROTOBUF_VERSION,
    "com.google.truth:truth:1.4.4",
    "junit:junit:4.13.2",
    "org.apache.kafka:kafka-clients:3.9.0",
    "org.mockito:mockito-core:5.17.0",
    "org.yaml:snakeyaml:2.4",
]

# Set --enable_workspace=true flag when running bazel build.
maven_install(
    name= "maven",
    artifacts = MAVEN_ARTIFACTS,
    repositories = [
        "https://repo1.maven.org/maven2",
        "https://packages.confluent.io/maven/", # Needed for a dependecy of managed-kafka-auth-login-handler
    ],
)