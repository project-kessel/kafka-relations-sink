# relations-connector
This is a Kafka Connect designed to be an intermediary between RBAC and ReBAC

## How to build
To build the uberJAR run:
`mvn package`
After it completes you will see the new "target" directory with the uberJAR inside.

## How to deploy with RBAC locally
`docker-compose --env-file ./test/.relations_env -f docker-compose.yaml up` inside the repo.

In order to configure and turn on the connector, run `curl -d @"relations_sink.json" -H "Content-Type: application/json" -X POST http://localhost:8084/connectors`

If it completes successfully you should get this response from the curl:
```
{"name":"org.project_kessel.kafka.relations.sink.RelationsSinkConnector","config":{"connector.class":"org.project_kessel.kafka.relations.sink.RelationsSinkConnector","tasks.max":"1","topics":"outbox.event.RelationReplicationEvent","relations-api.target-url":"relations-api:9000","relations-api.is-secure-clients":"false","relations-api.authn.mode":"disabled","relations-api.authn.client.issuer":"${file:/secrets/oidc-client-credentials:relations-api.authn.client.issuer}","relations-api.authn.client.id":"${file:/secrets/oidc-client-credentials:relations-api.authn.client.id}","relations-api.authn.client.secret":"${file:/secrets/oidc-client-credentials:relations-api.authn.client.secret}","name":"org.project_kessel.kafka.relations.sink.RelationsSinkConnector"},"tasks":[],"type":"sink"}
```

##  Test

Load spicedb schema (needed only once after container is created):
```
zed schema write test/spicedb_schema.zed
```
Send test message on outbox kafka topic:
```
docker container exec -it rbac_kafka /bin/bash
[strimzi@1e894fdb041b kafka]$ echo '{"schema":{"type":"string","optional":true,"name":"io.debezium.data.Json","version":1},"payload":"{\"relations_to_add\": [{\"subject\": {\"subject\": {\"id\": \"my_workspace_2\", \"type\": {\"name\": \"workspace\", \"namespace\": \"rbac\"}}}, \"relation\": \"workspace\", \"resource\": {\"id\": \"my_thing\", \"type\": {\"name\": \"thing\", \"namespace\": \"rbac\"}}}], \"relations_to_delete\": []}"}' | bin/kafka-console-producer.sh --bootstrap-server kafka:9092 --topic outbox.event.RelationReplicationEvent
```
Check spicedb to see if the message resulted in a new relation being created:
```
zed relationship read rbac/thing
```
expected output:
```
rbac/thing:my_thing t_workspace rbac/workspace:my_workspace_2
```