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

## RBAC end to end testing 
This setup should allow you to test creating a replication event from an insert in the outbox table and have it create 
a relationship in the relations-api. Flow:

Outbox table -> debezium connector -> kafka topic -> relations sink connector -> relations-api -> spicedb

Offer up a libation to the old gods and the new, because this will hardly work first time!

1. Go to the insights-rbac repo and checkout the debezium source connector branch. We're going to piggyback on this.
   ```
   git remote add wcmitchell git@github.com:wcmitchell/insights-rbac.git
   git fetch wcmitchell
   git checkout local_debezium_testing
   ```
2. In another terminal, clone the relations sink connector and checkout branch:
   ```
   git clone git@github.com:merlante/relations-connector.git
   git checkout RHCLOUD-34856-Implement-sink-to-relations
   ```
3. Build the sink connector:
   ```
   mvn clean package
   ```
4. Copy and paste the following folders/files from the relations sink connector (in 2.) into the root of the debezium connector
   (in 1.)
   ```
   target/
   test/
   relations_connect.properties
   relations_sink.json
   ```
5. Copy all lines in `docker-compose.yaml` in 2. from the line with 
   ```
   relations_sink_connect:
   ```
6. Paste those lines into `docker-compose-kafka.yml` in 1. after the following block:
   ```
     wait_for_app:
       container_name: wait_for_app
       image: hello-world:latest
       depends_on:
         rbac-server:
           condition: service_healthy
   ```
   and above the `networks` block.

   Make sure the indendation are right.
7. There are now 2 volume blocks that need to be merged into 1 in that file for it to be valid. e.g.
   ```
   volumes:
      pg_data:
      kafka-connect-logs:
   ```
8. Go to the terminal in 1. and run docker/podman compose:
   ```
   podman compose -f docker-compose-kafka.yml --env-file test/.relations_env up
   ```
9. Load spicedb schema (needed only once after container is created):
   ```
   zed schema write test/spicedb_schema.zed
   ```
   (`zed context` should be set to ENDPOINT localhost:50051, TOKEN set as foobar and with --insecure)
10. Enable the debezium source connector in a terminal in 1.:
   ```
   python /scripts/debezium/setup_local.py
   ```
   (Ignore complaining about a table already existing.)
11. Enable the sink connector in a terminal in 1.:
   ```
   curl -d @"relations_sink.json" -H "Content-Type: application/json" -X POST http://localhost:8084/connectors
   ```
12. Create an entry in the outbox table with the following:
   ```
   docker exec -it rbac_db /bin/bash
   root@0ae61698aa93:/# psql -U postgres
   postgres=# \c postgres
   postgres=# insert into management_outbox values('a0eebc99-9c0b-4ef8-bb6d-6bb9bd380a11', 'RelationReplicationEvent', 'agg_id', 'event_type', '{"relations_to_delete":[],"relations_to_add":[{"resource":{"type":{"namespace":"rbac","name":"thing"},"id":"my_thing"},"relation":"workspace","subject":{"subject":{"type":{"namespace":"rbac","name":"workspace"},"id":"my_workspace_3"}}}]}'::jsonb);
   ```
13. Check to see if the relationship was created in spicedb:
   ```
   zed relationship read rbac/thing
   ```
   should return
   ```
   rbac/thing:my_thing t_workspace rbac/workspace:my_workspace_2
   ```
14. The docker compose logs will show some spicedb and relation-api lines as well showing that writes happened if all is
   working.
