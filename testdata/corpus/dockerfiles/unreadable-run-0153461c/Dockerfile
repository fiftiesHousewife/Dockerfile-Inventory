FROM registry.ci.openshift.org/ocp/builder:rhel-9-golang-1.25-openshift-4.22

ENV GO111MODULE=on
ENV GOFLAGS=""

RUN curl -L "https://dl.k8s.io/release/$(curl -L -s https://dl.k8s.io/release/stable.txt)/bin/linux/amd64/kubectl" -o /tmp/kubectl
RUN install -o root -g root -m 0755 /tmp/kubectl /usr/local/bin/kubectl
RUN curl -sfL https://raw.githubusercontent.com/golangci/golangci-lint/master/install.sh| sh -s -- -b $(go env GOPATH)/bin $GOLANGCI_LINT_VERSION
RUN export OPERATOR_SDK_DL_URL=https://github.com/operator-framework/operator-sdk/releases/download/v1.32.0 \
    && curl --retry 5 -LO ${OPERATOR_SDK_DL_URL}/operator-sdk_linux_amd64 \
    && chmod +x operator-sdk_linux_amd64 \
    && install operator-sdk_linux_amd64 /usr/local/bin/operator-sdk
