package io.github.fiftieshousewife.dockerfileinventory.report;

import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;

record Dockerfile(String blobSha, String textHash, String signatureHash, String signature, Base base,
                         Set<String> finalPackages, Set<String> managers, String userKind, int runs, int opaqueRuns,
                         List<Location> locations, int otherShellRuns, boolean bundle) {

    Dockerfile {
        finalPackages = Collections.unmodifiableSortedSet(new TreeSet<>(finalPackages));
        managers = Collections.unmodifiableSortedSet(new TreeSet<>(managers));
        locations = List.copyOf(locations);
    }

    Dockerfile at(List<Location> elsewhere) {
        return new Dockerfile(blobSha, textHash, signatureHash, signature, base, finalPackages, managers, userKind, runs,
                opaqueRuns, elsewhere, otherShellRuns, bundle);
    }
}
