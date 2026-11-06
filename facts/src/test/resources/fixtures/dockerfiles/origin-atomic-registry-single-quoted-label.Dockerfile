FROM openshift/origin

LABEL name="projectatomic/atomic-registry-install" \
      vendor="Project Atomic" \
      INSTALL='docker run -i --rm \
                --privileged \
                -v /:/host \
                -e REGISTRYPORT \
                --entrypoint /usr/bin/install.sh \
                $IMAGE' \
      UNINSTALL='docker run -i --rm \
                --privileged \
                -v /:/host \
                --entrypoint /usr/bin/uninstall.sh \
                $IMAGE'

ADD install.sh uninstall.sh /usr/bin/
