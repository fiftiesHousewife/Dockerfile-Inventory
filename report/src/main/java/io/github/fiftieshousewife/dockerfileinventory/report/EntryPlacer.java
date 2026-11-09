package io.github.fiftieshousewife.dockerfileinventory.report;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Set;

final class EntryPlacer {

    private final Map<String, CatalogueLinks.Links> links;
    private final Map<String, CatalogueEntry> bySha;
    private final Hierarchies hierarchies;
    private final Set<String> archived;
    private final Map<String, Conversions.Conversion> conversions;
    private final Map<String, Siblings.Found> siblings;
    private final BaseLags baseLags;
    private final FootprintRules footprints;

    EntryPlacer(List<CatalogueEntry> parsed, CatalogueRun.Inputs inputs, CatalogueSettings settings) {
        Path lineageDir = inputs.lineageDir();
        this.links = CatalogueLinks.of(parsed, lineageDir);
        this.bySha = CatalogueEntries.bySha(parsed);
        this.hierarchies = hierarchies(lineageDir, settings);
        this.archived = Unmaintained.archivedRepositories(inputs.discoveryDir());
        this.conversions = Conversions.read(inputs.converterDir());
        this.siblings = Siblings.of(inputs.nearDuplicates());
        this.baseLags = new BaseLags(lineageDir == null ? Map.of() : RegistryImages.tagsByImage(lineageDir));
        this.footprints = settings.footprints();
    }

    static Hierarchies hierarchies(Path lineageDir, CatalogueSettings settings) {
        return lineageDir == null
                ? new Hierarchies(Map.of(), settings.staleAfterMonths(), settings.layerLimit(), Map.of())
                : new Hierarchies(RegistryImages.read(lineageDir), settings.staleAfterMonths(), settings.layerLimit(),
                        RegistryImages.pushesByName(lineageDir));
    }

    CatalogueEntry placed(CatalogueEntry entry) {
        Footprint.Found footprint = Footprint.through(entry.blobSha(), bySha, links, footprints);
        return entry.placed(new Placement(links.get(entry.blobSha()), footprint,
                Runtimes.inherited(footprint.through(), bySha),
                hierarchies.of(entry, bySha, footprint.through(), archived), actions(entry)));
    }

    Actions actions(CatalogueEntry entry) {
        return new Actions(ReleaseScope.of(entry.locations()),
                Reaches.of(entry.blobSha(), bySha, links, hierarchies.imagesBelow(entry.blobSha())),
                Conversions.of(conversions, entry.blobSha()), Siblings.of(siblings, entry.blobSha()),
                baseLag(entry));
    }

    BaseLags.BaseLag baseLag(CatalogueEntry entry) {
        CatalogueEntry.StageEntry base = CatalogueLinks.finalBaseStage(entry);
        return base == null || base.baseTag() == null ? null
                : baseLags.of(base.baseName() + ":" + base.baseTag()).orElse(null);
    }
}
