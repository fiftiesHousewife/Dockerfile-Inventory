package io.github.fiftieshousewife.dockerfileinventory.report;

import java.util.Map;

final class About {

    private About() {
    }

    static final Map<String, String> TEXT = Map.ofEntries(
            Map.entry(Health.PARSE_FAILURES, "Share of distinct Dockerfiles the parser could not read, templates "
                    + "left out. Good at 1% or less; act above 5%."),
            Map.entry(Health.ROOT, "Share of locations whose image ends running as root: the effective user where "
                    + "lineage linked a built image, the user written in the Dockerfile otherwise. Metadata-only "
                    + "bundles are left out. Good at 10% or less; act above 30%."),
            Map.entry(Health.PINNED, "Share of locations whose base image is pinned by digest (@sha256:...), so a "
                    + "rebuild gets exactly the same base; scratch, the empty base, and bundles are left out. Good at "
                    + "80% or more; act below 20%."),
            Map.entry(Health.EOL, "Share of locations built on a base the end-of-life list marks as end of life. "
                    + "Good at 0%; act above 5%. No data without EOL_LIST."),
            Map.entry(Health.RELEASED, "Share of locations read at the commit the release index says was released, "
                    + "not the default branch. Good at 90% or more; act below 50%. No data without a release index."),
            Map.entry(Health.OPAQUE, "Share of RUN instructions whose shell script the inventory could not follow "
                    + "(if, for, functions, subshells, heredocs). Their packages are not counted and the converter "
                    + "keeps them as raw shell. Good at 5% or less; act above 15%."),
            Map.entry("How alike", "Counting the same Dockerfiles four ways, each looser than the last: byte for "
                    + "byte; ignoring comments and spacing; by the kind of image they build (base, packages, user, "
                    + "ports); and by archetype. The drop from the first number to the last shows how much of the "
                    + "estate is copies and variations of a few designs."),
            Map.entry("Archetypes", "An archetype is a family of Dockerfiles that build essentially the same image "
                    + "(same base, nearly the same packages, user and ports) even when their text differs. Few "
                    + "archetypes covering many Dockerfiles means a few maintained templates could replace them."),
            Map.entry("Organisations compared", "Each organisation's own figures: the first part of each "
                    + "repository's name. Root and pinned leave metadata-only bundles out; the largest repository's "
                    + "share shows when one repository makes most of an organisation."),
            Map.entry("Applies to", "Where an organisation stands apart: a rate on the other side of a health "
                    + "threshold from the estate, or more than 20 points from it; bundles; one repository holding "
                    + "most locations; base families no other organisation uses."),
            Map.entry("Top archetypes", "The largest families of near-identical Dockerfiles, by locations, named "
                    + "by base family, package managers and user. Bundles, templates, unreadable files and bases "
                    + "set by a variable are counted apart. The full ranking is in archetypes.json."),
            Map.entry("Top bases", "The base families Dockerfiles build their final stage FROM, by locations, with "
                    + "how many organisations use each: a family used by many is a candidate to standardise on."),
            Map.entry("Package managers", "Locations whose RUN instructions call each package manager."),
            Map.entry("Final user", "The user the image ends running as: root, a numeric UID, a name, or a "
                    + "variable that could not be resolved."),
            Map.entry("Lineage", "Images in Artifactory, each linked to its parent image and to the Dockerfile it "
                    + "was built from."),
            Map.entry("Downloads", "The data behind these pages, for loading into a database or a spreadsheet."));
}
