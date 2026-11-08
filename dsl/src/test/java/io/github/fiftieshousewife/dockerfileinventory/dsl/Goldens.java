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

    static Map<String, ImageBuilder> all() {
        return Map.of("java-service", javaService(), "node-service", nodeService(), "python-service",
                pythonService(), "go-multistage", goMultistage(), "bitnami-app", bitnamiApp(), "olm-catalog",
                olmCatalog(), "go-operator", goOperator());
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
}
