/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements. See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License. You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.project_kessel.kafka.relations.sink;

import java.nio.charset.StandardCharsets;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import com.google.common.collect.Streams;
import com.google.gson.*;
import com.google.protobuf.InvalidProtocolBufferException;
import com.google.protobuf.util.JsonFormat;
import io.grpc.StatusRuntimeException;
import org.apache.kafka.clients.consumer.OffsetAndMetadata;
import org.apache.kafka.common.TopicPartition;
import org.apache.kafka.connect.errors.RetriableException;
import org.apache.kafka.connect.sink.SinkRecord;
import org.apache.kafka.connect.sink.SinkTask;
import org.apache.kafka.connect.json.JsonConverter;
import org.project_kessel.api.relations.v1beta1.*;
import org.project_kessel.relations.client.RelationTuplesClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static org.project_kessel.kafka.relations.sink.RelationsSinkConnector.startOrRetrieveManagerFromProps;
import static org.apache.kafka.common.requests.DescribeConfigsResponse.ConfigSource.TOPIC_CONFIG;

/**
 * FileStreamSinkTask writes records to stdout or a file.
 */
public class RelationsSinkTask extends SinkTask {
    private static final Logger log = LoggerFactory.getLogger(RelationsSinkTask.class);

    private RelationTuplesClient relationTuplesClient;
    private String topic;
    private final JsonConverter jsonConverter = new JsonConverter();

    public RelationsSinkTask() {
    }

    @Override
    public String version() {
        return "v0.1";
    }

    @Override
    public void start(Map<String, String> props) {
        log.debug("Starting RelationsSinkTask");
        jsonConverter.configure(props, false);
        /* No new grpc channel will be created, only retrieved, since the connector makes the call first */
        var relationsClientsManager = startOrRetrieveManagerFromProps(props);
        relationTuplesClient = relationsClientsManager.getRelationTuplesClient();
        topic = props.get(TOPIC_CONFIG);
        log.trace("Done starting RelationsSinkTask");
    }

    @Override
    public void put(Collection<SinkRecord> sinkRecords) {
        log.trace("Putting sinkRecords");
        for (SinkRecord record : sinkRecords) {
            log.trace("Processing record {}", record.value());
            try {
                // TODO: Replace string and json wrangling below with proper "replication event" schema
                byte[] rawJson = jsonConverter.fromConnectData(
                        topic,
                        record.valueSchema(),
                        record.value());
                String json = new String(rawJson, StandardCharsets.UTF_8);
                JsonObject replicationEvent = JsonParser.parseString(json).getAsJsonObject();

                log.trace("Received replication event. Json: {}", replicationEvent);

                /* Schema currently specifies payload as a string (as opposed to a json object) */
                String payloadString = replicationEvent.get("payload").getAsString();
                JsonObject payload = JsonParser.parseString(payloadString).getAsJsonObject();

                /* Do tuple deletes */
                JsonElement relationsToDeleteElement = payload.get("relations_to_remove");
                if (relationsToDeleteElement != null && relationsToDeleteElement.isJsonArray()) {
                    JsonArray relationsToDelete = relationsToDeleteElement.getAsJsonArray();
                    log.trace("Relations to remove: {}", relationsToDelete);

                    jsonArrayToDeleteRequestStream(relationsToDelete).forEach(relationTuplesClient::deleteTuples);
                    log.trace("Relations removed");
                }

                /* Do tuple creates */
                JsonElement relationsToAddElement = payload.get("relations_to_add");
                if (relationsToAddElement != null && relationsToAddElement.isJsonArray()) {
                    JsonArray relationsToAdd = relationsToAddElement.getAsJsonArray();
                    log.trace("Relations to add: {}", relationsToAddElement.getAsJsonArray());

                    CreateTuplesRequest ctr = CreateTuplesRequest.newBuilder()
                            .addAllTuples(jsonArrayToRelationshipList(relationsToAdd))
                            .setUpsert(true)
                            .build();

                    relationTuplesClient.createTuples(ctr);
                    log.trace("Relations added");
                }
            } catch (StatusRuntimeException e) {
                /* Handle retryable exceptions here (using KafkaConnect's retry mechanism).
                 *
                 * No retriable checked exceptions.
                 * Known retriable unchecked exceptions:
                 * - io.grpc.StatusRuntimeException: grpc connectivity issue when connecting to relations-api
                 */
                throw new RetriableException("A grpc-related error occurred when trying to contact the relations-api", e);
                /*
                 * Note on consistency:
                 * Task will be completely re-run on failure, therefore all calls must be idempotent.
                 * e.g. creates must have touch/upsert semantic, deletes must not fail in no relation is deleted.
                 */
            }
        }
    }

    @Override
    public void flush(Map<TopicPartition, OffsetAndMetadata> offsets) {
        log.trace("Flushing channel for RelationsSinkTask -- currently a no op");
    }

    @Override
    public void stop() {
        log.debug("Stopping RelationsSinkTask -- currently a no op");
    }

    private static Stream<DeleteTuplesRequest> jsonArrayToDeleteRequestStream(JsonArray relations) {
        return Streams.stream(relations.iterator())
                .map(JsonElement::getAsJsonObject)
                .map(RelationsSinkTask::jsonToDeleteRequest);
    }

    private static List<Relationship> jsonArrayToRelationshipList(JsonArray relations) {
        return Streams.stream(relations.iterator())
                .map(JsonElement::getAsJsonObject)
                .map(RelationsSinkTask::jsonToRelationship)
                .collect(Collectors.toList());
    }

    private static DeleteTuplesRequest jsonToDeleteRequest(JsonObject jsonRelationship) {
        return DeleteTuplesRequest.newBuilder()
                .setFilter(jsonToRelationTupleFilter(jsonRelationship))
                .build();
    }

    private static RelationTupleFilter jsonToRelationTupleFilter(JsonObject jsonRelationship) {
        return relationshipToRelationTupleFilter(jsonToRelationship(jsonRelationship));
    }

    private static RelationTupleFilter relationshipToRelationTupleFilter(Relationship relationship) {
        return RelationTupleFilter.newBuilder()
                .setResourceNamespace(relationship.getResource().getType().getNamespace())
                .setResourceType(relationship.getResource().getType().getName())
                .setResourceId(relationship.getResource().getId())
                .setRelation(relationship.getRelation())
                .setSubjectFilter(SubjectFilter.newBuilder()
                        .setSubjectNamespace(relationship.getSubject().getSubject().getType().getNamespace())
                        .setSubjectType(relationship.getSubject().getSubject().getType().getName())
                        .setSubjectId(relationship.getSubject().getSubject().getId())
                        .build())
                .build();
    }

    private static Relationship jsonToRelationship(JsonObject jsonRelationship) {
        Relationship.Builder rtfBuilder = Relationship.newBuilder();
        try {
            JsonFormat.parser().merge(jsonRelationship.toString(), rtfBuilder);
            return rtfBuilder.build();
        } catch (InvalidProtocolBufferException e) {
            log.error("Can't parse jsonRelationship JsonObject into protobuf type {}", Relationship.class);
            // TODO: figure out exception handling with regard to task/connector lifecycles, dead letter queues, etc.
            throw new RuntimeException(e);
        }
    }
}
