package io.github.fiftieshousewife.dockerfileinventory.report;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

// Field names are the catalogue's published schema.
record CatalogueEntry(String blobSha, List<Place> locations, boolean bundle, Image image, Build build,
                      CatalogueLinks.Links links, Footprint.Found footprint, Hierarchies.Hierarchy hierarchy,
                      Actions actions, String parseError) {

    record Place(String repository, String path, String scope, String commit, String lastChanged,
                 String lastChangedIn) {
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
                 int runs, int opaqueRuns, Map<String, Integer> instructions, List<Runtimes.Found> runtimes) {

        Build {
            stages = List.copyOf(stages);
            packages = List.copyOf(packages);
            buildKitFeatures = List.copyOf(buildKitFeatures);
            instructions = Collections.unmodifiableMap(new LinkedHashMap<>(instructions));
            runtimes = List.copyOf(runtimes);
        }
    }

    CatalogueEntry {
        locations = List.copyOf(locations);
    }

    CatalogueEntry placed(Placement placement) {
        List<Runtimes.Found> runtimes = new ArrayList<>(build.runtimes());
        runtimes.addAll(placement.inheritedRuntimes());
        Build placedBuild = new Build(build.stages(), build.packages(), build.buildKitFeatures(), build.layers(),
                build.runs(), build.opaqueRuns(), build.instructions(), runtimes);
        return new CatalogueEntry(blobSha, locations, bundle, image, placedBuild, placement.links(),
                placement.footprint(), placement.hierarchy(), placement.actions(), parseError);
    }
}
