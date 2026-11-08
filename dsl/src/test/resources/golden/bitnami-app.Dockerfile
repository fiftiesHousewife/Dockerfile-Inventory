FROM docker.io/bitnami/minideb:bookworm@sha256:4444444444444444444444444444444444444444444444444444444444444444
ARG REVISION
ARG SOURCE
LABEL org.opencontainers.image.revision="${REVISION}"
LABEL org.opencontainers.image.source="${SOURCE}"
ARG DOWNLOADS_URL="https://downloads.bitnami.com/files/stacksmith"
ARG TARGETARCH
LABEL org.opencontainers.image.title="notation"
LABEL org.opencontainers.image.version="1.3.2"
ENV HOME="/" \
    OS_ARCH="${TARGETARCH:-amd64}" \
    OS_FLAVOUR="debian-12" \
    OS_NAME="linux"
COPY --chown=0:0 prebuildfs /
SHELL ["/bin/bash", "-o", "errexit", "-o", "nounset", "-o", "pipefail", "-c"]
RUN apt-get update \
    && apt-get install -y --no-install-recommends ca-certificates curl procps \
    && rm -rf /var/lib/apt/lists/* \
    && mkdir -p /tmp/bitnami/pkg/cache /.config \
    && curl -SsLf "${DOWNLOADS_URL}/notation-1.3.2-27-linux-${OS_ARCH}-debian-12.tar.gz" -o /tmp/bitnami/pkg/cache/notation-1.3.2-27-linux-${OS_ARCH}-debian-12.tar.gz \
    && (cd /tmp/bitnami/pkg/cache && sha256sum -c /opt/bitnami/checksums/notation-1.3.2-27-linux-${OS_ARCH}-debian-12.tar.gz.sha256) \
    && tar -zxf /tmp/bitnami/pkg/cache/notation-1.3.2-27-linux-${OS_ARCH}-debian-12.tar.gz -C /opt/bitnami --strip-components=2 --no-same-owner \
    && rm -rf /tmp/bitnami /opt/bitnami/checksums \
    && { find / -perm /6000 -type f -exec chmod a-s {} \; || true; } \
    && apt-get purge -y --auto-remove curl \
    && rm -rf /var/lib/apt/lists/*
ENV APP_VERSION="1.3.2" \
    BITNAMI_APP_NAME="notation" \
    PATH="/opt/bitnami/notation/bin:$PATH"
RUN chgrp -R 0 /opt/bitnami /.config \
    && chmod -R g=u /opt/bitnami /.config
USER 1001
ENTRYPOINT ["notation"]
CMD ["--help"]
