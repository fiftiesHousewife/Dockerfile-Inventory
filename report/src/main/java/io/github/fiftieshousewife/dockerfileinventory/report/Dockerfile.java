package io.github.fiftieshousewife.dockerfileinventory.report;

import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;

/** One distinct Dockerfile (blob) and what the report needs of its facts. */
public record Dockerfile(String blobSha, String textHash, String signatureHash, String signature, Base base,
                         Set<String> finalPackages, Set<String> managers, String userKind, int runs, int opaqueRuns,
                         List<Location> locations, int otherShellRuns) {

    public Dockerfile {
        finalPackages = Collections.unmodifiableSortedSet(new TreeSet<>(finalPackages));
        managers = Collections.unmodifiableSortedSet(new TreeSet<>(managers));
        locations = List.copyOf(locations);
    }
}
