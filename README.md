# relations-connector
This is a Kafka Connect designed to be an intermediary between RBAC and ReBAC

## How to build
To build the uberJAR run:
`mvn package`
After it completes you will see the new "target" directory with the uberJAR inside.

## How to deploy with RBAC locally
First clone the insights-rbac repo.

Then you need to update the file `docker-compose-kafka.yml` to include the connect JAR and it's properties.

it should look something like this (the 2 new lines under `volumes`):
```
  kafka_connect:
    container_name: rbac_kafka_connect
    image: quay.io/cloudservices/kafka-connect:57decfc
    command: [
      "sh", "-c", "bin/connect-distributed.sh /opt/kafka/config/rebac_connect.properties"
    ]
    depends_on:
      - kafka
    ports:
      - "8083:8083"
    volumes:
      - ./scripts/debezium/rebac_connect.properties:/opt/kafka/config/rebac_connect.properties
      - <this_repo_path>/relations-connector/relations_connect.properties:/opt/kafka/config/relations_connect.properties
      - <this_repo_path>/relations-connector/target/relations-connector-1.0-SNAPSHOT.jar:/opt/kafka/plugins/relations-connector-1.0-SNAPSHOT.jar
```

The SNAPSHOT.jar is what you created in the "How to build" step.

Now you can get the environment stood up with `docker-compose -f docker-compose-kafka.yml up` inside the RBAC repo.

In order to configure and turn on the connector, go back to the `relations-connector` repo and run `curl -d @"sink.json" -H "Content-Type: application/json" -X POST http://localhost:8083/connectors`

If it completes successfully you should get this response from the curl:
```
{"name":"relations-sink","config":{"connector.class":"RelationsSink","tasks.max":"1","topics":"debezium-test.public.outbox","name":"relations-sink"},"tasks":[],"type":"sink"}
```
