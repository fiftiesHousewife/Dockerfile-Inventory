package io.github.fiftieshousewife.dockerfileinventory.dsl;

import java.util.List;
import java.util.Map;

/**
 * Four hand-picked images (Phase 4, Q3), standing in for the top Phase 2 archetypes until a real run exists. Each
 * meets every invariant, and their rendered Dockerfiles are committed under {@code src/test/resources/golden/}.
 */
final class Goldens {

    static final String REGISTRY = "registry.example.com/";
    static final BaseImage UBI_MINIMAL = new BaseImage(REGISTRY + "ubi9/ubi-minimal", "9.4",
            "sha256:" + "1".repeat(64));
    static final BaseImage UBI = new BaseImage(REGISTRY + "ubi9/ubi", "9.4", "sha256:" + "2".repeat(64));
    static final BaseImage GO_TOOLSET = new BaseImage(REGISTRY + "ubi9/go-toolset", "1.22",
            "sha256:" + "3".repeat(64));
    static final Policy POLICY = Policy.of(List.of(new Policy.Approved("ubi9/ubi-minimal", "9"),
            new Policy.Approved("ubi9/ubi", "9")));

    private Goldens() {
    }

    static Map<String, ImageBuilder> all() {
        return Map.of("java-service", javaService(), "node-service", nodeService(), "python-service",
                pythonService(), "go-multistage", goMultistage());
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
}
