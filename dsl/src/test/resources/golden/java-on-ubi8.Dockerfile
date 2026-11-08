FROM registry.access.redhat.com/ubi8/ubi-minimal:8.1@sha256:9999999999999999999999999999999999999999999999999999999999999999
ARG REVISION
ARG SOURCE
LABEL org.opencontainers.image.revision="${REVISION}"
LABEL org.opencontainers.image.source="${SOURCE}"
ARG JAVA_PACKAGE="java-11-openjdk-headless"
ARG RUN_JAVA_VERSION="1.3.8"
ENV LANG="en_US.UTF-8" \
    LANGUAGE="en_US:en"
RUN microdnf install -y --nodocs --setopt=install_weak_deps=0 curl ca-certificates ${JAVA_PACKAGE} \
    && microdnf clean all \
    && mkdir /deployments \
    && curl https://repo1.maven.org/maven2/io/fabric8/run-java-sh/${RUN_JAVA_VERSION}/run-java-sh-${RUN_JAVA_VERSION}-sh.sh -o /deployments/run-java.sh \
    && chmod 550 /deployments/run-java.sh \
    && echo "securerandom.source=file:/dev/urandom" >> /etc/alternatives/jre/lib/security/java.security
ENV JAVA_OPTIONS="-Dquarkus.http.host=0.0.0.0 -Djava.util.logging.manager=org.jboss.logmanager.LogManager"
COPY --chown=1001:0 target/lib/* /deployments/lib/
COPY --chown=1001:0 target/*-runner.jar /deployments/app.jar
EXPOSE 8080
RUN chgrp -R 0 /deployments \
    && chmod -R g=u /deployments
USER 1001
ENTRYPOINT ["/deployments/run-java.sh"]
