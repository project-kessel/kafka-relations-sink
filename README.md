# relations-connector
This is a Kafka Connect designed to be an intermediary between RBAC and ReBAC

## How to build
To build the uberJAR run:
`mvn package`
After it completes you will see the new "target" directory with the uberJAR inside.

## How to deploy with RBAC locally
`docker-compose --env-file ./test/.relations_env -f docker-compose.yaml up` inside the repo.

In order to configure and turn on the connector, run `curl -d @"sink.json" -H "Content-Type: application/json" -X POST http://localhost:8083/connectors`

If it completes successfully you should get this response from the curl:
```
{"name":"org.project_kessel.kafka.relations.sink.RelationsSinkConnector","config":{"connector.class":"org.project_kessel.kafka.relations.sink.RelationsSinkConnector","tasks.max":"1","relations-api.target-url":"relations-api:9000","topics":"debezium-test.public.outbox","name":"org.project_kessel.kafka.relations.sink.RelationsSinkConnector"},"tasks":[],"type":"sink"}
```
