ARG GO_VERSION=1.22
FROM registry.access.redhat.com/ubi9/go-toolset:${GO_VERSION} AS build
WORKDIR /src
COPY . .
RUN go build -o /out/server ./cmd/server

FROM registry.access.redhat.com/ubi9/ubi-minimal:9.4@sha256:1111111111111111111111111111111111111111111111111111111111111111
LABEL name="payments/server" version="1.4.2"
ENV APP_HOME=/opt/app API_TOKEN=s3cr3t-value
RUN microdnf install -y tzdata-2024a && microdnf clean all
WORKDIR /opt/app
COPY --from=build /out/server /usr/local/bin/server
VOLUME /opt/app/data
EXPOSE 8080 9090/udp
HEALTHCHECK --interval=30s CMD ["/usr/local/bin/server", "health"]
USER 1001
ENTRYPOINT ["/usr/local/bin/server"]
CMD ["--port", "8080"]
