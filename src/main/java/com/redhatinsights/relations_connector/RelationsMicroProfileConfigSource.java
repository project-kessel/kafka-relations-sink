package com.redhatinsights.relations_connector;

import org.eclipse.microprofile.config.spi.ConfigSource;

import java.util.Map;
import java.util.Set;

public class RelationsMicroProfileConfigSource implements ConfigSource {
    private Map<String, String> kafkaProps;

    public RelationsMicroProfileConfigSource(Map<String, String> kafkaProps) {
        this.kafkaProps = kafkaProps;
    }

    @Override
    public Map<String, String> getProperties() {
        return kafkaProps;
    }

    @Override
    public Set<String> getPropertyNames() {
        return kafkaProps.keySet();
    }

    @Override
    public int getOrdinal() {
        // will be ordered between the .env source, and the application.properties source due to the 275 ordinal
        return 275;
    }

    @Override
    public String getValue(String s) {
        return kafkaProps.get(s);
    }

    @Override
    public String getName() {
        return RelationsMicroProfileConfigSource.class.getSimpleName();
    }
}
