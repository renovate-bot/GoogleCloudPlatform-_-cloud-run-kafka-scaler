FROM gcr.io/cloud-builders/bazel AS builder
WORKDIR /workspace

# Copy project files
COPY . .

# Build the Java binary
RUN bazel build src/main/java/com/google/cloud/run/kafkascaler:example_consumer_binary_deploy.jar

FROM gcr.io/distroless/java17-debian12:nonroot

ENV APP_DIR=/app
WORKDIR $APP_DIR

COPY --from=builder /workspace/bazel-bin/src/main/java/com/google/cloud/run/kafkascaler/example_consumer_binary_deploy.jar $APP_DIR/example_consumer.jar

ENTRYPOINT ["java", "-jar", "/app/example_consumer.jar"]