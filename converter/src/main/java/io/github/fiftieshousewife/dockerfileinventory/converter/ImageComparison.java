package io.github.fiftieshousewife.dockerfileinventory.converter;

import java.util.List;
import java.util.Optional;
import java.util.SortedMap;
import java.util.stream.Stream;
import tools.jackson.databind.JsonNode;

final class ImageComparison {

    record Result(String blobSha, boolean built, String failure, boolean same, List<String> config,
            List<String> files, List<String> stubs, boolean controlled, List<String> noise) {

        Result {
            config = List.copyOf(config);
            files = List.copyOf(files);
            stubs = List.copyOf(stubs);
            noise = List.copyOf(noise);
        }
    }

    record Control(JsonNode inspect, SortedMap<String, FileEntry> files) {
    }

    private ImageComparison() {
    }

    static Result notBuilt(String sha, String failure) {
        return new Result(sha, false, failure, false, List.of(), List.of(), List.of(), false, List.of());
    }

    static Result compared(String sha, JsonNode originalInspect, JsonNode regeneratedInspect,
            SortedMap<String, FileEntry> originalFiles, SortedMap<String, FileEntry> regeneratedFiles,
            List<String> stubs, Optional<Control> control) {
        List<String> config = ConfigDifferences.of(config(originalInspect), config(regeneratedInspect));
        List<String> files = FileDifferences.of(originalFiles, regeneratedFiles);
        List<String> noise = control.map(c -> Stream.concat(
                ConfigDifferences.of(config(originalInspect), config(c.inspect())).stream(),
                FileDifferences.of(originalFiles, c.files()).stream()).toList()).orElse(List.of());
        Noise filter = Noise.of(noise);
        List<String> realConfig = filter.removedFrom(config);
        List<String> realFiles = filter.removedFrom(files);
        List<String> setAside = Stream.concat(config.stream(), files.stream())
                .filter(difference -> !realConfig.contains(difference) && !realFiles.contains(difference)).toList();
        return new Result(sha, true, null, realConfig.isEmpty() && realFiles.isEmpty(), realConfig, realFiles, stubs,
                control.isPresent(), setAside);
    }

    // Docker's config keeps HEALTHCHECK, ONBUILD and SHELL, which the OCI one has no place for.
    static JsonNode config(JsonNode inspected) {
        JsonNode docker = inspected.path("Docker").path("config");
        return docker.isMissingNode() ? inspected.path("OCIv1").path("config") : docker;
    }
}
