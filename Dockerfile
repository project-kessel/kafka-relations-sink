FROM registry.redhat.io/amq-streams/maven-builder-rhel9:2.8.0-3 AS builder

ARG RELATIONS_SINK_VERSION=""

RUN curl -f -L --create-dirs --output /tmp/kafka-relations-sink/pom.xml https://repo1.maven.org/maven2/org/project-kessel/kafka-relations-sink/${RELATIONS_SINK_VERSION}/kafka-relations-sink-${RELATIONS_SINK_VERSION}.pom && \
    echo '<settings xmlns="http://maven.apache.org/SETTINGS/1.0.0"><profiles><profile><id>download</id><repositories><repository><id>custom-repo</id><url>https://repo1.maven.org/maven2/</url></repository></repositories></profile></profiles><activeProfiles><activeProfile>download</activeProfile></activeProfiles></settings>' > /tmp/settings.xml && \
    mvn dependency:copy-dependencies -s /tmp/settings.xml -DoutputDirectory=/tmp/artifacts/kafka-relations-sink -f /tmp/kafka-relations-sink/pom.xml && \
    curl -f -L --create-dirs --output /tmp/artifacts/kafka-relations-sink/kafka-relations-sink-${RELATIONS_SINK_VERSION}.jar https://repo1.maven.org/maven2/org/project-kessel/kafka-relations-sink/${RELATIONS_SINK_VERSION}/kafka-relations-sink-${RELATIONS_SINK_VERSION}.jar

FROM registry.redhat.io/amq-streams/kafka-37-rhel9:2.8.0-4

USER root:root

COPY --from=builder /tmp/artifacts/kafka-relations-sink /opt/kafka/plugins/kafka-relations-sink

USER 1001
