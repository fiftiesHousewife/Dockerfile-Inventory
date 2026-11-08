FROM registry.ci.openshift.org/ocp/builder:rhel-9-golang-1.26-openshift-5.0@sha256:6666666666666666666666666666666666666666666666666666666666666666 AS builder
WORKDIR /go/src/github.com/k8snetworkplumbingwg/ptp-operator
COPY . .
ENV GO111MODULE="auto"
RUN make

FROM registry.ci.openshift.org/ocp/5.0:base-rhel9@sha256:7777777777777777777777777777777777777777777777777777777777777777
ARG REVISION
ARG SOURCE
LABEL org.opencontainers.image.revision="${REVISION}"
LABEL org.opencontainers.image.source="${SOURCE}"
COPY --from=builder --chown=0:0 /go/src/github.com/k8snetworkplumbingwg/ptp-operator/build/_output/bin/ptp-operator /usr/local/bin/
COPY --from=builder --chown=0:0 /go/src/github.com/k8snetworkplumbingwg/ptp-operator/manifests /manifests
COPY --chown=0:0 bindata /bindata
LABEL io.k8s.display-name="OpenShift ptp-operator" \
      io.k8s.description="This is a component that manages cluster PTP configuration." \
      io.openshift.tags="openshift,ptp"
USER 1001
ENTRYPOINT ["/usr/local/bin/ptp-operator"]
