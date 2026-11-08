FROM registry.redhat.io/openshift4/ose-operator-registry-rhel9:v4.22@sha256:5555555555555555555555555555555555555555555555555555555555555555
ARG REVISION
ARG SOURCE
LABEL org.opencontainers.image.revision="${REVISION}"
LABEL org.opencontainers.image.source="${SOURCE}"
COPY --chown=1001:0 catalog/ /configs
RUN ["/bin/opm", "serve", "/configs", "--cache-dir=/tmp/cache", "--cache-only"]
LABEL operators.operatorframework.io.index.configs.v1="/configs"
RUN chgrp -R 0 /tmp/cache \
    && chmod -R g=u /tmp/cache
USER 1001
ENTRYPOINT ["/bin/opm"]
CMD ["serve", "/configs", "--cache-dir=/tmp/cache"]
