package io.github.fiftieshousewife.dockerfileinventory.report;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

final class Footprint {

    static final String UNKNOWN = "unknown";
    private static final Set<String> FULL = Set.of("ubi", "ubi8", "ubi9", "ubi10", "debian", "ubuntu", "fedora",
            "centos", "rockylinux", "almalinux");

    record Found(String footprint, String base, List<String> through) {

        Found {
            through = List.copyOf(through);
        }
    }

    private Footprint() {
    }

    static Found through(String sha, Map<String, CatalogueEntry> bySha, Map<String, CatalogueLinks.Links> links) {
        List<String> through = new ArrayList<>();
        String current = sha;
        for (List<CatalogueLinks.Link> up = parents(links, current); !up.isEmpty()
                && bySha.containsKey(up.getFirst().blobSha()) && !through.contains(up.getFirst().blobSha())
                && !up.getFirst().blobSha().equals(sha); up = parents(links, current)) {
            current = up.getFirst().blobSha();
            through.add(current);
        }
        String base = CatalogueLinks.finalBase(bySha.get(current));
        return new Found(base == null ? UNKNOWN : of(base), base, through);
    }

    private static List<CatalogueLinks.Link> parents(Map<String, CatalogueLinks.Links> links, String sha) {
        CatalogueLinks.Links found = links.get(sha);
        return found == null ? List.of() : found.parents();
    }

    static String of(String image) {
        String path = CatalogueLinks.path(image);
        String name = path.substring(path.lastIndexOf('/') + 1);
        String tag = image.matches(".*:[^/]*$") ? image.substring(image.lastIndexOf(':') + 1) : "";
        if (name.equals("scratch")) {
            return "scratch";
        }
        if (path.contains("distroless")) {
            return "distroless";
        }
        if (name.endsWith("micro")) {
            return "micro";
        }
        if (name.contains("minimal") || name.equals("busybox")) {
            return "minimal";
        }
        if (name.equals("alpine") || name.endsWith("-alpine")) {
            return "alpine";
        }
        if (name.contains("slim") || tag.contains("slim")) {
            return "slim";
        }
        return FULL.contains(name) ? "full" : UNKNOWN;
    }
}
