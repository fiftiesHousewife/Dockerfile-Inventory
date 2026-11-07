package io.github.fiftieshousewife.dockerfileinventory.lineage;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

final class SourceLabels {

    private static final List<String> LABELS =
            List.of("io.openshift.build.source-location", "org.opencontainers.image.source");

    private SourceLabels() {
    }

    static Optional<String> repository(Map<String, String> labels) {
        return LABELS.stream().map(labels::get).filter(Objects::nonNull).findFirst().flatMap(SourceLabels::fromUrl);
    }

    static Optional<String> fromUrl(String url) {
        try {
            String path = new URI(url.strip()).getPath();
            String[] segments = path == null ? new String[0] : path.replaceFirst("^/", "").split("/");
            return segments.length < 2 || segments[0].isEmpty() ? Optional.empty()
                    : Optional.of(segments[0] + "/" + segments[1].replaceFirst("\\.git$", ""));
        } catch (URISyntaxException e) {
            return Optional.empty();
        }
    }
}
