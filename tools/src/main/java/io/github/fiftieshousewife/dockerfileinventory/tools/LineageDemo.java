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
import java.util.function.BooleanSupplier;

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

    record Ready(String repository, String token) {
    }

    // Polls until a condition holds, or gives up saying what did not come up.
    record Waiter(int attempts, Duration every) {

        static Waiter immediate() {
            return new Waiter(1, Duration.ZERO);
        }

        void until(String what, BooleanSupplier up) {
            for (int i = 0; i < attempts; i++) {
                if (up.getAsBoolean()) {
                    return;
                }
                try {
                    Thread.sleep(every);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    throw new IllegalStateException(e);
                }
            }
            throw new IllegalStateException(what + " did not come up");
        }
    }

    private final Commands docker;
    private final JcrApi jcr;
    private final JcrContainers containers;
    private final Waiter waiter;
    private final Map<String, String> env;

    LineageDemo(Commands docker, JcrApi jcr, JcrContainers containers, Waiter waiter, Map<String, String> env) {
        this.docker = docker;
        this.jcr = jcr;
        this.containers = containers;
        this.waiter = waiter;
        this.env = env;
    }

    /**
     * Runs the demo with the environment's settings.
     *
     * @param args none
     */
    public static void main(String[] args) {
        Map<String, String> env = System.getenv();
        int port = Integer.parseInt(env.getOrDefault("JCR_PORT", "8082"));
        Commands docker = Commands.processes();
        new LineageDemo(docker, new JcrApi(URI.create("http://localhost:" + port + "/"), password(env),
                HttpClient.newHttpClient()), new JcrContainers(docker, env.getOrDefault("JCR_IMAGE", JCR_IMAGE),
                env.getOrDefault("POSTGRES_IMAGE", POSTGRES_IMAGE), port), new Waiter(120, Duration.ofSeconds(5)), env)
                .run();
    }

    void run() {
        Path discovery = Path.of(env.getOrDefault("DISCOVERY_DIR", "out/demo4/discovery"));
        Path facts = Path.of(env.getOrDefault("FACTS_DIR", "out/demo5/facts"));
        Path out = Path.of(env.getOrDefault("OUT", "out/lineage-demo")).toAbsolutePath();
        require(discovery.resolve("manifest.jsonl"), "run discovery first");
        require(facts.resolve("facts.jsonl"), "run facts on " + discovery + " first");
        Ready ready = registry();
        Path log = out.resolve("build.log");
        images(discovery, out, log, ready);
        String api = "http://localhost:" + env.getOrDefault("JCR_PORT", "8082") + "/artifactory";
        LineageMain.run(Map.of("ARTIFACTORY_URL", api, "ARTIFACTORY_TOKEN", ready.token(), "FACTS_DIR",
                facts.toString(), "DISCOVERY_DIR", discovery.toString(), "OUT_DIR", out.resolve("lineage").toString(),
                "CONCURRENCY", "4"));
        ReportMain.run(Map.of("FACTS_DIR", facts.toString(), "DISCOVERY_DIR", discovery.toString(), "LINEAGE_DIR",
                out.resolve("lineage").toString(), "OUT_DIR", out.resolve("report").toString()));
        RulesMain.run(Map.of("DISCOVERY_DIR", discovery.toString(), "LINEAGE_DIR", out.resolve("lineage").toString(),
                "OUT_DIR", out.resolve("rules").toString()));
        System.out.println("output: " + out.resolve("lineage") + ", " + out.resolve("report/index.html") + ", "
                + out.resolve("rules"));
    }

    // PostgreSQL and JCR up, a local Docker repository, the licence agreement, the Docker API, and a read token.
    Ready registry() {
        int memory = containers.memoryGb();
        if (memory < 4) {
            throw new IllegalStateException("Docker may use " + memory + " GB; JCR needs 4 or more (Docker Desktop > "
                    + "Settings > Resources > Memory)");
        }
        containers.network();
        containers.postgres();
        waiter.until("PostgreSQL", containers::postgresReady);
        containers.jcr();
        waiter.until("JCR (a first start takes a few minutes; jcrDiagnose says why not)", jcr::answers);
        if (!jcr.adminAccepted()) {
            throw new IllegalStateException("admin login refused; if you changed the admin password in the UI, set "
                    + "ADMIN_PASSWORD");
        }
        String repository = repository();
        if (jcr.agreementPending(repository)) {
            if (!"yes".equals(env.get("ACCEPT_JCR_EULA"))) {
                throw new IllegalStateException("JCR's Docker API stays off until JFrog's JCR licence agreement is "
                        + "accepted. Read it at the UI's first login, then accept it there or rerun with "
                        + "ACCEPT_JCR_EULA=yes to accept it through the API");
            }
            jcr.acceptAgreement();
        }
        waiter.until("the Docker API", () -> jcr.dockerApiReady(repository));
        String token = env.getOrDefault("ARTIFACTORY_TOKEN", "");
        token = token.isEmpty() ? jcr.token() : token;
        if (!jcr.canRead(token)) {
            throw new IllegalStateException("ARTIFACTORY_TOKEN cannot list JCR's repositories");
        }
        return new Ready(repository, token);
    }

    // Through the API, then JCR's setup wizard; else the person makes it in the UI and reruns.
    private String repository() {
        String wanted = "docker-local";
        if (jcr.repository(wanted).isEmpty()) {
            System.out.println("creating " + wanted + " through the API: " + jcr.create(wanted));
        }
        if (jcr.repository(wanted).isEmpty()) {
            containers.askWizardForDocker();
            waiter.until("JCR after its restart", jcr::answers);
        }
        return jcr.repository(wanted).orElseThrow(() -> new IllegalStateException("JCR would not create the "
                + "repository; in its UI: Administration > Repositories > Create a Repository > Local > Docker, key '"
                + wanted + "', then rerun"));
    }

    void images(Path discovery, Path out, Path log, Ready ready) {
        String registry = JcrContainers.JCR + ":8082/" + ready.repository();
        String architecture = docker.run("docker", "info", "--format", "{{.Architecture}}").out().strip()
                .replace("x86_64", "amd64").replace("aarch64", "arm64");
        DemoImages images = new DemoImages(docker, registry, password(env), env.getOrDefault("SKOPEO_IMAGE",
                SKOPEO_IMAGE), env.getOrDefault("BUILDAH_IMAGE", BUILDAH_IMAGE), "linux/" + architecture);
        try {
            Files.createDirectories(out.resolve("src"));
            Files.deleteIfExists(log);
            String mirror = env.get("MIRROR");
            if (mirror != null && !mirror.isBlank()) {
                images.mirror(Files.readAllLines(Path.of(mirror)), log);
                return;
            }
            List<BuildPlan.Build> all = BuildPlan.all(discovery);
            List<BuildPlan.Build> plan = BuildPlan.ordered("all".equals(env.get("SCOPE")) ? all
                    : BuildPlan.chain(all));
            Files.write(out.resolve("plan.txt.ordered"), plan.stream().map(BuildPlan.Build::line).toList());
            images.clone(plan, out.resolve("src"));
            images.build(plan, out, log);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static String password(Map<String, String> env) {
        return env.getOrDefault("ADMIN_PASSWORD", "password");
    }

    private static void require(Path file, String hint) {
        if (!Files.isRegularFile(file)) {
            throw new IllegalArgumentException("no " + file + "; " + hint);
        }
    }
}
