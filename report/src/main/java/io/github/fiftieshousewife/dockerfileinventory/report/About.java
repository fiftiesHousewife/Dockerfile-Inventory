package io.github.fiftieshousewife.dockerfileinventory.report;

import java.util.Map;

/** What each number and section on the dashboard means, shown behind its info marker. */
final class About {

    private About() {
    }

    static final Map<String, String> TEXT = Map.ofEntries(
            Map.entry("Parse failures", "Share of distinct Dockerfiles the parser could not read, templates left out. "
                    + "Good at 1% or less; act above 5%."),
            Map.entry("Final user root", "Share of locations whose image ends running as root: the effective user "
                    + "where lineage linked a built image, the user written in the Dockerfile otherwise. Good at 10% "
                    + "or less; act above 30%."),
            Map.entry("Bases pinned by digest", "Share of locations whose base image is pinned by digest "
                    + "(@sha256:...), so a rebuild gets exactly the same base; scratch, the empty base, has no digest and is "
                    + "left out. Good at 80% or more; act below 20%."),
            Map.entry("Bases at end of life", "Share of locations built on a base the end-of-life list marks as end "
                    + "of life. Good at 0%; act above 5%. Needs EOL_LIST."),
            Map.entry("Scanned at a released commit", "Share of locations read at the commit the release index says "
                    + "was released, not the default branch. Good at 90% or more; act below 50%. Needs the release "
                    + "index."),
            Map.entry("RUN too complex to read", "Share of RUN instructions whose shell script the inventory could "
                    + "not follow (if, for, functions, subshells, heredocs). Their packages are not counted and the "
                    + "converter keeps them as raw shell. Nothing breaks; the inventory is less complete. Good at 5% "
                    + "or less; act above 15%."),
            Map.entry("Dockerfile locations", "Each path in each repository that holds a Dockerfile: the same file "
                    + "in two places counts twice."),
            Map.entry("Unique files", "Distinct Dockerfile contents, by git blob SHA."),
            Map.entry("Unique after normalising text", "Distinct once comments, blank lines and whitespace "
                    + "differences are dropped."),
            Map.entry("Unique signatures", "Distinct by what the image is: base, package manager, packages, final "
                    + "user, ports, entrypoint form and stage count."),
            Map.entry("Archetypes", "An archetype is a family of Dockerfiles that build essentially the same image "
                    + "(same base, nearly the same packages, user and ports) even when their text differs. Few "
                    + "archetypes covering many Dockerfiles means a few maintained templates could replace them."),
            Map.entry("Archetypes covering 80%", "How many archetypes, largest first, cover 80% of locations: the "
                    + "fewer, the more standard the estate."),
            Map.entry("Archetypes covering 95%", "How many archetypes, largest first, cover 95% of locations."),
            Map.entry("Images in Artifactory", "Tagged amd64 images read from Artifactory's local Docker "
                    + "repositories."),
            Map.entry("With a known parent", "Share of images whose parent was found from their layers, roots "
                    + "included."),
            Map.entry("Linked to a Dockerfile", "Images matched to a Dockerfile discovery found, by comparing their "
                    + "build steps."),
            Map.entry("No Dockerfile found (gaps)", "Images with a parent in Artifactory but no matching Dockerfile: "
                    + "built from somewhere discovery does not see."),
            Map.entry("Built outside (external bases)", "Images with no parent in Artifactory and no Dockerfile: "
                    + "bases brought in from outside."),
            Map.entry("On an outdated base", "Images built on a base that has since been pushed again under the "
                    + "same name; a rebuild would pick up the newer one."),
            Map.entry("How alike", "Counting the same Dockerfiles four ways, each looser than the last: byte for "
                    + "byte; ignoring comments and spacing; by the kind of image they build (base, packages, user, "
                    + "ports); and by archetype. The drop from the first number to the last shows how much of the "
                    + "estate is copies and variations of a few designs."),
            Map.entry("Rates", "The estate-wide shares behind the health strip, with the template count."),
            Map.entry("Archetype coverage", "Share of locations covered as archetypes are added, largest first."),
            Map.entry("Top archetypes", "The largest families of near-identical Dockerfiles, by locations: what "
                    + "each builds, how many Dockerfiles and repositories share it, and one example to read. The full "
                    + "ranking is in archetypes.json."),
            Map.entry("Top bases", "The images Dockerfiles build their final stage FROM, by locations. Colour is "
                    + "the status from the end-of-life list (EOL_LIST); the hatched part is pinned by digest."),
            Map.entry("Parse outcome", "Distinct files: read, templates saved under a Dockerfile's name, and those "
                    + "the parser could not read."),
            Map.entry("Package managers", "Locations whose RUN instructions call each package manager."),
            Map.entry("Final user", "The user the image ends running as: root, a numeric UID, a name, or a "
                    + "variable that could not be resolved."),
            Map.entry("Lineage", "Images in Artifactory, each linked to its parent image and to the Dockerfile it "
                    + "was built from."));
}
