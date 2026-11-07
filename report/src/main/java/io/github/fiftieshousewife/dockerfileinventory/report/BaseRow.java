package io.github.fiftieshousewife.dockerfileinventory.report;

import java.time.LocalDate;

record BaseRow(String path, String tag, String family, int locations, int pinnedLocations, String status,
                      LocalDate eolDate) {
}
