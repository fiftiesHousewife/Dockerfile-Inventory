package io.github.fiftieshousewife.dockerfileinventory.lineage;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

record Dockerfile(String blobSha, List<Instruction> steps, String fromPath, boolean released,
                  Set<String> repositories) {

    Dockerfile {
        steps = List.copyOf(steps);
        repositories = Set.copyOf(repositories);
    }

    Dockerfile(String blobSha, List<Instruction> steps, String fromPath, boolean released) {
        this(blobSha, steps, fromPath, released, Set.of());
    }

    Dockerfile in(String repository) {
        Set<String> more = new HashSet<>(repositories);
        more.add(repository);
        return new Dockerfile(blobSha, steps, fromPath, released, more);
    }
}
