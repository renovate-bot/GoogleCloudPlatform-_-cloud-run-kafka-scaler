# This Dockerfile will produce an image that only includes the Java binary and *not* the Java runtime.
# The resulting image will not run locally. It is intended at being layered on top of a Java base image.

FROM gcr.io/cloud-builders/bazel AS builder
WORKDIR /workspace

# Copy project files
COPY . .

RUN bazel version

# Build the Java binary
RUN bazel build src/main/java/com/google/cloud/run/kafkascaler:kafka_scaler_binary_deploy.jar

FROM scratch
WORKDIR /app

COPY --from=builder /workspace/bazel-bin/src/main/java/com/google/cloud/run/kafkascaler/kafka_scaler_binary_deploy.jar /app/kafka_scaler.jar

CMD ["java", "-jar", "/app/kafka_scaler.jar"]