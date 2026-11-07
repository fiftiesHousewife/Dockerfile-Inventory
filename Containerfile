# The pipeline as one image for a pod on OpenShift: `inventory <stage>` runs discovery, facts, lineage, report, rules
# or converter, configured from the environment as the README says. Build with Buildah or Podman:
#   buildah build -t dockerfile-inventory -f Containerfile .
# Behind a firewall, pass the Maven mirror the build should use: --build-arg INVENTORY_MIRROR=https://...
# Pin the two base images by digest in the pipeline that builds this (rule R1); the tags here are for a first build.
ARG BUILDER_IMAGE=registry.access.redhat.com/ubi9/openjdk-25:latest
ARG RUNTIME_IMAGE=registry.access.redhat.com/ubi9/ubi-minimal:latest

FROM ${BUILDER_IMAGE} AS build
ARG INVENTORY_MIRROR=
USER 0
WORKDIR /src
COPY . .
RUN INVENTORY_MIRROR="${INVENTORY_MIRROR}" ./gradlew --no-daemon -q -x verifyUpToDate \
        :discovery:installDist :facts:installDist :lineage:installDist :report:installDist :rules:installDist \
        :converter:installDist \
    && mkdir -p /opt/inventory \
    && for stage in discovery facts lineage report rules converter; do \
           cp -r "$stage/build/install/$stage" /opt/inventory/; \
       done \
    && modules=$(jdeps --ignore-missing-deps --print-module-deps --multi-release 25 \
           --class-path '/opt/inventory/*/lib/*' /opt/inventory/*/lib/*.jar),jdk.crypto.ec,jdk.localedata \
    && jlink --add-modules "$modules" --strip-debug --no-man-pages --no-header-files --compress zip-6 \
           --output /opt/jre

FROM ${RUNTIME_IMAGE}
LABEL org.opencontainers.image.title="dockerfile-inventory" \
      org.opencontainers.image.description="Inventories the Dockerfiles of a GitHub Enterprise estate" \
      org.opencontainers.image.source="https://github.com/fiftiesHousewife/Dockerfile-Inventory" \
      org.opencontainers.image.licenses="Apache-2.0"
COPY --from=build /opt/jre /opt/jre
COPY --from=build /opt/inventory /opt/inventory
COPY scripts/inventory /usr/local/bin/inventory
# OpenShift runs the container as an arbitrary user in group 0: HOME and the work folder are group-writable.
RUN mkdir -p /home/inventory /work \
    && chgrp -R 0 /home/inventory /work /opt/inventory \
    && chmod -R g=u /home/inventory /work \
    && chmod 0755 /usr/local/bin/inventory
ENV JAVA_HOME=/opt/jre \
    PATH=/opt/jre/bin:/usr/local/bin:/usr/bin:/bin \
    HOME=/home/inventory \
    JAVA_OPTS="-XX:MaxRAMPercentage=75 -XX:+ExitOnOutOfMemoryError"
WORKDIR /work
USER 1001
ENTRYPOINT ["/usr/local/bin/inventory"]
CMD ["--help"]
