package io.github.fiftieshousewife.dockerfileinventory.tools;

import io.github.fiftieshousewife.dockerfileinventory.lineage.LineageMain;
import io.github.fiftieshousewife.dockerfileinventory.report.ReportMain;
import io.github.fiftieshousewife.dockerfileinventory.rules.RulesMain;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Map;

/**
 * Lineage end to end on a laptop: PostgreSQL and a local JFrog Container Registry in Docker, the images (copied
 * unchanged from a {@code MIRROR} list that {@code selectOkd} writes, or built with Buildah from the Dockerfiles
 * discovery found), then lineage, report and rules on them. {@code lineageDemo}; settings {@code DISCOVERY_DIR}
 * ({@code out/demo4/discovery}), {@code FACTS_DIR} ({@code out/demo5/facts}), {@code OUT} ({@code out/lineage-demo}),
 * {@code SCOPE} ({@code chain} or {@code all}), {@code MIRROR}, {@code ACCEPT_JCR_EULA=yes} (accepts JFrog's JCR
 * licence agreement for you: read it first), {@code JCR_PORT} (8082), {@code ADMIN_PASSWORD} ({@code password}),
 * {@code JCR_IMAGE}, {@code POSTGRES_IMAGE}, {@code BUILDAH_IMAGE}, {@code SKOPEO_IMAGE}, {@code ARTIFACTORY_TOKEN}.
 * Rerunning reuses running containers, the repository and clones. Remove everything afterwards with
 * {@code docker rm -f inventory-jcr inventory-postgres && docker volume rm inventory-jcr inventory-postgres}.
 */
public final class LineageDemo {

    static final String JCR_IMAGE = "releases-docker.jfrog.io/jfrog/artifactory-jcr:7.77.17";
    static final String POSTGRES_IMAGE = "postgres:16.4";
    static final String BUILDAH_IMAGE = "quay.io/buildah/stable:v1.37.0";
    static final String SKOPEO_IMAGE = "quay.io/skopeo/stable:v1.16.1";

    private final Commands docker;
    private final DemoRegistry registry;
    private final Map<String, String> env;

    LineageDemo(Commands docker, DemoRegistry registry, Map<String, String> env) {
        this.docker = docker;
        this.registry = registry;
        this.env = env;
    }

    /**
     * Runs the demo with the environment's settings.
     *
     * @param args none
     */
    public static void main(String[] args) {
        of(System.getenv()).run();
    }

    static LineageDemo of(Map<String, String> env) {
        int port = Integer.parseInt(env.getOrDefault("JCR_PORT", "8082"));
        Commands docker = Commands.processes();
        JcrApi jcr = new JcrApi(URI.create("http://localhost:" + port + "/"), password(env),
                HttpClient.newHttpClient());
        JcrContainers containers = new JcrContainers(docker, env.getOrDefault("JCR_IMAGE", JCR_IMAGE),
                env.getOrDefault("POSTGRES_IMAGE", POSTGRES_IMAGE), port);
        return new LineageDemo(docker, new DemoRegistry(jcr, containers, new Waiter(120, Duration.ofSeconds(5)), env),
                env);
    }

    void run() {
        Path discovery = Path.of(env.getOrDefault("DISCOVERY_DIR", "out/demo4/discovery"));
        Path facts = Path.of(env.getOrDefault("FACTS_DIR", "out/demo5/facts"));
        Path out = Path.of(env.getOrDefault("OUT", "out/lineage-demo")).toAbsolutePath();
        require(discovery.resolve("manifest.jsonl"), "run discovery first");
        require(facts.resolve("facts.jsonl"), "run facts on " + discovery + " first");
        DemoRegistry.Ready ready = registry.start();
        images(discovery, out, out.resolve("build.log"), ready);
        stages(discovery, facts, out, ready.token());
    }

    private void stages(Path discovery, Path facts, Path out, String token) {
        String api = "http://localhost:" + env.getOrDefault("JCR_PORT", "8082") + "/artifactory";
        String lineage = out.resolve("lineage").toString();
        LineageMain.run(Map.of("ARTIFACTORY_URL", api, "ARTIFACTORY_TOKEN", token, "FACTS_DIR", facts.toString(),
                "DISCOVERY_DIR", discovery.toString(), "OUT_DIR", lineage, "ARTIFACTORY_CONCURRENCY", "4",
                "ARTIFACTORY_MAX_REQUESTS_PER_SECOND", "50"));
        ReportMain.run(Map.of("FACTS_DIR", facts.toString(), "DISCOVERY_DIR", discovery.toString(), "LINEAGE_DIR",
                lineage, "OUT_DIR", out.resolve("report").toString()));
        RulesMain.run(Map.of("DISCOVERY_DIR", discovery.toString(), "LINEAGE_DIR", lineage, "OUT_DIR",
                out.resolve("rules").toString()));
        System.out.println("output: " + lineage + ", " + out.resolve("report/index.html") + ", "
                + out.resolve("rules"));
    }

    void images(Path discovery, Path out, Path log, DemoRegistry.Ready ready) {
        DemoImages images = demoImages(ready.repository());
        try {
            Files.createDirectories(out.resolve("src"));
            Files.deleteIfExists(log);
            String mirror = env.get("MIRROR");
            if (mirror != null && !mirror.isBlank()) {
                images.mirror(Files.readAllLines(Path.of(mirror)), log);
                return;
            }
            List<BuildPlan.Build> plan = plan(BuildPlan.all(discovery));
            Files.write(out.resolve("plan.txt.ordered"), plan.stream().map(BuildPlan.Build::line).toList());
            images.clone(plan, out.resolve("src"));
            images.build(plan, out, log);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private DemoImages demoImages(String repository) {
        String architecture = docker.run("docker", "info", "--format", "{{.Architecture}}").out().strip()
                .replace("x86_64", "amd64").replace("aarch64", "arm64");
        return new DemoImages(docker, JcrContainers.JCR + ":8082/" + repository, password(env),
                env.getOrDefault("SKOPEO_IMAGE", SKOPEO_IMAGE), env.getOrDefault("BUILDAH_IMAGE", BUILDAH_IMAGE),
                "linux/" + architecture);
    }

    List<BuildPlan.Build> plan(List<BuildPlan.Build> all) {
        return BuildPlan.parentsFirst("all".equals(env.get("SCOPE")) ? all : BuildPlan.chain(all));
    }

    private static String password(Map<String, String> env) {
        return env.getOrDefault("ADMIN_PASSWORD", "password");
    }

    static void require(Path file, String hint) {
        if (!Files.isRegularFile(file)) {
            throw new IllegalArgumentException("no " + file + "; " + hint);
        }
    }
}
