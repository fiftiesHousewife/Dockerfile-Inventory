FROM registry.access.redhat.com/ubi9/ubi-minimal:9.4@sha256:1111111111111111111111111111111111111111111111111111111111111111
ARG REVISION
ARG SOURCE
LABEL org.opencontainers.image.revision="${REVISION}"
LABEL org.opencontainers.image.source="${SOURCE}"
COPY --chown=0:0 private-org-peribolos-sync /usr/bin/private-org-peribolos-sync
USER 1001
ENTRYPOINT ["/usr/bin/private-org-peribolos-sync"]
