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

filegroup(
    name = "data_files",
    srcs = ["scaling.yaml"],
    visibility = ["//third_party/java/foo:private"],
)

java_test(
    name = "ParserTest",
    srcs = ["ParserTest.java"],
    resources = [
        "scaling.yaml",
    ],
    test_class = "com.google.cloud.run.kafkascaler.scalingconfig.ParserTest",
    deps = [
        "//src/main/java/com/google/cloud/run/kafkascaler/scalingconfig:parser",
        "//src/main/java/com/google/cloud/run/kafkascaler/scalingconfig:scaling_config",
        "@maven//:com_google_guava_guava",
        "@maven//:com_google_truth_truth",
        "@maven//:junit_junit",
    ],
)

java_test(
    name = "MergerTest",
    srcs = ["MergerTest.java"],
    test_class = "com.google.cloud.run.kafkascaler.scalingconfig.MergerTest",
    deps = [
        "//src/main/java/com/google/cloud/run/kafkascaler/scalingconfig:default_behavior",
        "//src/main/java/com/google/cloud/run/kafkascaler/scalingconfig:merger",
        "//src/main/java/com/google/cloud/run/kafkascaler/scalingconfig:scaling_config",
        "@maven//:com_google_guava_guava",
        "@maven//:com_google_truth_truth",
        "@maven//:junit_junit",
    ],
)

java_test(
    name = "ValidationTest",
    srcs = ["ValidationTest.java"],
    test_class = "com.google.cloud.run.kafkascaler.scalingconfig.ValidationTest",
    deps = [
        "//src/main/java/com/google/cloud/run/kafkascaler/scalingconfig:validation",
        "@maven//:com_google_guava_guava",
        "@maven//:junit_junit",
    ],
)

java_test(
    name = "PolicyTest",
    srcs = ["PolicyTest.java"],
    test_class = "com.google.cloud.run.kafkascaler.scalingconfig.PolicyTest",
    deps = [
        "//src/main/java/com/google/cloud/run/kafkascaler/scalingconfig:scaling_config",
        "@maven//:com_google_guava_guava",
        "@maven//:com_google_truth_truth",
        "@maven//:junit_junit",
    ],
)
