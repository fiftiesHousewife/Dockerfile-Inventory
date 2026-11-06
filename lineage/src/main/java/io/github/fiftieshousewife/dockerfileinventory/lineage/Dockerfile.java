package io.github.fiftieshousewife.dockerfileinventory.lineage;

import java.util.List;

/**
 * What lineage knows of one Dockerfile, from {@code facts.jsonl} and {@code manifest.jsonl}: its steps, its base's
 * image path, and whether any location of it is at a released commit.
 */
public record Dockerfile(String blobSha, List<Instruction> steps, String fromPath, boolean released) {

    public Dockerfile {
        steps = List.copyOf(steps);
    }
}
