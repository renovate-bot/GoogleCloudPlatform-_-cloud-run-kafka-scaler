/*
 * Copyright 2025 Google LLC
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 */
package google.cloud.run.kafkascaler;

import java.time.Duration;
import java.util.Collections;
import java.util.Properties;
import org.apache.kafka.clients.consumer.Consumer;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.common.serialization.StringDeserializer;

/** Example Kafka consumer which polls a Kafka topic and prints out the records it receives. */
public final class ExampleConsumer {

  private static final String BOOTSTRAP_SERVERS = System.getenv("BOOTSTRAP_SERVERS");
  private static final String TOPIC_ID = System.getenv("KAFKA_TOPIC_ID");
  private static final String CONSUMER_GROUP_ID = System.getenv("CONSUMER_GROUP_ID");

  public static void main(String[] args) {

    Properties props = new Properties();
    props.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, BOOTSTRAP_SERVERS);
    props.put(ConsumerConfig.GROUP_ID_CONFIG, CONSUMER_GROUP_ID);
    props.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class.getName());
    props.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class.getName());

    props.put("security.protocol", "SASL_SSL");
    props.put("sasl.mechanism", "OAUTHBEARER");
    props.put(
        "sasl.login.callback.handler.class",
        "com.google.cloud.hosted.kafka.auth.GcpLoginCallbackHandler");
    props.put(
        "sasl.jaas.config",
        "org.apache.kafka.common.security.oauthbearer.OAuthBearerLoginModule required;");

    Consumer<String, String> consumer = new KafkaConsumer<>(props);
    consumer.subscribe(Collections.singletonList(TOPIC_ID));

    while (true) {
      ConsumerRecords<String, String> records = consumer.poll(Duration.ofMillis(100));
      records.forEach(
          record -> {
            System.out.printf(
                "Consumed record with key %s and value %s%n", record.key(), record.value());
          });
    }
  }

  private ExampleConsumer() {}
}
