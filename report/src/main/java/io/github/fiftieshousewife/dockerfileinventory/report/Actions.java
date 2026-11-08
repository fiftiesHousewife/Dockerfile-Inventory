package io.github.fiftieshousewife.dockerfileinventory.report;

record Actions(String releaseScope, Reaches.Reach reach, Conversions.Conversion conversion,
               Siblings.Found nearDuplicates, BaseLags.BaseLag baseLag) {
}
