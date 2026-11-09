package io.github.fiftieshousewife.dockerfileinventory.tools;

import java.io.IOException;
import java.net.ServerSocket;
import java.util.List;

// PostgreSQL and the JFrog Container Registry in containers on the demo's network. JCR 7.19x refuses its old
// built-in Derby, so it runs on PostgreSQL; the database password is a fixed local one, reachable only on the network.
final class JcrContainers {

    static final String NETWORK = "inventory-demo";
    static final String JCR = "inventory-jcr";
    static final String POSTGRES = "inventory-postgres";
    static final String WIZARD = "/var/opt/jfrog/artifactory/etc/artifactory/artifactory.config.import.yml";

    private final Commands docker;
    private final String jcrImage;
    private final String postgresImage;
    private final int port;

    JcrContainers(Commands docker, String jcrImage, String postgresImage, int port) {
        this.docker = docker;
        this.jcrImage = jcrImage;
        this.postgresImage = postgresImage;
        this.port = port;
    }

    void network() {
        if (!docker.run("docker", "network", "inspect", NETWORK).ok()) {
            docker.run("docker", "network", "create", NETWORK);
        }
    }

    void postgres() {
        if (running(POSTGRES)) {
            return;
        }
        docker.run("docker", "rm", "-f", POSTGRES);
        docker.run(List.of("docker", "run", "-d", "--name", POSTGRES, "--network", NETWORK, "-v", POSTGRES
                + ":/var/lib/postgresql/data", "-e", "POSTGRES_USER=artifactory", "-e", "POSTGRES_PASSWORD=artifactory",
                "-e", "POSTGRES_DB=artifactory", postgresImage), null);
    }

    boolean postgresReady() {
        return docker.run("docker", "exec", POSTGRES, "pg_isready", "-q", "-U", "artifactory", "-d", "artifactory")
                .ok();
    }

    // A JCR of another version cannot open this one's database: removing its volumes is left to the person.
    void jcr() {
        String running = docker.run("docker", "inspect", "-f", "{{.Config.Image}}", JCR).out().strip();
        if (!running.isEmpty() && !running.equals(jcrImage) && !running.startsWith("Error")) {
            throw new IllegalStateException("JCR runs " + running + ", not " + jcrImage + ". To start again on "
                    + "empty data: docker rm -f " + JCR + " " + POSTGRES + "; docker volume rm " + JCR + " "
                    + POSTGRES);
        }
        if (running(JCR) && !docker.run("docker", "port", JCR, "8082/tcp").out().strip().endsWith(":" + port)) {
            docker.run("docker", "rm", "-f", JCR);
        }
        if (running(JCR)) {
            return;
        }
        if (taken(port)) {
            throw new IllegalStateException("port " + port + " is already taken; free it, or choose another: "
                    + "JCR_PORT=18082");
        }
        docker.run("docker", "rm", "-f", JCR);
        docker.run(List.of("docker", "run", "-d", "--name", JCR, "--network", NETWORK, "-p", port + ":8082",
                "-v", JCR + ":/var/opt/jfrog/artifactory", "-e", "JF_SHARED_DATABASE_TYPE=postgresql",
                "-e", "JF_SHARED_DATABASE_DRIVER=org.postgresql.Driver",
                "-e", "JF_SHARED_DATABASE_URL=jdbc:postgresql://" + POSTGRES + ":5432/artifactory",
                "-e", "JF_SHARED_DATABASE_USERNAME=artifactory", "-e", "JF_SHARED_DATABASE_PASSWORD=artifactory",
                jcrImage), null);
    }

    // The free edition refuses the repositories API, but its setup wizard reads this file at start.
    void askWizardForDocker() {
        docker.run(List.of("docker", "exec", "-i", JCR, "sh", "-c", "cat > " + WIZARD),
                "version: 1\nOnboardingConfiguration:\n  repoTypes:\n    - docker\n");
        docker.run("docker", "restart", JCR);
    }

    int memoryGb() {
        String bytes = docker.run("docker", "info", "--format", "{{.MemTotal}}").out().strip();
        return bytes.matches("\\d+") ? (int) Math.round(Long.parseLong(bytes) / 1024.0 / 1024 / 1024) : 0;
    }

    private boolean running(String name) {
        return !docker.run("docker", "ps", "-q", "-f", "name=^" + name + "$").out().isBlank();
    }

    static boolean taken(int port) {
        try (ServerSocket socket = new ServerSocket(port)) {
            return socket.getLocalPort() != port;
        } catch (IOException inUse) {
            return true;
        }
    }
}
