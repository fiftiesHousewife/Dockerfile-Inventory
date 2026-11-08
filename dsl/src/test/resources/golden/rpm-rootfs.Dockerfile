FROM registry.example.com/ubi9/ubi:9.4@sha256:2222222222222222222222222222222222222222222222222222222222222222 AS minimal-ubi
RUN dnf update -y --allowerasing \
    && mkdir /image \
    && ln -s usr/bin /image/bin \
    && ln -s usr/sbin /image/sbin \
    && ln -s usr/lib64 /image/lib64 \
    && ln -s usr/lib /image/lib \
    && mkdir -p /image/{usr/bin,usr/lib64,usr/lib,root,home,proc,etc,sys,var,dev} \
    && rpm --root /image --initdb \
    && dnf install -y --installroot=/image --releasever=9 --setopt=install_weak_deps=false --nodocs glibc tzdata ca-certificates \
    && dnf clean all --installroot=/image \
    && rm -rf /image/var/cache/dnf /image/var/log/*

FROM scratch
ARG REVISION
ARG SOURCE
LABEL org.opencontainers.image.revision="${REVISION}"
LABEL org.opencontainers.image.source="${SOURCE}"
LABEL description="External Secrets Operator is a Kubernetes operator that integrates external secret management systems"
COPY --from=minimal-ubi --chown=0:0 /image/ /
ARG TARGETOS
ARG TARGETARCH
COPY --chown=0:0 bin/external-secrets-${TARGETOS}-${TARGETARCH} /bin/external-secrets
USER 65534
ENTRYPOINT ["/bin/external-secrets"]
