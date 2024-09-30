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

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import io.smallrye.config.SmallRyeConfig;
import io.smallrye.config.SmallRyeConfigBuilder;
import io.smallrye.config.common.MapBackedConfigSource;
import org.apache.kafka.common.TopicPartition;
import org.apache.kafka.common.config.ConfigDef;
import org.apache.kafka.common.utils.AppInfoParser;
import org.apache.kafka.connect.connector.Task;
import org.apache.kafka.connect.sink.SinkConnector;
import org.project_kessel.relations.client.RelationsConfig;
import org.project_kessel.relations.client.RelationsGrpcClientsManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Very simple sink connector that works with stdout or a file.
 */
public class RelationsSinkConnector extends SinkConnector {
    private static final String KAFKA_PROPERTIES_NAME = "KafkaProperties";

    private static final Logger log = LoggerFactory.getLogger(RelationsSinkTask.class);

    // TODO: ConfigDef not build out -- using microprofile instead, but config() returns it...
    static final ConfigDef CONFIG_DEF = new ConfigDef();

    private Map<String, String> props;
    private RelationsGrpcClientsManager relationsClientsManager;

    /**
     * This method is idempotent and can be called at any time.
     * @param props
     * @return
     */
    static RelationsGrpcClientsManager startOrRetrieveManagerFromProps(Map<String, String> props) {
        SmallRyeConfig config = new SmallRyeConfigBuilder()
                .withSources(new MapBackedConfigSource(KAFKA_PROPERTIES_NAME, props){})
                .withMapping(RelationsConfig.class)
                .build();
        RelationsConfig relationsConfig = config.getConfigMapping(RelationsConfig.class);

        return RelationsGrpcClientsManager.forClientsWithConfig(relationsConfig);
    }

    @Override
    public String version() {
        return AppInfoParser.getVersion();
    }

    @Override
    public void start(Map<String, String> props) {
        log.debug("Starting RelationsSinkConnector");
        this.props = props;
        relationsClientsManager = startOrRetrieveManagerFromProps(props);
        log.trace("Done starting RelationsSinkConnector");
    }

    @Override
    public Class<? extends Task> taskClass() {
        return RelationsSinkTask.class;
    }

    @Override
    public List<Map<String, String>> taskConfigs(int maxTasks) {
        ArrayList<Map<String, String>> configs = new ArrayList<>();
        for (int i = 0; i < maxTasks; i++) {
            configs.add(props);
        }
        return configs;
    }

    @Override
    public void stop() {
        log.debug("Stopping RelationsSinkConnector");
        RelationsGrpcClientsManager.shutdownManager(relationsClientsManager);
    }

    @Override
    public ConfigDef config() {
        return CONFIG_DEF;
    }

    @Override
    public boolean alterOffsets(Map<String, String> connectorConfig, Map<TopicPartition, Long> offsets) {
        // Nothing to do here since FileStreamSinkConnector does not manage offsets externally nor does it require any
        // custom offset validation
        return true;
    }
}