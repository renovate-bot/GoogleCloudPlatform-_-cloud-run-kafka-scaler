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







package(default_visibility = ["//visibility:public"])

java_library(
    name = "scaling_stabilizer",
    srcs = ["ScalingStabilizer.java"],
    deps = [
        "//src/main/java/com/google/cloud/run/kafkascaler/scalingconfig:scaling_config",
        "@maven//:com_google_flogger_flogger",
    ],
)

java_library(
    name = "workload_info_parser",
    srcs = ["WorkloadInfoParser.java"],
)

java_library(
    name = "self_scheduler",
    srcs = ["SelfScheduler.java"],
    deps = [
        ":configuration_provider",
        "//src/main/java/com/google/cloud/run/kafkascaler/clients:cloud_run_metadata_client",
        "//src/main/java/com/google/cloud/run/kafkascaler/clients:cloud_tasks_client_wrapper",
        "@maven//:com_google_api_grpc_proto_google_cloud_tasks_v2",
        "@maven//:com_google_flogger_flogger",
        "@maven//:com_google_guava_guava",
        "@maven//:com_google_protobuf_protobuf_java",
    ],
)

java_library(
    name = "scaler",
    srcs = ["Scaler.java"],
    deps = [
        ":configuration_provider",
        ":cpu_scaling",
        ":instance_count_provider",
        ":kafka",
        ":lag_scaling",
        ":metrics_service",
        ":scaling_stabilizer",
        ":workload_info_parser",
        "//src/main/java/com/google/cloud/run/kafkascaler/clients:cloud_run_client_wrapper",
        "//src/main/java/com/google/cloud/run/kafkascaler/scalingconfig:scaling_config",
        "@maven//:com_google_flogger_flogger",
        "@maven//:com_google_guava_guava",
        "@maven//:org_apache_kafka_kafka_clients",
    ],
)

java_library(
    name = "lag_scaling",
    srcs = ["LagScaling.java"],
    deps = [
        "//src/main/java/com/google/cloud/run/kafkascaler/scalingconfig:scaling_config",
        "@maven//:com_google_flogger_flogger",
    ],
)

java_library(
    name = "cpu_scaling",
    srcs = ["CpuScaling.java"],
    deps = [
        ":metrics_service",
        "//src/main/java/com/google/cloud/run/kafkascaler/scalingconfig:scaling_config",
        "@maven//:com_google_flogger_flogger",
    ],
)

java_library(
    name = "kafka",
    srcs = ["Kafka.java"],
    deps = [
        "//src/main/java/com/google/cloud/run/kafkascaler/clients:kafka_admin_client_wrapper",
        "@maven//:org_apache_kafka_kafka_clients",
    ],
)

java_library(
    name = "configuration_provider",
    srcs = ["ConfigurationProvider.java"],
    deps = [
        ":workload_info_parser",
        "//src/main/java/com/google/cloud/run/kafkascaler/scalingconfig:default_behavior",
        "//src/main/java/com/google/cloud/run/kafkascaler/scalingconfig:merger",
        "//src/main/java/com/google/cloud/run/kafkascaler/scalingconfig:parser",
        "//src/main/java/com/google/cloud/run/kafkascaler/scalingconfig:scaling_config",
        "@maven//:com_google_flogger_flogger",
        "@maven//:com_google_guava_guava",
        "@maven//:org_apache_kafka_kafka_clients",
    ],
)

java_library(
    name = "request_response_handler",
    srcs = ["RequestResponseHandler.java"],
    deps = [
        "@maven//:com_google_flogger_flogger",
        "@maven//:com_google_guava_guava",
    ],
)

java_library(
    name = "instance_count_provider",
    srcs = ["InstanceCountProvider.java"],
    deps = [
        ":workload_info_parser",
        "//src/main/java/com/google/cloud/run/kafkascaler/clients:cloud_run_client_wrapper",
    ],
)

java_library(
    name = "metrics_service",
    srcs = ["MetricsService.java"],
    deps = [
        "//src/main/java/com/google/cloud/run/kafkascaler/clients:cloud_monitoring_client_wrapper",
        "@maven//:com_google_api_grpc_proto_google_cloud_monitoring_v3",
        "@maven//:com_google_api_grpc_proto_google_common_protos",
        "@maven//:com_google_cloud_google_cloud_monitoring",
        "@maven//:com_google_flogger_flogger",
        "@maven//:com_google_guava_guava",
        "@maven//:com_google_protobuf_protobuf_java_util",
    ],
)

java_binary(
    name = "kafka_scaler_binary",
    srcs = ["KafkaScaler.java"],
    data = [
        "logging.properties",
    ],
    jvm_flags = ["-Djava.util.logging.config.file=src/main/java/com/google/cloud/run/kafkascaler/logging.properties"],
    main_class = "com.google.cloud.run.kafkascaler.KafkaScaler",
    runtime_deps = [
        "@maven//:com_google_cloud_hosted_kafka_managed_kafka_auth_login_handler",  # required for connecting to Managed Kafka
        "@maven//:com_google_flogger_flogger_system_backend",
    ],
    deps = [
        ":configuration_provider",
        ":instance_count_provider",
        ":kafka",
        ":metrics_service",
        ":request_response_handler",
        ":scaler",
        ":scaling_stabilizer",
        ":self_scheduler",
        ":workload_info_parser",
        "//src/main/java/com/google/cloud/run/kafkascaler/clients:cloud_monitoring_client_wrapper",
        "//src/main/java/com/google/cloud/run/kafkascaler/clients:cloud_run_client_wrapper",
        "//src/main/java/com/google/cloud/run/kafkascaler/clients:cloud_run_metadata_client",
        "//src/main/java/com/google/cloud/run/kafkascaler/clients:cloud_tasks_client_wrapper",
        "//src/main/java/com/google/cloud/run/kafkascaler/clients:kafka_admin_client_wrapper",
        "//src/main/java/com/google/cloud/run/kafkascaler/scalingconfig:scaling_config",
        "@maven//:com_google_flogger_flogger",
        "@maven//:com_google_guava_guava",
    ],
)
