package io.github.fiftieshousewife.dockerfileinventory.lineage;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * What lineage knows of one Dockerfile, from {@code facts.jsonl} and {@code manifest.jsonl}: its steps, its base's
 * image path, whether any location of it is at a released commit, and the repositories that hold it (a source label
 * on an image names one).
 */
public record Dockerfile(String blobSha, List<Instruction> steps, String fromPath, boolean released,
                         Set<String> repositories) {

    public Dockerfile {
        steps = List.copyOf(steps);
        repositories = Set.copyOf(repositories);
    }

    public Dockerfile(String blobSha, List<Instruction> steps, String fromPath, boolean released) {
        this(blobSha, steps, fromPath, released, Set.of());
    }

    /** The same Dockerfile, held by one more repository. */
    Dockerfile in(String repository) {
        Set<String> more = new HashSet<>(repositories);
        more.add(repository);
        return new Dockerfile(blobSha, steps, fromPath, released, more);
    }
}
