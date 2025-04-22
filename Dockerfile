# This Dockerfile will produce an image that only includes the Java binary and *not* the Java runtime.
# The resulting image will not run locally. It is intended at being layered on top of a Java base image.

FROM gcr.io/cloud-builders/bazel AS builder
WORKDIR /workspace

# Copy project files
COPY . .

# Build the Java binary
RUN bazel build src/main/java/com/google/cloud/run/kafkascaler:kafka_scaler_binary_deploy.jar

FROM scratch
ENV APP_DIR=/app
WORKDIR $APP_DIR

COPY --from=builder /workspace/src/main/java/com/google/cloud/run/kafkascaler/logging.properties $APP_DIR/logging.properties
COPY --from=builder /workspace/bazel-bin/src/main/java/com/google/cloud/run/kafkascaler/kafka_scaler_binary_deploy.jar $APP_DIR/kafka_scaler.jar

ENV JAVA_TOOL_OPTIONS="-Djava.util.logging.config.file=$APP_DIR/logging.properties"

CMD ["java", "-jar", "/app/kafka_scaler.jar"]