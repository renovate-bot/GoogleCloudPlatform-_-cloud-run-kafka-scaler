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

# Placeholder: load java_test

package(default_visibility = ["//visibility:private"])

java_test(
    name = "ScalingStabilizerTest",
    srcs = ["ScalingStabilizerTest.java"],
    test_class = "com.google.cloud.run.kafkascaler.ScalingStabilizerTest",
    deps = [
        "//src/main/java/com/google/cloud/run/kafkascaler:scaling_stabilizer",
        "//src/main/java/com/google/cloud/run/kafkascaler/scalingconfig:scaling_config",
        "@maven//:com_google_guava_guava",
        "@maven//:com_google_truth_truth",
        "@maven//:junit_junit",
    ],
)

java_test(
    name = "WorkloadInfoParserTest",
    srcs = ["WorkloadInfoParserTest.java"],
    test_class = "com.google.cloud.run.kafkascaler.WorkloadInfoParserTest",
    deps = [
        "//src/main/java/com/google/cloud/run/kafkascaler:workload_info_parser",
        "@maven//:com_google_truth_truth",
        "@maven//:junit_junit",
    ],
)

java_test(
    name = "SelfSchedulerTest",
    srcs = ["SelfSchedulerTest.java"],
    test_class = "com.google.cloud.run.kafkascaler.SelfSchedulerTest",
    deps = [
        "//src/main/java/com/google/cloud/run/kafkascaler:configuration_provider",
        "//src/main/java/com/google/cloud/run/kafkascaler:self_scheduler",
        "//src/main/java/com/google/cloud/run/kafkascaler/clients:cloud_run_metadata_client",
        "//src/main/java/com/google/cloud/run/kafkascaler/clients:cloud_tasks_client_wrapper",
        "@maven//:com_google_api_grpc_proto_google_cloud_tasks_v2",
        "@maven//:com_google_guava_guava",
        "@maven//:com_google_protobuf_protobuf_java",
        "@maven//:com_google_truth_truth",
        "@maven//:junit_junit",
        "@maven//:org_mockito_mockito_core",
    ],
)

java_test(
    name = "LagScalingTest",
    srcs = ["LagScalingTest.java"],
    test_class = "com.google.cloud.run.kafkascaler.LagScalingTest",
    deps = [
        "//src/main/java/com/google/cloud/run/kafkascaler:lag_scaling",
        "//src/main/java/com/google/cloud/run/kafkascaler/scalingconfig:scaling_config",
        "@maven//:com_google_truth_truth",
        "@maven//:junit_junit",
    ],
)

java_test(
    name = "CpuScalingTest",
    srcs = ["CpuScalingTest.java"],
    test_class = "com.google.cloud.run.kafkascaler.CpuScalingTest",
    deps = [
        "//src/main/java/com/google/cloud/run/kafkascaler:cpu_scaling",
        "//src/main/java/com/google/cloud/run/kafkascaler:metrics_service",
        "//src/main/java/com/google/cloud/run/kafkascaler/scalingconfig:scaling_config",
        "@maven//:com_google_guava_guava",
        "@maven//:com_google_truth_truth",
        "@maven//:junit_junit",
    ],
)

java_test(
    name = "KafkaTest",
    srcs = ["KafkaTest.java"],
    test_class = "com.google.cloud.run.kafkascaler.KafkaTest",
    deps = [
        "//src/main/java/com/google/cloud/run/kafkascaler:kafka",
        "//src/main/java/com/google/cloud/run/kafkascaler/clients:kafka_admin_client_wrapper",
        "@maven//:com_google_guava_guava",
        "@maven//:com_google_truth_truth",
        "@maven//:junit_junit",
        "@maven//:org_apache_kafka_kafka_clients",
        "@maven//:org_mockito_mockito_core",
    ],
)

java_test(
    name = "ScalerTest",
    srcs = ["ScalerTest.java"],
    test_class = "com.google.cloud.run.kafkascaler.ScalerTest",
    deps = [
        "//src/main/java/com/google/cloud/run/kafkascaler:configuration_provider",
        "//src/main/java/com/google/cloud/run/kafkascaler:kafka",
        "//src/main/java/com/google/cloud/run/kafkascaler:metrics_service",
        "//src/main/java/com/google/cloud/run/kafkascaler:scaler",
        "//src/main/java/com/google/cloud/run/kafkascaler:scaling_stabilizer",
        "//src/main/java/com/google/cloud/run/kafkascaler:workload_info_parser",
        "//src/main/java/com/google/cloud/run/kafkascaler/clients:cloud_run_client_wrapper",
        "//src/main/java/com/google/cloud/run/kafkascaler/scalingconfig:scaling_config",
        "@maven//:com_google_guava_guava",
        "@maven//:junit_junit",
        "@maven//:org_apache_kafka_kafka_clients",
        "@maven//:org_mockito_mockito_core",
    ],
)

java_test(
    name = "RequestResponseHandlerTest",
    srcs = ["RequestResponseHandlerTest.java"],
    test_class = "com.google.cloud.run.kafkascaler.RequestResponseHandlerTest",
    deps = [
        "//src/main/java/com/google/cloud/run/kafkascaler:request_response_handler",
        "@maven//:com_google_guava_guava",
        "@maven//:com_google_truth_truth",
        "@maven//:junit_junit",
        "@maven//:org_mockito_mockito_core",
    ],
)

java_test(
    name = "ConfigurationProviderTest",
    srcs = ["ConfigurationProviderTest.java"],
    resources = [
        "bad_client_properties.yaml",
        "client_properties.yaml",
        "scaling.yaml",
    ],
    test_class = "com.google.cloud.run.kafkascaler.ConfigurationProviderTest",
    deps = [
        "//src/main/java/com/google/cloud/run/kafkascaler:configuration_provider",
        "//src/main/java/com/google/cloud/run/kafkascaler:workload_info_parser",
        "//src/main/java/com/google/cloud/run/kafkascaler/scalingconfig:scaling_config",
        "@maven//:com_google_guava_guava",
        "@maven//:com_google_truth_truth",
        "@maven//:junit_junit",
    ],
)

java_test(
    name = "InstanceCountProviderTest",
    srcs = ["InstanceCountProviderTest.java"],
    test_class = "com.google.cloud.run.kafkascaler.InstanceCountProviderTest",
    deps = [
        "//src/main/java/com/google/cloud/run/kafkascaler:instance_count_provider",
        "//src/main/java/com/google/cloud/run/kafkascaler:workload_info_parser",
        "//src/main/java/com/google/cloud/run/kafkascaler/clients:cloud_run_client_wrapper",
        "@maven//:com_google_truth_truth",
        "@maven//:junit_junit",
        "@maven//:org_mockito_mockito_core",
    ],
)

java_test(
    name = "MetricsServiceTest",
    srcs = ["MetricsServiceTest.java"],
    test_class = "com.google.cloud.run.kafkascaler.MetricsServiceTest",
    deps = [
        "//src/main/java/com/google/cloud/run/kafkascaler:metrics_service",
        "//src/main/java/com/google/cloud/run/kafkascaler/clients:cloud_monitoring_client_wrapper",
        "@maven//:com_google_api_grpc_proto_google_cloud_monitoring_v3",
        "@maven//:com_google_api_grpc_proto_google_common_protos",
        "@maven//:com_google_cloud_google_cloud_monitoring",
        "@maven//:com_google_guava_guava",
        "@maven//:com_google_truth_truth",
        "@maven//:junit_junit",
        "@maven//:org_mockito_mockito_core",
    ],
)
