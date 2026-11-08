package io.github.fiftieshousewife.dockerfile.model;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;

final class Goldens {

    static final String REGISTRY = "registry.access.redhat.com/";
    static final BaseImage UBI_MINIMAL = new BaseImage(REGISTRY + "ubi9/ubi-minimal", "9.4",
            "sha256:" + "1".repeat(64));
    static final BaseImage UBI = new BaseImage(REGISTRY + "ubi9/ubi", "9.4", "sha256:" + "2".repeat(64));
    static final BaseImage MINIDEB = new BaseImage("docker.io/bitnami/minideb", "bookworm", "sha256:" + "4".repeat(64));
    static final BaseImage OPERATOR_REGISTRY = new BaseImage(
            "registry.redhat.io/openshift4/ose-operator-registry-rhel9", "v4.22", "sha256:" + "5".repeat(64));
    static final BaseImage OCP_BUILDER = new BaseImage("registry.ci.openshift.org/ocp/builder",
            "rhel-9-golang-1.26-openshift-5.0", "sha256:" + "6".repeat(64));
    static final BaseImage OCP_BASE = new BaseImage("registry.ci.openshift.org/ocp/5.0", "base-rhel9",
            "sha256:" + "7".repeat(64));
    static final BaseImage DEBIAN = new BaseImage("debian", "bookworm-slim", "sha256:" + "8".repeat(64));
    static final BaseImage SCRATCH = new BaseImage("scratch", null, null);
    static final BaseImage UBI8_MINIMAL = new BaseImage("registry.access.redhat.com/ubi8/ubi-minimal", "8.1",
            "sha256:" + "9".repeat(64));
    static final BaseImage S2I_BASE = new BaseImage("quay.io/sclorg/s2i-base-c9s", "c9s", "sha256:" + "a".repeat(64));
    static final BaseImage PYTHON_311 = new BaseImage("registry.access.redhat.com/ubi9/python-311", "1-77.1726664316",
            "sha256:" + "b".repeat(64));
    static final Policy POLICY = Policy.read(Path.of("src/test/resources/golden/allow.csv"));

    private Goldens() {
    }

    static Map<String, ImageBuilder> all() {
        return Map.ofEntries(Map.entry("bitnami-app", bitnamiApp()), Map.entry("olm-catalog", olmCatalog()),
                Map.entry("go-operator", goOperator()), Map.entry("php-from-source", phpFromSource()),
                Map.entry("rpm-rootfs", rpmRootfs()), Map.entry("binary-on-ubi", binaryOnUbi()),
                Map.entry("tool-image", toolImage()), Map.entry("java-on-ubi8", javaOnUbi8()),
                Map.entry("s2i-php", s2iPhp()), Map.entry("python-on-ubi", pythonOnUbi()));
    }

    private static ImageBuilder labelled(ImageBuilder builder) {
        return builder.arg("REVISION").arg("SOURCE")
                .label("org.opencontainers.image.revision", "${REVISION}")
                .label("org.opencontainers.image.source", "${SOURCE}");
    }

    static ImageBuilder bitnamiApp() {
        String component = "notation-1.3.2-27-linux-${OS_ARCH}-debian-12";
        return labelled(ImageBuilder.finalStage(MINIDEB))
                .arg("DOWNLOADS_URL", "https://downloads.bitnami.com/files/stacksmith")
                .arg("TARGETARCH")
                .label("org.opencontainers.image.title", "notation")
                .label("org.opencontainers.image.version", "1.3.2")
                .env(OrderedMap.of("HOME", "/", "OS_ARCH", "${TARGETARCH:-amd64}", "OS_FLAVOUR", "debian-12",
                        "OS_NAME", "linux"))
                .copy("prebuildfs", "/")
                .shell("/bin/bash", "-o", "errexit", "-o", "nounset", "-o", "pipefail", "-c")
                .install(PackageManager.APT, List.of("-y", "--no-install-recommends"),
                        List.of("ca-certificates", "curl", "procps"))
                .run("mkdir -p /tmp/bitnami/pkg/cache /.config",
                        "curl -SsLf \"${DOWNLOADS_URL}/" + component + ".tar.gz\" -o /tmp/bitnami/pkg/cache/"
                                + component + ".tar.gz",
                        "(cd /tmp/bitnami/pkg/cache && sha256sum -c /opt/bitnami/checksums/" + component
                                + ".tar.gz.sha256)",
                        "tar -zxf /tmp/bitnami/pkg/cache/" + component
                                + ".tar.gz -C /opt/bitnami --strip-components=2 --no-same-owner",
                        "rm -rf /tmp/bitnami /opt/bitnami/checksums",
                        "{ find / -perm /6000 -type f -exec chmod a-s {} \\; || true; }",
                        "apt-get purge -y --auto-remove curl",
                        "rm -rf /var/lib/apt/lists/*")
                .writablePaths("/opt/bitnami", "/.config")
                .env(OrderedMap.of("APP_VERSION", "1.3.2", "BITNAMI_APP_NAME", "notation",
                        "PATH", "/opt/bitnami/notation/bin:$PATH"))
                .user(1001)
                .entrypoint("notation")
                .cmd("--help");
    }

    static ImageBuilder olmCatalog() {
        return labelled(ImageBuilder.finalStage(OPERATOR_REGISTRY))
                .copy("catalog/", "/configs")
                .runExec("/bin/opm", "serve", "/configs", "--cache-dir=/tmp/cache", "--cache-only")
                .label("operators.operatorframework.io.index.configs.v1", "/configs")
                .writablePaths("/tmp/cache")
                .user(1001)
                .entrypoint("/bin/opm")
                .cmd("serve", "/configs", "--cache-dir=/tmp/cache");
    }

    static ImageBuilder goOperator() {
        String source = "/go/src/github.com/k8snetworkplumbingwg/ptp-operator";
        return labelled(ImageBuilder.stage("builder", OCP_BUILDER)
                .workdir(source)
                .copy(".", ".")
                .env("GO111MODULE", "auto")
                .run("make")
                .thenFrom(OCP_BASE))
                .copyFrom("builder", source + "/build/_output/bin/ptp-operator", "/usr/local/bin/")
                .copyFrom("builder", source + "/manifests", "/manifests")
                .copy("bindata", "/bindata")
                .label(OrderedMap.of("io.k8s.display-name", "OpenShift ptp-operator",
                        "io.k8s.description", "This is a component that manages cluster PTP configuration.",
                        "io.openshift.tags", "openshift,ptp"))
                .user(1001)
                .entrypoint("/usr/local/bin/ptp-operator");
    }

    static ImageBuilder phpFromSource() {
        return labelled(ImageBuilder.finalStage(DEBIAN))
                .run("{ echo 'Package: php*'; echo 'Pin: release *'; echo 'Pin-Priority: -1'; }"
                        + " > /etc/apt/preferences.d/no-debian-php")
                .env("PHPIZE_DEPS", "autoconf dpkg-dev file g++ gcc libc-dev make pkg-config re2c")
                .install(PackageManager.APT, List.of("-y", "--no-install-recommends"),
                        List.of("$PHPIZE_DEPS", "ca-certificates", "curl", "xz-utils"))
                .env("PHP_INI_DIR", "/usr/local/etc/php")
                .run("mkdir -p \"$PHP_INI_DIR/conf.d\"", "mkdir -p /var/www/html")
                .env(OrderedMap.of(
                        "PHP_CFLAGS",
                        "-fstack-protector-strong -fpic -fpie -O2 -D_LARGEFILE_SOURCE -D_FILE_OFFSET_BITS=64",
                        "PHP_CPPFLAGS", "$PHP_CFLAGS", "PHP_LDFLAGS", "-Wl,-O1 -pie",
                        "GPG_KEYS", "AFD8691FDAEDF03BDF6E460563F15A9B715376CA 9D7F99A0CB8F05C8A6958D6256A97AF7600A39A6"
                                + " 0616E93D95AF471243E26761770426E17EBBB3DD",
                        "PHP_VERSION", "8.4.27", "PHP_URL", "https://www.php.net/distributions/php-8.4.27.tar.xz",
                        "PHP_ASC_URL", "https://www.php.net/distributions/php-8.4.27.tar.xz.asc",
                        "PHP_SHA256", "dcf93923b0e38e0d5252b25b796520b5a47761a08b3488b6d0b5d1115764d3f0"))
                .run("savedAptMark=\"$(apt-mark showmanual)\"",
                        "apt-get update",
                        "apt-get install -y --no-install-recommends gnupg",
                        "rm -rf /var/lib/apt/lists/*",
                        "mkdir -p /usr/src",
                        "cd /usr/src",
                        "curl -fsSL -o php.tar.xz \"$PHP_URL\"",
                        "echo \"$PHP_SHA256 *php.tar.xz\" | sha256sum -c -",
                        "curl -fsSL -o php.tar.xz.asc \"$PHP_ASC_URL\"",
                        "export GNUPGHOME=\"$(mktemp -d)\"",
                        "for key in $GPG_KEYS; do gpg --batch --keyserver keyserver.ubuntu.com --recv-keys \"$key\";"
                                + " done",
                        "gpg --batch --verify php.tar.xz.asc php.tar.xz",
                        "gpgconf --kill all",
                        "rm -rf \"$GNUPGHOME\"",
                        "apt-mark auto '.*' > /dev/null",
                        "apt-mark manual $savedAptMark > /dev/null",
                        "apt-get purge -y --auto-remove -o APT::AutoRemove::RecommendsImportant=false")
                .copy("docker-php-source", "/usr/local/bin/")
                .run("savedAptMark=\"$(apt-mark showmanual)\"",
                        "apt-get update",
                        "apt-get install -y --no-install-recommends libargon2-dev libcurl4-openssl-dev libonig-dev"
                                + " libreadline-dev libsodium-dev libsqlite3-dev libssl-dev libxml2-dev zlib1g-dev",
                        "export CFLAGS=\"$PHP_CFLAGS\" CPPFLAGS=\"$PHP_CPPFLAGS\" LDFLAGS=\"$PHP_LDFLAGS\""
                                + " PHP_BUILD_PROVIDER='https://github.com/docker-library/php' PHP_UNAME='Linux -"
                                + " Docker'",
                        "docker-php-source extract",
                        "cd /usr/src/php",
                        "gnuArch=\"$(dpkg-architecture --query DEB_BUILD_GNU_TYPE)\"",
                        "debMultiarch=\"$(dpkg-architecture --query DEB_BUILD_MULTIARCH)\"",
                        "if [ ! -d /usr/include/curl ]; then ln -sT \"/usr/include/$debMultiarch/curl\""
                                + " /usr/local/include/curl; fi",
                        "./configure --build=\"$gnuArch\" --sysconfdir=\"${PHP_INI_DIR%/php}\""
                                + " --with-config-file-path=\"$PHP_INI_DIR\""
                                + " --with-config-file-scan-dir=\"$PHP_INI_DIR/conf.d\" --enable-option-checking=fatal"
                                + " --with-mhash --with-pic --enable-mbstring --enable-mysqlnd --with-password-argon2"
                                + " --with-sodium=shared --with-pdo-sqlite=/usr --with-sqlite3=/usr --with-curl"
                                + " --with-iconv --with-openssl --with-readline --with-zlib --enable-phpdbg"
                                + " --enable-phpdbg-readline --with-pear --with-libdir=\"lib/$debMultiarch\""
                                + " --enable-embed --enable-zts --disable-zend-signals",
                        "make -j \"$(nproc)\"",
                        "find -type f -name '*.a' -delete",
                        "make install",
                        "find /usr/local -type f -perm '/0111' -exec sh -euxc 'strip --strip-all \"$@\" || :' -- '{}'"
                                + " +",
                        "make clean",
                        "cp -v php.ini-* \"$PHP_INI_DIR/\"",
                        "cd /",
                        "docker-php-source delete",
                        "apt-mark auto '.*' > /dev/null",
                        "{ [ -z \"$savedAptMark\" ] || apt-mark manual $savedAptMark; }",
                        "find /usr/local -type f -executable -exec ldd '{}' ';' | awk '/=>/ { so = $(NF-1); if"
                                + " (index(so, \"/usr/local/\") == 1) { next }; gsub(\"^/(usr/)?\", \"\", so); printf"
                                + " \"*%s\\n\", so }' | sort -u | xargs -rt dpkg-query --search | awk 'sub(\":$\","
                                + " \"\", $1) { print $1 }' | sort -u | xargs -r apt-mark manual",
                        "apt-get purge -y --auto-remove -o APT::AutoRemove::RecommendsImportant=false",
                        "rm -rf /var/lib/apt/lists/*",
                        "pecl update-channels",
                        "rm -rf /tmp/pear ~/.pearrc",
                        "php --version")
                .copy(Copy.of("docker-php-ext-*", "docker-php-entrypoint").to("/usr/local/bin/"))
                .run("docker-php-ext-enable opcache", "docker-php-ext-enable sodium")
                .writablePaths("/var/www/html")
                .user(1001)
                .entrypoint("docker-php-entrypoint")
                .cmd("php", "-a");
    }

    static ImageBuilder rpmRootfs() {
        return ImageBuilder.stage("minimal-ubi", UBI)
                .run("dnf update -y --allowerasing",
                        "mkdir /image",
                        "ln -s usr/bin /image/bin",
                        "ln -s usr/sbin /image/sbin",
                        "ln -s usr/lib64 /image/lib64",
                        "ln -s usr/lib /image/lib",
                        "mkdir -p /image/{usr/bin,usr/lib64,usr/lib,root,home,proc,etc,sys,var,dev}",
                        "rpm --root /image --initdb",
                        "dnf install -y --installroot=/image --releasever=9 --setopt=install_weak_deps=false --nodocs"
                                + " glibc tzdata ca-certificates",
                        "dnf clean all --installroot=/image",
                        "rm -rf /image/var/cache/dnf /image/var/log/*")
                .thenFrom(SCRATCH)
                .arg("REVISION").arg("SOURCE")
                .label("org.opencontainers.image.revision", "${REVISION}")
                .label("org.opencontainers.image.source", "${SOURCE}")
                .label("description", "External Secrets Operator is a Kubernetes operator that integrates external"
                        + " secret management systems")
                .copyFrom("minimal-ubi", "/image/", "/")
                .arg("TARGETOS").arg("TARGETARCH")
                .copy("bin/external-secrets-${TARGETOS}-${TARGETARCH}", "/bin/external-secrets")
                .user(65534)
                .entrypoint("/bin/external-secrets");
    }

    static ImageBuilder binaryOnUbi() {
        return labelled(ImageBuilder.finalStage(UBI_MINIMAL))
                .copy("private-org-peribolos-sync", "/usr/bin/private-org-peribolos-sync")
                .user(1001)
                .entrypoint("/usr/bin/private-org-peribolos-sync");
    }

    static ImageBuilder toolImage() {
        return labelled(ImageBuilder.finalStage(UBI_MINIMAL))
                .copy("install-deps.sh", "/tmp/")
                .run("/tmp/install-deps.sh", "rm /tmp/install-deps.sh")
                .user(1001);
    }

    static ImageBuilder javaOnUbi8() {
        return labelled(ImageBuilder.finalStage(UBI8_MINIMAL))
                .arg("JAVA_PACKAGE", "java-11-openjdk-headless")
                .arg("RUN_JAVA_VERSION", "1.3.8")
                .env(OrderedMap.of("LANG", "en_US.UTF-8", "LANGUAGE", "en_US:en"))
                .install("curl", "ca-certificates", "${JAVA_PACKAGE}")
                .run("mkdir /deployments",
                        "curl https://repo1.maven.org/maven2/io/fabric8/run-java-sh/${RUN_JAVA_VERSION}/run-java-sh-"
                                + "${RUN_JAVA_VERSION}-sh.sh -o /deployments/run-java.sh",
                        "chmod 550 /deployments/run-java.sh",
                        "echo \"securerandom.source=file:/dev/urandom\""
                                + " >> /etc/alternatives/jre/lib/security/java.security")
                .env("JAVA_OPTIONS",
                        "-Dquarkus.http.host=0.0.0.0 -Djava.util.logging.manager=org.jboss.logmanager.LogManager")
                .copy("target/lib/*", "/deployments/lib/")
                .copy("target/*-runner.jar", "/deployments/app.jar")
                .writablePaths("/deployments")
                .expose(8080)
                .user(1001)
                .entrypoint("/deployments/run-java.sh");
    }

    static ImageBuilder s2iPhp() {
        return labelled(ImageBuilder.finalStage(S2I_BASE))
                .expose("8080", "8443")
                .env(OrderedMap.of("PHP_VERSION", "8.0", "PHP_VER_SHORT", "80", "NAME", "php"))
                .env(OrderedMap.of("SUMMARY", "Platform for building and running PHP $PHP_VERSION applications",
                        "DESCRIPTION", "PHP $PHP_VERSION available as container is a base platform for building and"
                                + " running various PHP $PHP_VERSION applications and frameworks."))
                .label(OrderedMap.of("summary", "${SUMMARY}", "description", "${DESCRIPTION}",
                        "io.k8s.description", "${DESCRIPTION}",
                        "io.k8s.display-name", "Apache 2.4 with PHP ${PHP_VERSION}",
                        "io.openshift.expose-services", "8080:http",
                        "io.openshift.tags", "builder,${NAME},${NAME}${PHP_VER_SHORT},${NAME}-${PHP_VER_SHORT}",
                        "io.openshift.s2i.scripts-url", "image:///usr/libexec/s2i",
                        "io.s2i.scripts-url", "image:///usr/libexec/s2i",
                        "name", "sclorg/${NAME}-${PHP_VER_SHORT}-c9s", "version", "1"))
                .arg("INSTALL_PKGS", "php php-fpm php-mysqlnd php-pgsql php-bcmath php-gd php-intl php-ldap"
                        + " php-mbstring php-pdo php-process php-soap php-opcache php-xml php-gmp php-pecl-apcu"
                        + " php-pecl-zip mod_ssl hostname")
                .run("yum install -y --setopt=tsflags=nodocs $INSTALL_PKGS",
                        "yum reinstall -y tzdata",
                        "rpm -V $INSTALL_PKGS",
                        "php -v | grep -qe \"v$PHP_VERSION\\.\"",
                        "echo \"Found VERSION $PHP_VERSION\"",
                        "yum -y clean all --enablerepo='*'")
                .env(OrderedMap.of("PHP_CONTAINER_SCRIPTS_PATH", "/usr/share/container-scripts/php/",
                        "APP_DATA", "${APP_ROOT}/src", "PHP_DEFAULT_INCLUDE_PATH", "/usr/share/pear",
                        "PHP_SYSCONF_PATH", "/etc", "PHP_HTTPD_CONF_FILE", "php.conf",
                        "PHP_FPM_CONF_D_PATH", "/etc/php-fpm.d", "PHP_FPM_CONF_FILE", "www.conf",
                        "PHP_FPM_RUN_DIR", "/run/php-fpm", "PHP_MAIN_FPM_CONF_FILE", "/etc/php-fpm.conf",
                        "PHP_FPM_LOG_PATH", "/var/log/php-fpm", "HTTPD_CONFIGURATION_PATH", "${APP_ROOT}/etc/conf.d",
                        "HTTPD_MAIN_CONF_PATH", "/etc/httpd/conf", "HTTPD_MAIN_CONF_D_PATH", "/etc/httpd/conf.d",
                        "HTTPD_MODULES_CONF_D_PATH", "/etc/httpd/conf.modules.d", "HTTPD_VAR_RUN", "/var/run/httpd",
                        "HTTPD_DATA_PATH", "/var/www", "HTTPD_DATA_ORIG_PATH", "/var/www", "HTTPD_VAR_PATH", "/var"))
                .copy("./s2i/bin/", "$STI_SCRIPTS_PATH")
                .copy("./root/", "/")
                .run("/usr/libexec/container-setup", "rpm-file-permissions")
                .user(1001)
                .cmd("/usr/libexec/s2i/usage");
    }

    static ImageBuilder pythonOnUbi() {
        return labelled(ImageBuilder.finalStage(PYTHON_311))
                .user(0)
                .run("dnf remove sqlite3 -y",
                        "wget https://www.sqlite.org/2023/sqlite-autoconf-3410200.tar.gz",
                        "tar -xvzf sqlite-autoconf-3410200.tar.gz",
                        "cd sqlite-autoconf-3410200",
                        "./configure",
                        "make",
                        "make install",
                        "mv /usr/local/bin/sqlite3 /usr/bin/sqlite3",
                        "cd ..",
                        "rm -rf sqlite-autoconf-3410200 sqlite-autoconf-3410200.tar.gz")
                .env("LD_LIBRARY_PATH", "/usr/local/lib")
                .workdir("/rag")
                .copy("requirements.txt", ".")
                .run("pip install --upgrade pip", "pip install --no-cache-dir --upgrade -r /rag/requirements.txt",
                        "mkdir -p /rag/models/")
                .copy(Copy.of("rag_app.py", "manage_vectordb.py").to("."))
                .expose(8501)
                .env("HF_HUB_CACHE", "/rag/models/")
                .writablePaths("/rag/models/")
                .user(1001)
                .entrypoint("streamlit", "run", "rag_app.py");
    }
}
