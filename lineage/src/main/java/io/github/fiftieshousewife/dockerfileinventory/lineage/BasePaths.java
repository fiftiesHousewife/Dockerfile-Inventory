package io.github.fiftieshousewife.dockerfileinventory.lineage;

import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.stream.IntStream;

final class BasePaths {

    private BasePaths() {
    }

    static String basePath(Resolved.Node image) {
        List<HistoryStep> history = image.config().history();
        return IntStream.iterate(history.size() - 1, i -> i >= 0, i -> i - 1)
                .mapToObj(i -> HistoryNormaliser.normalise(history.get(i)).from())
                .filter(Objects::nonNull)
                .findFirst()
                .map(ImagePaths::path)
                .orElse(null);
    }

    static List<Dockerfile> on(String basePath, List<Dockerfile> dockerfiles) {
        return dockerfiles.stream().filter(d -> d.fromPath() != null && samePath(d.fromPath(), basePath)).toList();
    }

    // Nothing is deleted from Artifactory, so a base it holds should have been found as the image's parent.
    static boolean heldByArtifactory(Dockerfile dockerfile, Set<String> imageNames) {
        String path = dockerfile.fromPath();
        return path != null && (imageNames.contains(path) || imageNames.contains(withoutFirstSegment(path)));
    }

    // A Dockerfile may name an Artifactory image with or without the repository key as its first segment.
    static boolean samePath(String a, String b) {
        return a.equals(b) || withoutFirstSegment(a).equals(b) || a.equals(withoutFirstSegment(b));
    }

    private static String withoutFirstSegment(String path) {
        return path.substring(path.indexOf('/') + 1);
    }
}
