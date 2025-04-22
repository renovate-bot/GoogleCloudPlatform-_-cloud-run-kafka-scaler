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

# Placeholder: load java_library

package(default_visibility = [
    "//third_party/java/cloud_run_consumer_lag_scaler:__subpackages__",
])

java_library(
    name = "scaling_config",
    srcs = glob(
        ["*.java"],
        exclude = ["Parser.java"],
    ),
    deps = [
        "//third_party:auto_value",
        "@maven//:com_google_code_findbugs_jsr305",
        "@maven//:com_google_guava_guava",
    ],
)

java_library(
    name = "parser",
    srcs = ["Parser.java"],
    deps = [
        ":scaling_config",
        "@maven//:org_yaml_snakeyaml",
    ],
)

java_library(
    name = "merger",
    srcs = ["Merger.java"],
    deps = [
        ":scaling_config",
    ],
)

java_library(
    name = "default_behavior",
    srcs = ["DefaultBehavior.java"],
    deps = [
        ":scaling_config",
        "@maven//:com_google_guava_guava",
    ],
)

java_library(
    name = "validation",
    srcs = ["Validation.java"],
    deps = [
    ],
)
