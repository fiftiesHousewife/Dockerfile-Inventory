FROM registry.example.com/ubi9/go-toolset:1.22@sha256:3333333333333333333333333333333333333333333333333333333333333333 AS build
WORKDIR /src
COPY . /src/
RUN go build -o /src/out/server ./cmd/server

FROM registry.example.com/ubi9/ubi-minimal:9.4@sha256:1111111111111111111111111111111111111111111111111111111111111111
ARG REVISION
ARG SOURCE
LABEL org.opencontainers.image.revision="${REVISION}"
LABEL org.opencontainers.image.source="${SOURCE}"
COPY --from=build --chown=1001:0 /src/out/server /usr/local/bin/server
USER 1001
ENTRYPOINT ["/usr/local/bin/server"]
