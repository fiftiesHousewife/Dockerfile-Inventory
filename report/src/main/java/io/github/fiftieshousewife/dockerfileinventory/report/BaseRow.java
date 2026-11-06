package io.github.fiftieshousewife.dockerfileinventory.report;

import java.time.LocalDate;

/**
 * One base in the de facto catalogue: image path (no registry host) and tag, the locations built on it, how many
 * of those pin it by digest, and its status from the internal end-of-life list.
 */
public record BaseRow(String path, String tag, String family, int locations, int pinnedLocations, String status,
                      LocalDate eolDate) {
}
