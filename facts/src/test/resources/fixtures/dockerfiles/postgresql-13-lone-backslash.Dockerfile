FROM quay.io/sclorg/s2i-core-c9s:c9s
COPY root/usr/libexec/fix-permissions /usr/libexec/fix-permissions

# This image must forever use UID 26 for postgres user so our volumes are
# safe in the future. This should *never* change, the last test is there
# to make sure of that.
 \
RUN INSTALL_PKGS="rsync tar gettext nss_wrapper-libs postgresql-server postgresql-contrib" && \
    INSTALL_PKGS="$INSTALL_PKGS pgaudit" && \
    INSTALL_PKGS="$INSTALL_PKGS procps-ng util-linux postgresql-upgrade" && \
    yum -y --setopt=tsflags=nodocs install $INSTALL_PKGS && \
    rpm -V $INSTALL_PKGS && \
    postgres -V | grep -qe "$POSTGRESQL_VERSION\." && echo "Found VERSION $POSTGRESQL_VERSION" && \
    (yum -y reinstall tzdata || yum -y update tzdata ) && \
    yum -y clean all --enablerepo='*' && \
    localedef -f UTF-8 -i en_US en_US.UTF-8 && \
    test "$(id postgres)" = "uid=26(postgres) gid=26(postgres) groups=26(postgres)" && \
    mkdir -p /var/lib/pgsql/data && \
    mkdir -p /run/postgresql && \
    /usr/libexec/fix-permissions /var/lib/pgsql /run/postgresql
