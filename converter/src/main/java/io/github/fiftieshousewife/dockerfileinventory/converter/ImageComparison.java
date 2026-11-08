package io.github.fiftieshousewife.dockerfileinventory.converter;

import java.util.List;
import java.util.SortedMap;
import tools.jackson.databind.JsonNode;

final class ImageComparison {

    record Result(String blobSha, boolean built, String failure, boolean same, List<String> config,
            List<String> files, List<String> stubs) {

        Result {
            config = List.copyOf(config);
            files = List.copyOf(files);
            stubs = List.copyOf(stubs);
        }
    }

    private ImageComparison() {
    }

    static Result notBuilt(String sha, String failure) {
        return new Result(sha, false, failure, false, List.of(), List.of(), List.of());
    }

    static Result compared(String sha, JsonNode originalInspect, JsonNode regeneratedInspect,
            SortedMap<String, FileEntry> originalFiles, SortedMap<String, FileEntry> regeneratedFiles,
            List<String> stubs) {
        List<String> config = ConfigDifferences.of(config(originalInspect), config(regeneratedInspect));
        List<String> files = FileDifferences.of(originalFiles, regeneratedFiles);
        return new Result(sha, true, null, config.isEmpty() && files.isEmpty(), config, files, stubs);
    }

    // Docker's config keeps HEALTHCHECK, ONBUILD and SHELL, which the OCI one has no place for.
    static JsonNode config(JsonNode inspected) {
        JsonNode docker = inspected.path("Docker").path("config");
        return docker.isMissingNode() ? inspected.path("OCIv1").path("config") : docker;
    }
}
