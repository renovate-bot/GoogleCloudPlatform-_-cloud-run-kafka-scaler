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
package com.google.cloud.run.kafkascaler;

import static java.lang.Math.max;

import com.google.cloud.run.kafkascaler.clients.KafkaAdminClientWrapper;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ExecutionException;
import org.apache.kafka.clients.admin.ConsumerGroupDescription;
import org.apache.kafka.clients.admin.ListOffsetsResult;
import org.apache.kafka.clients.admin.OffsetSpec;
import org.apache.kafka.clients.admin.TopicDescription;
import org.apache.kafka.clients.consumer.OffsetAndMetadata;
import org.apache.kafka.common.TopicPartition;
import org.apache.kafka.common.TopicPartitionInfo;

/** A utility class for interacting with Kafka. */
public class Kafka {

  private final KafkaAdminClientWrapper adminClient;

  /**
   * Constructs a new Kafka instance.
   *
   * @param adminClient The Kafka admin client to use.
   */
  public Kafka(KafkaAdminClientWrapper adminClient) {
    this.adminClient = adminClient;
  }

  /**
   * Checks if a topic exists.
   *
   * @param topicName The name of the topic to check.
   * @return True if the topic exists, false otherwise.
   */
  public boolean doesTopicExist(String topicName) throws InterruptedException, ExecutionException {
    return adminClient.listTopics().contains(topicName);
  }

  /**
   * Gets the current number of consumers in a consumer group.
   *
   * <p>It's reasonable for the consumer group to not exist yet if there's never been any consumer
   * instances started.
   *
   * @param consumerGroupId The ID of the consumer group.
   * @return The number of consumers in the group, or 0 if the group does not exist.
   */
  public int getCurrentConsumerCount(String consumerGroupId)
      throws InterruptedException, ExecutionException {
    ConsumerGroupDescription consumerGroupDescription =
        adminClient.describeConsumerGroup(consumerGroupId);

    return consumerGroupDescription == null ? 0 : consumerGroupDescription.members().size();
  }

  /**
   * Gets the lag for each partition of a given topic and consumer group.
   *
   * @param topicName The name of the topic.
   * @param consumerGroupId The ID of the consumer group.
   * @return An Optional containing a map of partition to lag, or an empty Optional if the topic
   *     does not exist.
   */
  public Optional<Map<TopicPartition, Long>> getLagPerPartition(
      String topicName, String consumerGroupId) throws InterruptedException, ExecutionException {
    Map<String, TopicDescription> topicDescription = adminClient.describeTopics(topicName);

    if (!topicDescription.containsKey(topicName)) {
      return Optional.empty();
    }

    List<TopicPartitionInfo> partitions = topicDescription.get(topicName).partitions();

    Map<TopicPartition, OffsetSpec> topicPartitionOffsets = new HashMap<>();
    for (TopicPartitionInfo partition : partitions) {
      TopicPartition topicPartition = new TopicPartition(topicName, partition.partition());
      topicPartitionOffsets.put(topicPartition, OffsetSpec.latest());
    }
    Map<TopicPartition, ListOffsetsResult.ListOffsetsResultInfo> topicOffsets =
        adminClient.listOffsets(topicPartitionOffsets);

    Map<TopicPartition, OffsetAndMetadata> consumerGroupOffsets =
        adminClient.listConsumerGroupOffsets(consumerGroupId);

    Map<TopicPartition, Long> lagPerPartition = new HashMap<>();
    // Iterate over the topic's offsets rather than the consumer group's to ensure we get info even
    // if the consumer groups don't exist or don't have anything committed yet.
    for (Map.Entry<TopicPartition, ListOffsetsResult.ListOffsetsResultInfo> e :
        topicOffsets.entrySet()) {
      long topicOffset = e.getValue().offset();

      if (!consumerGroupOffsets.containsKey(e.getKey())) {
        lagPerPartition.put(e.getKey(), topicOffset);
      } else {
        OffsetAndMetadata consumer = consumerGroupOffsets.get(e.getKey());
        long consumerOffset = consumer.offset();
        lagPerPartition.put(e.getKey(), max(topicOffset - consumerOffset, 0));
      }
    }

    return Optional.of(lagPerPartition);
  }
}
