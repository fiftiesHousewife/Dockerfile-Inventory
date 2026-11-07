FROM registry.access.redhat.com/ubi10/go-toolset@sha256:efa721f5d675f0494425ea582823d4324f546c0e19f13ca996942a1c354c8497

# renovate: datasource=github-releases depName=hashicorp/terraform
ARG TERRAFORM_VERSION=1.14.9

USER 0

# System packages (bash, curl, unzip are already in the base image)
RUN dnf install -y jq && \
    dnf clean all && \
    rm -rf /var/cache/dnf

# Terraform (download + verify checksum from HashiCorp)
RUN ARCH="$(uname -m | sed 's/x86_64/amd64/' | sed 's/aarch64/arm64/')" && \
    ZIP="terraform_${TERRAFORM_VERSION}_linux_${ARCH}.zip" && \
    curl -fsSL "https://releases.hashicorp.com/terraform/${TERRAFORM_VERSION}/${ZIP}" -o /tmp/${ZIP} && \
    curl -fsSL "https://releases.hashicorp.com/terraform/${TERRAFORM_VERSION}/terraform_${TERRAFORM_VERSION}_SHA256SUMS" -o /tmp/terraform_SHA256SUMS && \
    grep -F "${ZIP}" /tmp/terraform_SHA256SUMS > /tmp/terraform_checksum && \
    (cd /tmp && sha256sum -c terraform_checksum) && \
    unzip /tmp/${ZIP} -d /usr/local/bin/ && \
    rm /tmp/${ZIP} /tmp/terraform_SHA256SUMS /tmp/terraform_checksum

# AWS CLI v2
RUN curl -fsSL "https://awscli.amazonaws.com/awscli-exe-linux-$(uname -m).zip" -o /tmp/awscli.zip && \
    unzip /tmp/awscli.zip -d /tmp/ && \
    /tmp/aws/install && \
    rm -rf /tmp/awscli.zip /tmp/aws

# IBM Cloud CLI + vpc-infrastructure plugin
RUN curl -fsSL https://clis.cloud.ibm.com/install/linux | sh && \
    ibmcloud plugin install vpc-infrastructure -f && \
    ibmcloud plugin install cloud-object-storage -f && \
    rm -rf /root/.bluemix/tmp && \
    chown -R 1001:0 /opt/app-root/src/.bluemix

ENTRYPOINT []
CMD ["/bin/bash"]
USER 1001
