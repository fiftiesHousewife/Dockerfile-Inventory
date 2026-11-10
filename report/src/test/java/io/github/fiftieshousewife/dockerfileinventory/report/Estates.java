package io.github.fiftieshousewife.dockerfileinventory.report;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Set;

final class Estates {

    static final Estate ESTATE = new Estate(List.of(
            file("a1", new Base("registry.example.com/ubi9/ubi-minimal", "9.4", "sha256:a"), "NUMERIC", false,
                    "acme/api", "acme/api", "acme/web"),
            file("a2", new Base("scratch", null, null), "ROOT", true,
                    "acme/bundles", "acme/bundles", "acme/bundles", "acme/bundles"),
            file("b1", new Base("debian", "12", null), "ROOT", false, "beta/x")),
            List.of(new Failure("t1", Tallies.TEMPLATE + " (jinja)", List.of(at("beta/y"))),
                    new Failure("f1", "Syntax error", List.of(at("acme/z")))),
            List.of(), Map.of());

    private Estates() {
    }

    static Dockerfile file(String sha, Base base, String user, boolean bundle, String... repositories) {
        boolean ubi = base.name().contains("ubi");
        return new Dockerfile(sha, "t-" + sha, "s-" + sha, "sig-" + sha, base, Set.of(),
                ubi ? Set.of("microdnf") : Set.of(), user, ubi ? 2 : 0, ubi ? 1 : 0,
                Arrays.stream(repositories).map(Estates::at).toList(), 0, bundle);
    }

    static Location at(String repository) {
        return new Location(repository, "Dockerfile", repository.equals("beta/x") ? "RELEASED" : "DEFAULT_BRANCH");
    }
}
