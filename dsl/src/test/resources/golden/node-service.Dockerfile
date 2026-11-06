FROM registry.example.com/ubi9/ubi-minimal:9.4@sha256:1111111111111111111111111111111111111111111111111111111111111111
ARG REVISION
ARG SOURCE
LABEL org.opencontainers.image.revision="${REVISION}"
LABEL org.opencontainers.image.source="${SOURCE}"
RUN microdnf install -y --nodocs --setopt=install_weak_deps=0 nodejs npm \
    && microdnf clean all \
    && npm config set fund false --location=global
WORKDIR /opt/app
COPY --chown=1001:0 package.json /opt/app/
RUN npm ci --omit=dev
COPY --chown=1001:0 dist/ /opt/app/dist/
ENV NODE_ENV="production"
EXPOSE 3000
RUN chgrp -R 0 /opt/app \
    && chmod -R g=u /opt/app
USER 1001
ENTRYPOINT ["node", "/opt/app/dist/server.js"]
