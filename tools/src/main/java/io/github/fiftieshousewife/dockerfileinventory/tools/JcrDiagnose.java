package io.github.fiftieshousewife.dockerfileinventory.tools;

import java.io.IOException;
import java.io.PrintStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Base64;
import java.util.List;
import java.util.Map;

/**
 * Why the local JCR the lineage demo starts is not answering: the image's architecture, Docker's memory, the
 * container's state and memory use, the errors in its own logs, where it can be reached from, and what its Docker API
 * replies. Prints no secrets. {@code jcrDiagnose}; settings {@code JCR_PORT} (8082), {@code JCR_IMAGE},
 * {@code ADMIN_PASSWORD} ({@code password}).
 */
public final class JcrDiagnose {

    static final String LOGS = "/var/opt/jfrog/artifactory/log";
    static final List<String> DOCKER_API = List.of("v2/", "artifactory/api/docker/docker-local/v2/",
            "artifactory/api/docker/docker-local/v2/token", "artifactory/api/docker/docker-local/v2/_catalog",
            "artifactory/api/repositories");

    private final Commands docker;
    private final URI root;
    private final String admin;
    private final HttpClient client;
    private final String image;
    private final PrintStream out;

    JcrDiagnose(Commands docker, URI root, String password, HttpClient client, String image, PrintStream out) {
        this.docker = docker;
        this.root = root;
        this.admin = "Basic " + Base64.getEncoder().encodeToString(("admin:" + password)
                .getBytes(StandardCharsets.UTF_8));
        this.client = client;
        this.image = image;
        this.out = out;
    }

    /**
     * Diagnoses the JCR on {@code JCR_PORT}.
     *
     * @param args none
     */
    public static void main(String[] args) {
        Map<String, String> env = System.getenv();
        new JcrDiagnose(Commands.processes(), URI.create("http://127.0.0.1:" + env.getOrDefault("JCR_PORT", "8082")
                + "/"), env.getOrDefault("ADMIN_PASSWORD", "password"), HttpClient.newHttpClient(),
                env.getOrDefault("JCR_IMAGE", LineageDemo.JCR_IMAGE), System.out).run();
    }

    void run() {
        containerSections();
        networkSections();
        dockerApi();
    }

    void containerSections() {
        String jcr = JcrContainers.JCR;
        section("image architecture (arm64 runs natively on Apple silicon; amd64 is emulated)",
                List.of("docker", "image", "inspect", image, "--format", "{{.Architecture}}"));
        section("memory Docker may use, in bytes (Artifactory wants 4 GB or more)",
                List.of("docker", "info", "--format", "{{.MemTotal}}"));
        section("container state", List.of("docker", "ps", "-a", "--filter", "name=^" + jcr + "$", "--format",
                "{{.Status}}"), List.of("docker", "stats", "--no-stream", "--format",
                "{{.MemUsage}}  cpu {{.CPUPerc}}", jcr));
        section("log files", List.of("docker", "exec", jcr, "ls", LOGS));
        section("access-service.log, last 40 lines", List.of("docker", "exec", jcr, "tail", "-40",
                LOGS + "/access-service.log"));
        section("errors in the service logs, last 20 (start-up retries left out)", List.of("docker", "exec", jcr, "sh",
                "-c", "grep -hiE 'error|exception|fatal' " + LOGS + "/*-service.log | grep -v 'Cluster join' "
                        + "| tail -20"));
    }

    void networkSections() {
        String jcr = JcrContainers.JCR;
        String ping = "/artifactory/api/system/ping";
        section("reachability from the demo network and inside JCR", List.of("docker", "run", "--rm", "--network",
                JcrContainers.NETWORK, "curlimages/curl:8.10.1", "-s", "-m", "5", "-o", "/dev/null", "-w",
                "%{http_code}\n", "http://" + jcr + ":8082" + ping), List.of("docker", "exec", jcr, "curl", "-s", "-m",
                "5", "-o", "/dev/null", "-w", "%{http_code}\n", "http://localhost:8082" + ping));
        section("addresses JCR listens on inside its container (0.0.0.0 or :: means every interface)",
                List.of("docker", "exec", jcr, "sh", "-c", "(command -v ss >/dev/null && ss -ltn || cat /proc/net/tcp "
                        + "/proc/net/tcp6) | grep -E ':8082|:1F92' || true"));
        section("configuration import (artifactory.config.import.yml): files left, and what the log says",
                List.of("docker", "exec", jcr, "ls", "/var/opt/jfrog/artifactory/etc/artifactory"),
                List.of("docker", "exec", jcr, "sh", "-c", "grep -hiE 'config.import|import.*config|yaml' " + LOGS
                        + "/artifactory-service.log | tail -15"));
    }

    void dockerApi() {
        out.println("\n== Docker API from this machine: status and the start of each reply (admin login)");
        DOCKER_API.forEach(path -> out.printf("%-52s %s%n", "/" + path, probe(path)));
    }

    @SafeVarargs
    final void section(String title, List<String>... commands) {
        out.println("\n== " + title);
        for (List<String> command : commands) {
            out.print(docker.run(command, null).out());
        }
    }

    String probe(String path) {
        try {
            HttpResponse<String> reply = client.send(HttpRequest.newBuilder(root.resolve(path))
                    .timeout(Duration.ofSeconds(10)).header("Authorization", admin).build(),
                    HttpResponse.BodyHandlers.ofString());
            return reply.statusCode() + " " + reply.headers().firstValue("WWW-Authenticate").map(v -> v + " ")
                    .orElse("") + redacted(reply.body());
        } catch (IOException e) {
            return "no answer: " + e.getMessage();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return "interrupted";
        }
    }

    static String redacted(String body) {
        String hidden = body.replaceAll("\"(token|access_token)\"\\s*:\\s*\"[^\"]*\"", "\"$1\":\"(hidden)\"")
                .replace('\n', ' ');
        return hidden.length() > 200 ? hidden.substring(0, 200) : hidden;
    }
}
