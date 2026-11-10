package io.github.fiftieshousewife.dockerfileinventory.report;

import static io.github.fiftieshousewife.dockerfileinventory.report.DockerfilePage.table;
import static io.github.fiftieshousewife.dockerfileinventory.report.Html.escape;

import java.util.List;
import java.util.stream.Collectors;

final class DockerfileSections {

    private DockerfileSections() {
    }

    static String image(CatalogueEntry entry) {
        CatalogueEntry.Image image = entry.image();
        return "<h2>The final image</h2>" + table(List.of("User", "Kind", "Ports", "Entrypoint", "Command"),
                List.of(List.of(image.user() == null ? "not set" : image.user(), image.userKind(),
                        String.join(" ", image.ports()), command(image.entrypoint()), command(image.cmd()))));
    }

    static String stages(CatalogueEntry entry) {
        List<CatalogueEntry.StageEntry> stages = entry.build().stages();
        return "<h2>Stages</h2>" + stageTable(stages) + "<h3>Copies</h3>" + copyTable(stages)
                + "<h3>Ownership and modes</h3>" + permissionTable(stages);
    }

    static String stageTable(List<CatalogueEntry.StageEntry> stages) {
        return table(List.of("Stage", "Base", "Builds on", "Copies from", "Instructions", "Commands", "Layers",
                "Unusual"), stages.stream().map(stage -> List.of(
                        stage.index() + (stage.name() == null ? "" : " " + stage.name()), base(stage),
                        text(stage.fromStage()), String.join(" ", stage.copiesFrom()),
                        String.valueOf(stage.instructions()), String.valueOf(stage.commands()),
                        String.valueOf(stage.layers()), String.join(", ", stage.unusual()))).toList());
    }

    static String base(CatalogueEntry.StageEntry stage) {
        return stage.baseName() == null ? ""
                : stage.baseName() + (stage.baseTag() == null ? "" : ":" + stage.baseTag());
    }

    static String copyTable(List<CatalogueEntry.StageEntry> stages) {
        return table(List.of("Stage", "Instruction", "From", "To", "Origin", "Owner", "Mode"),
                stages.stream().flatMap(stage -> stage.copies().stream().map(copy -> List.of(
                        String.valueOf(stage.index()), copy.instruction(), String.join(" ", copy.sources()),
                        text(copy.destination()), copy.origin() + (copy.from() == null ? "" : " " + copy.from()),
                        text(copy.chown()), text(copy.chmod())))).toList());
    }

    static String permissionTable(List<CatalogueEntry.StageEntry> stages) {
        return table(List.of("Stage", "Command", "Setting", "Paths"),
                stages.stream().flatMap(stage -> stage.permissions().stream().map(change -> List.of(
                        String.valueOf(stage.index()), change.command() + (change.recursive() ? " -R" : ""),
                        change.setting(), String.join(" ", change.paths())))).toList());
    }

    static String packages(CatalogueEntry entry) {
        return "<h2>Packages and runtimes</h2>" + table(List.of("Stage", "Manager", "Package", "Versioned", "Cleaned"),
                entry.build().packages().stream().map(installed -> List.of(text(installed.stage()), installed.manager(),
                        installed.name(), installed.versioned() ? "yes" : "no", installed.cleaned() ? "yes" : "no"))
                        .toList())
                + "<p>Runtimes: " + escape(entry.build().runtimes().stream()
                        .map(Runtimes::label).distinct()
                        .collect(Collectors.joining(", "))) + "</p>";
    }

    static String images(CatalogueEntry entry) {
        Hierarchies.Hierarchy hierarchy = entry.hierarchy();
        Hierarchies.Layers layers = hierarchy.layers();
        return "<h2>Images</h2><p>Layers: " + layers.own() + " own, " + layers.inherited() + " inherited ("
                + escape(layers.counted()) + ")" + (layers.overLimit() ? ", over the limit" : "") + "</p>"
                + hierarchy.images().stream().map(DockerfileSections::chain).collect(Collectors.joining())
                + (hierarchy.candidateImages().isEmpty() ? "" : "<p>May also have built: "
                        + escape(String.join(", ", hierarchy.candidateImages())) + "</p>");
    }

    static String chain(Hierarchies.BuiltImage image) {
        return "<h3>" + escape(String.join(", ", image.names())) + "</h3>"
                + table(List.of("Image", "Own layers", "Created", "Pushed", "Unmaintained"),
                image.chain().stream().map(step -> List.of(String.join(", ", step.names()),
                        text(step.ownLayers()), text(step.created()),
                        text(step.lastPushed()), String.join("; ", step.unmaintained()))).toList());
    }

    static String text(Object value) {
        return value == null ? "" : value.toString();
    }

    static String command(CatalogueEntry.Command command) {
        return command == null ? "" : String.join(" ", command.arguments()) + " (" + command.form() + ")";
    }
}
