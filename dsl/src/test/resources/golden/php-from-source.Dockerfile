FROM debian:bookworm-slim@sha256:8888888888888888888888888888888888888888888888888888888888888888
ARG REVISION
ARG SOURCE
LABEL org.opencontainers.image.revision="${REVISION}"
LABEL org.opencontainers.image.source="${SOURCE}"
RUN { echo 'Package: php*'; echo 'Pin: release *'; echo 'Pin-Priority: -1'; } > /etc/apt/preferences.d/no-debian-php
ENV PHPIZE_DEPS="autoconf dpkg-dev file g++ gcc libc-dev make pkg-config re2c"
RUN apt-get update \
    && apt-get install -y --no-install-recommends $PHPIZE_DEPS ca-certificates curl xz-utils \
    && rm -rf /var/lib/apt/lists/*
ENV PHP_INI_DIR="/usr/local/etc/php"
RUN mkdir -p "$PHP_INI_DIR/conf.d" \
    && mkdir -p /var/www/html
ENV PHP_CFLAGS="-fstack-protector-strong -fpic -fpie -O2 -D_LARGEFILE_SOURCE -D_FILE_OFFSET_BITS=64" \
    PHP_CPPFLAGS="$PHP_CFLAGS" \
    PHP_LDFLAGS="-Wl,-O1 -pie" \
    GPG_KEYS="AFD8691FDAEDF03BDF6E460563F15A9B715376CA 9D7F99A0CB8F05C8A6958D6256A97AF7600A39A6 0616E93D95AF471243E26761770426E17EBBB3DD" \
    PHP_VERSION="8.4.27" \
    PHP_URL="https://www.php.net/distributions/php-8.4.27.tar.xz" \
    PHP_ASC_URL="https://www.php.net/distributions/php-8.4.27.tar.xz.asc" \
    PHP_SHA256="dcf93923b0e38e0d5252b25b796520b5a47761a08b3488b6d0b5d1115764d3f0"
RUN savedAptMark="$(apt-mark showmanual)" \
    && apt-get update \
    && apt-get install -y --no-install-recommends gnupg \
    && rm -rf /var/lib/apt/lists/* \
    && mkdir -p /usr/src \
    && cd /usr/src \
    && curl -fsSL -o php.tar.xz "$PHP_URL" \
    && echo "$PHP_SHA256 *php.tar.xz" | sha256sum -c - \
    && curl -fsSL -o php.tar.xz.asc "$PHP_ASC_URL" \
    && export GNUPGHOME="$(mktemp -d)" \
    && for key in $GPG_KEYS; do gpg --batch --keyserver keyserver.ubuntu.com --recv-keys "$key"; done \
    && gpg --batch --verify php.tar.xz.asc php.tar.xz \
    && gpgconf --kill all \
    && rm -rf "$GNUPGHOME" \
    && apt-mark auto '.*' > /dev/null \
    && apt-mark manual $savedAptMark > /dev/null \
    && apt-get purge -y --auto-remove -o APT::AutoRemove::RecommendsImportant=false
COPY --chown=0:0 docker-php-source /usr/local/bin/
RUN savedAptMark="$(apt-mark showmanual)" \
    && apt-get update \
    && apt-get install -y --no-install-recommends libargon2-dev libcurl4-openssl-dev libonig-dev libreadline-dev libsodium-dev libsqlite3-dev libssl-dev libxml2-dev zlib1g-dev \
    && export CFLAGS="$PHP_CFLAGS" CPPFLAGS="$PHP_CPPFLAGS" LDFLAGS="$PHP_LDFLAGS" PHP_BUILD_PROVIDER='https://github.com/docker-library/php' PHP_UNAME='Linux - Docker' \
    && docker-php-source extract \
    && cd /usr/src/php \
    && gnuArch="$(dpkg-architecture --query DEB_BUILD_GNU_TYPE)" \
    && debMultiarch="$(dpkg-architecture --query DEB_BUILD_MULTIARCH)" \
    && if [ ! -d /usr/include/curl ]; then ln -sT "/usr/include/$debMultiarch/curl" /usr/local/include/curl; fi \
    && ./configure --build="$gnuArch" --sysconfdir="${PHP_INI_DIR%/php}" --with-config-file-path="$PHP_INI_DIR" --with-config-file-scan-dir="$PHP_INI_DIR/conf.d" --enable-option-checking=fatal --with-mhash --with-pic --enable-mbstring --enable-mysqlnd --with-password-argon2 --with-sodium=shared --with-pdo-sqlite=/usr --with-sqlite3=/usr --with-curl --with-iconv --with-openssl --with-readline --with-zlib --enable-phpdbg --enable-phpdbg-readline --with-pear --with-libdir="lib/$debMultiarch" --enable-embed --enable-zts --disable-zend-signals \
    && make -j "$(nproc)" \
    && find -type f -name '*.a' -delete \
    && make install \
    && find /usr/local -type f -perm '/0111' -exec sh -euxc 'strip --strip-all "$@" || :' -- '{}' + \
    && make clean \
    && cp -v php.ini-* "$PHP_INI_DIR/" \
    && cd / \
    && docker-php-source delete \
    && apt-mark auto '.*' > /dev/null \
    && { [ -z "$savedAptMark" ] || apt-mark manual $savedAptMark; } \
    && find /usr/local -type f -executable -exec ldd '{}' ';' | awk '/=>/ { so = $(NF-1); if (index(so, "/usr/local/") == 1) { next }; gsub("^/(usr/)?", "", so); printf "*%s\n", so }' | sort -u | xargs -rt dpkg-query --search | awk 'sub(":$", "", $1) { print $1 }' | sort -u | xargs -r apt-mark manual \
    && apt-get purge -y --auto-remove -o APT::AutoRemove::RecommendsImportant=false \
    && rm -rf /var/lib/apt/lists/* \
    && pecl update-channels \
    && rm -rf /tmp/pear ~/.pearrc \
    && php --version
COPY --chown=0:0 docker-php-ext-* docker-php-entrypoint /usr/local/bin/
RUN docker-php-ext-enable opcache \
    && docker-php-ext-enable sodium \
    && chgrp -R 0 /var/www/html \
    && chmod -R g=u /var/www/html
USER 1001
ENTRYPOINT ["docker-php-entrypoint"]
CMD ["php", "-a"]
