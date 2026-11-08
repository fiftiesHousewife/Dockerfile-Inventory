package io.github.fiftieshousewife.dockerfileinventory.report;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

// One distinct Dockerfile as the catalogue describes it; its field names are the catalogue's published schema.
record CatalogueEntry(String blobSha, List<Place> locations, boolean bundle, Image image, Build build,
                      CatalogueLinks.Links links, String parseError) {

    record Place(String repository, String path, String scope, String commit) {
    }

    record Command(String form, List<String> arguments) {

        Command {
            arguments = List.copyOf(arguments);
        }
    }

    record Image(String user, String userKind, List<String> ports, Command entrypoint, Command cmd, String workdir,
                 List<String> volumes, String healthcheck, List<String> shell, String stopSignal,
                 Map<String, String> environment, Map<String, String> labels) {

        Image {
            ports = List.copyOf(ports);
            volumes = List.copyOf(volumes);
            shell = List.copyOf(shell);
            environment = Collections.unmodifiableMap(new LinkedHashMap<>(environment));
            labels = Collections.unmodifiableMap(new LinkedHashMap<>(labels));
        }
    }

    record StageEntry(int index, String name, String baseName, String baseTag, String baseDigest, boolean pinned,
                      String fromStage, List<String> copiesFrom, int instructions, int runs, int commands,
                      int unreadRuns, int layers, List<Copy> copies, List<Permission> permissions,
                      List<String> unusual) {

        StageEntry {
            copiesFrom = List.copyOf(copiesFrom);
            copies = List.copyOf(copies);
            permissions = List.copyOf(permissions);
            unusual = List.copyOf(unusual);
        }
    }

    record Copy(String instruction, List<String> sources, String destination, String origin, String from,
                String chown, String chmod) {

        Copy {
            sources = List.copyOf(sources);
        }
    }

    record Permission(String command, String setting, List<String> paths, boolean recursive) {

        Permission {
            paths = List.copyOf(paths);
        }
    }

    record PackageEntry(int stage, String manager, String name, boolean versioned, boolean cleaned) {
    }

    record Build(List<StageEntry> stages, List<PackageEntry> packages, List<String> buildKitFeatures, int layers,
                 int runs, int opaqueRuns, Map<String, Integer> instructions) {

        Build {
            stages = List.copyOf(stages);
            packages = List.copyOf(packages);
            buildKitFeatures = List.copyOf(buildKitFeatures);
            instructions = Collections.unmodifiableMap(new LinkedHashMap<>(instructions));
        }
    }

    CatalogueEntry {
        locations = List.copyOf(locations);
    }

    CatalogueEntry withLinks(CatalogueLinks.Links found) {
        return new CatalogueEntry(blobSha, locations, bundle, image, build, found, parseError);
    }
}
