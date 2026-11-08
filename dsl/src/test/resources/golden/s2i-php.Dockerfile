FROM quay.io/sclorg/s2i-base-c9s:c9s@sha256:aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa
ARG REVISION
ARG SOURCE
LABEL org.opencontainers.image.revision="${REVISION}"
LABEL org.opencontainers.image.source="${SOURCE}"
EXPOSE 8080 8443
ENV PHP_VERSION="8.0" \
    PHP_VER_SHORT="80" \
    NAME="php"
ENV SUMMARY="Platform for building and running PHP $PHP_VERSION applications" \
    DESCRIPTION="PHP $PHP_VERSION available as container is a base platform for building and running various PHP $PHP_VERSION applications and frameworks."
LABEL summary="${SUMMARY}" \
      description="${DESCRIPTION}" \
      io.k8s.description="${DESCRIPTION}" \
      io.k8s.display-name="Apache 2.4 with PHP ${PHP_VERSION}" \
      io.openshift.expose-services="8080:http" \
      io.openshift.tags="builder,${NAME},${NAME}${PHP_VER_SHORT},${NAME}-${PHP_VER_SHORT}" \
      io.openshift.s2i.scripts-url="image:///usr/libexec/s2i" \
      io.s2i.scripts-url="image:///usr/libexec/s2i" \
      name="sclorg/${NAME}-${PHP_VER_SHORT}-c9s" \
      version="1"
ARG INSTALL_PKGS="php php-fpm php-mysqlnd php-pgsql php-bcmath php-gd php-intl php-ldap php-mbstring php-pdo php-process php-soap php-opcache php-xml php-gmp php-pecl-apcu php-pecl-zip mod_ssl hostname"
RUN yum install -y --setopt=tsflags=nodocs $INSTALL_PKGS \
    && yum reinstall -y tzdata \
    && rpm -V $INSTALL_PKGS \
    && php -v | grep -qe "v$PHP_VERSION\." \
    && echo "Found VERSION $PHP_VERSION" \
    && yum -y clean all --enablerepo='*'
ENV PHP_CONTAINER_SCRIPTS_PATH="/usr/share/container-scripts/php/" \
    APP_DATA="${APP_ROOT}/src" \
    PHP_DEFAULT_INCLUDE_PATH="/usr/share/pear" \
    PHP_SYSCONF_PATH="/etc" \
    PHP_HTTPD_CONF_FILE="php.conf" \
    PHP_FPM_CONF_D_PATH="/etc/php-fpm.d" \
    PHP_FPM_CONF_FILE="www.conf" \
    PHP_FPM_RUN_DIR="/run/php-fpm" \
    PHP_MAIN_FPM_CONF_FILE="/etc/php-fpm.conf" \
    PHP_FPM_LOG_PATH="/var/log/php-fpm" \
    HTTPD_CONFIGURATION_PATH="${APP_ROOT}/etc/conf.d" \
    HTTPD_MAIN_CONF_PATH="/etc/httpd/conf" \
    HTTPD_MAIN_CONF_D_PATH="/etc/httpd/conf.d" \
    HTTPD_MODULES_CONF_D_PATH="/etc/httpd/conf.modules.d" \
    HTTPD_VAR_RUN="/var/run/httpd" \
    HTTPD_DATA_PATH="/var/www" \
    HTTPD_DATA_ORIG_PATH="/var/www" \
    HTTPD_VAR_PATH="/var"
COPY --chown=0:0 ./s2i/bin/ $STI_SCRIPTS_PATH
COPY --chown=0:0 ./root/ /
RUN /usr/libexec/container-setup \
    && rpm-file-permissions
USER 1001
CMD ["/usr/libexec/s2i/usage"]
