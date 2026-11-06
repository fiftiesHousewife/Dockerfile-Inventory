FROM registry.example.com/ubi9/ubi:9.4@sha256:2222222222222222222222222222222222222222222222222222222222222222
ARG REVISION
ARG SOURCE
LABEL org.opencontainers.image.revision="${REVISION}"
LABEL org.opencontainers.image.source="${SOURCE}"
RUN dnf install -y --nodocs --setopt=install_weak_deps=0 python3.11 python3.11-pip \
    && dnf clean all
WORKDIR /opt/app
COPY --chown=1001:0 requirements.txt /opt/app/
RUN python3.11 -m pip install --no-cache-dir -r requirements.txt
COPY --chown=1001:0 src/ /opt/app/src/
ENV PYTHONUNBUFFERED="1"
EXPOSE 8000
RUN chgrp -R 0 /opt/app \
    && chmod -R g=u /opt/app
USER 1001
ENTRYPOINT ["python3.11", "-m", "app"]
CMD ["--port", "8000"]
