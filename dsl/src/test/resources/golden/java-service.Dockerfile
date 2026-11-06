FROM registry.example.com/ubi9/ubi-minimal:9.4@sha256:1111111111111111111111111111111111111111111111111111111111111111
ARG REVISION
ARG SOURCE
LABEL org.opencontainers.image.revision="${REVISION}"
LABEL org.opencontainers.image.source="${SOURCE}"
RUN microdnf install -y --nodocs --setopt=install_weak_deps=0 java-21-openjdk-headless shadow-utils \
    && microdnf clean all
WORKDIR /opt/app
COPY --chown=1001:0 target/app.jar /opt/app/app.jar
EXPOSE 8080
RUN chgrp -R 0 /opt/app \
    && chmod -R g=u /opt/app
USER 1001
ENTRYPOINT ["java", "-jar", "/opt/app/app.jar"]
