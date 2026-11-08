package io.github.fiftieshousewife.dockerfileinventory.dsl;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;

final class Goldens {

    static final String REGISTRY = "registry.example.com/";
    static final BaseImage UBI_MINIMAL = new BaseImage(REGISTRY + "ubi9/ubi-minimal", "9.4",
            "sha256:" + "1".repeat(64));
    static final BaseImage UBI = new BaseImage(REGISTRY + "ubi9/ubi", "9.4", "sha256:" + "2".repeat(64));
    static final BaseImage GO_TOOLSET = new BaseImage(REGISTRY + "ubi9/go-toolset", "1.22",
            "sha256:" + "3".repeat(64));
    static final Policy POLICY = Policy.read(Path.of("src/test/resources/golden/allow.csv"));

    private Goldens() {
    }

    static final BaseImage MINIDEB = new BaseImage("docker.io/bitnami/minideb", "bookworm", "sha256:" + "4".repeat(64));

    static final BaseImage OPERATOR_REGISTRY = new BaseImage(
            "registry.redhat.io/openshift4/ose-operator-registry-rhel9", "v4.22", "sha256:" + "5".repeat(64));

    static final BaseImage OCP_BUILDER = new BaseImage("registry.ci.openshift.org/ocp/builder",
            "rhel-9-golang-1.26-openshift-5.0", "sha256:" + "6".repeat(64));
    static final BaseImage OCP_BASE = new BaseImage("registry.ci.openshift.org/ocp/5.0", "base-rhel9",
            "sha256:" + "7".repeat(64));

    static final BaseImage DEBIAN = new BaseImage("debian", "bookworm-slim", "sha256:" + "8".repeat(64));

    static final BaseImage SCRATCH = new BaseImage("scratch", null, null);

    static Map<String, ImageBuilder> all() {
        return Map.of("java-service", javaService(), "node-service", nodeService(), "python-service",
                pythonService(), "go-multistage", goMultistage(), "bitnami-app", bitnamiApp(), "olm-catalog",
                olmCatalog(), "go-operator", goOperator(), "php-from-source", phpFromSource(),
                "rpm-rootfs", rpmRootfs(), "binary-on-ubi", binaryOnUbi());
    }

    private static ImageBuilder labelled(ImageBuilder builder) {
        return builder.arg("REVISION").arg("SOURCE")
                .label("org.opencontainers.image.revision", "${REVISION}")
                .label("org.opencontainers.image.source", "${SOURCE}");
    }

    static ImageBuilder javaService() {
        return labelled(ImageBuilder.finalStage(UBI_MINIMAL))
                .install("java-21-openjdk-headless", "shadow-utils")
                .workdir("/opt/app")
                .copy("target/app.jar", "/opt/app/app.jar")
                .writablePaths("/opt/app")
                .expose(8080)
                .user(1001)
                .entrypoint("java", "-jar", "/opt/app/app.jar");
    }

    static ImageBuilder nodeService() {
        return labelled(ImageBuilder.finalStage(UBI_MINIMAL))
                .install("nodejs", "npm")
                .run("npm config set fund false --location=global")
                .workdir("/opt/app")
                .copy("package.json", "/opt/app/")
                .run("npm ci --omit=dev")
                .copy("dist/", "/opt/app/dist/")
                .writablePaths("/opt/app")
                .env("NODE_ENV", "production")
                .expose(3000)
                .user(1001)
                .entrypoint("node", "/opt/app/dist/server.js");
    }

    static ImageBuilder pythonService() {
        return labelled(ImageBuilder.finalStage(UBI))
                .install("python3.11", "python3.11-pip")
                .workdir("/opt/app")
                .copy("requirements.txt", "/opt/app/")
                .run("python3.11 -m pip install --no-cache-dir -r requirements.txt")
                .copy("src/", "/opt/app/src/")
                .writablePaths("/opt/app")
                .env("PYTHONUNBUFFERED", "1")
                .expose(8000)
                .user(1001)
                .entrypoint("python3.11", "-m", "app")
                .cmd("--port", "8000");
    }

    static ImageBuilder goMultistage() {
        return ImageBuilder.stage("build", GO_TOOLSET)
                .workdir("/src")
                .copy(".", "/src/")
                .run("go build -o /src/out/server ./cmd/server")
                .thenFrom(UBI_MINIMAL)
                .arg("REVISION").arg("SOURCE")
                .label("org.opencontainers.image.revision", "${REVISION}")
                .label("org.opencontainers.image.source", "${SOURCE}")
                .copyFrom("build", "/src/out/server", "/usr/local/bin/server")
                .user(1001)
                .entrypoint("/usr/local/bin/server");
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
                .copy(List.of("docker-php-ext-*", "docker-php-entrypoint"), "/usr/local/bin/", null, null)
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
                .copy(List.of("/image/"), "/", "0:0", "minimal-ubi")
                .arg("TARGETOS").arg("TARGETARCH")
                .copy(List.of("bin/external-secrets-${TARGETOS}-${TARGETARCH}"), "/bin/external-secrets", "0:0", null)
                .user(65534)
                .entrypoint("/bin/external-secrets");
    }

    static ImageBuilder binaryOnUbi() {
        return labelled(ImageBuilder.finalStage(UBI_MINIMAL))
                .copy(List.of("private-org-peribolos-sync"), "/usr/bin/private-org-peribolos-sync", "0:0", null)
                .user(1001)
                .entrypoint("/usr/bin/private-org-peribolos-sync");
    }
}
