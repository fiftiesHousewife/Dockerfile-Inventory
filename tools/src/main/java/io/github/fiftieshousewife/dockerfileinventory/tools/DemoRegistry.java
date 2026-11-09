package io.github.fiftieshousewife.dockerfileinventory.tools;

import java.util.Map;

final class DemoRegistry {

    static final String REPOSITORY = "docker-local";

    record Ready(String repository, String token) {
    }

    private final JcrApi jcr;
    private final JcrContainers containers;
    private final Waiter waiter;
    private final Map<String, String> env;

    DemoRegistry(JcrApi jcr, JcrContainers containers, Waiter waiter, Map<String, String> env) {
        this.jcr = jcr;
        this.containers = containers;
        this.waiter = waiter;
        this.env = env;
    }

    Ready start() {
        requireMemory();
        startContainers();
        requireAdmin();
        String repository = repository();
        acceptAgreement(repository);
        waiter.until("the Docker API", () -> jcr.dockerApiReady(repository));
        return new Ready(repository, readToken());
    }

    void requireMemory() {
        int memory = containers.memoryGb();
        if (memory < 4) {
            throw new IllegalStateException("Docker may use " + memory + " GB; JCR needs 4 or more (Docker Desktop > "
                    + "Settings > Resources > Memory)");
        }
    }

    private void startContainers() {
        containers.network();
        containers.postgres();
        waiter.until("PostgreSQL", containers::postgresReady);
        containers.jcr();
        waiter.until("JCR (a first start takes a few minutes; jcrDiagnose says why not)", jcr::answers);
    }

    void requireAdmin() {
        if (!jcr.adminAccepted()) {
            throw new IllegalStateException("admin login refused; if you changed the admin password in the UI, set "
                    + "ADMIN_PASSWORD");
        }
    }

    String repository() {
        if (jcr.repository(REPOSITORY).isEmpty()) {
            System.out.println("creating " + REPOSITORY + " through the API: " + jcr.create(REPOSITORY));
        }
        if (jcr.repository(REPOSITORY).isEmpty()) {
            containers.askWizardForDocker();
            waiter.until("JCR after its restart", jcr::answers);
        }
        return jcr.repository(REPOSITORY).orElseThrow(() -> new IllegalStateException("JCR would not create the "
                + "repository; in its UI: Administration > Repositories > Create a Repository > Local > Docker, key '"
                + REPOSITORY + "', then rerun"));
    }

    void acceptAgreement(String repository) {
        if (!jcr.agreementPending(repository)) {
            return;
        }
        if (!"yes".equals(env.get("ACCEPT_JCR_EULA"))) {
            throw new IllegalStateException("JCR's Docker API stays off until JFrog's JCR licence agreement is "
                    + "accepted. Read it at the UI's first login, then accept it there or rerun with "
                    + "ACCEPT_JCR_EULA=yes to accept it through the API");
        }
        jcr.acceptAgreement();
    }

    String readToken() {
        String given = env.getOrDefault("ARTIFACTORY_TOKEN", "");
        String token = given.isEmpty() ? jcr.token() : given;
        if (!jcr.canRead(token)) {
            throw new IllegalStateException("ARTIFACTORY_TOKEN cannot list JCR's repositories");
        }
        return token;
    }
}
