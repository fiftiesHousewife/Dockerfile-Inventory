package io.github.fiftieshousewife.dockerfileinventory.report;

import static io.github.fiftieshousewife.dockerfileinventory.report.DockerfilePage.table;
import static io.github.fiftieshousewife.dockerfileinventory.report.Html.escape;

import java.util.List;
import java.util.stream.Collectors;

// The parts of a Dockerfile's page drawn from what it builds: the final image, its stages, packages and images.
final class DockerfileSections {

    private DockerfileSections() {
    }

    static String image(CatalogueEntry entry) {
        CatalogueEntry.Image image = entry.image();
        return "<h2>The final image</h2>" + table(List.of("User", "Kind", "Ports", "Entrypoint", "Command"),
                List.of(List.of(image.user() == null ? "not set" : image.user(), image.userKind(), String.join(" ", image.ports()),
                        command(image.entrypoint()), command(image.cmd()))));
    }

    static String stages(CatalogueEntry entry) {
        return "<h2>Stages</h2>" + table(List.of("Stage", "Base", "Builds on", "Copies from", "Instructions",
                "Commands", "Layers", "Unusual"), entry.build().stages().stream().map(s -> List.of(
                        s.index() + (s.name() == null ? "" : " " + s.name()),
                        s.baseName() == null ? "" : s.baseName() + (s.baseTag() == null ? "" : ":" + s.baseTag()),
                        text(s.fromStage()), String.join(" ", s.copiesFrom()),
                        String.valueOf(s.instructions()), String.valueOf(s.commands()), String.valueOf(s.layers()),
                        String.join(", ", s.unusual()))).toList())
                + "<h3>Copies</h3>" + table(List.of("Stage", "Instruction", "From", "To", "Origin", "Owner", "Mode"),
                entry.build().stages().stream().flatMap(s -> s.copies().stream().map(c -> List.of(
                        String.valueOf(s.index()), c.instruction(), String.join(" ", c.sources()),
                        text(c.destination()), c.origin() + (c.from() == null ? "" : " " + c.from()),
                        text(c.chown()),
                        text(c.chmod())))).toList())
                + "<h3>Ownership and modes</h3>" + table(List.of("Stage", "Command", "Setting", "Paths"),
                entry.build().stages().stream().flatMap(s -> s.permissions().stream().map(p -> List.of(
                        String.valueOf(s.index()), p.command() + (p.recursive() ? " -R" : ""), p.setting(),
                        String.join(" ", p.paths())))).toList());
    }

    static String packages(CatalogueEntry entry) {
        return "<h2>Packages and runtimes</h2>" + table(List.of("Stage", "Manager", "Package", "Versioned", "Cleaned"),
                entry.build().packages().stream().map(p -> List.of(text(p.stage()), p.manager(), p.name(),
                        p.versioned() ? "yes" : "no", p.cleaned() ? "yes" : "no")).toList())
                + "<p>Runtimes: " + escape(entry.build().runtimes().stream().map(r -> r.runtime() + " (" + r.where()
                        + ")").distinct().collect(Collectors.joining(", "))) + "</p>";
    }

    static String images(CatalogueEntry entry) {
        Hierarchies.Hierarchy h = entry.hierarchy();
        return "<h2>Images</h2><p>Layers: " + h.layers().own() + " own, " + h.layers().inherited() + " inherited ("
                + escape(h.layers().counted()) + ")" + (h.layers().overLimit() ? ", over the limit" : "") + "</p>"
                + h.images().stream().map(i -> "<h3>" + escape(String.join(", ", i.names())) + "</h3>"
                        + table(List.of("Image", "Own layers", "Created", "Pushed", "Unmaintained"),
                        i.chain().stream().map(s -> List.of(String.join(", ", s.names()),
                                text(s.ownLayers()), text(s.created()),
                                text(s.lastPushed()), String.join("; ", s.unmaintained()))).toList()))
                .collect(Collectors.joining())
                + (h.candidateImages().isEmpty() ? "" : "<p>May also have built: " + escape(String.join(", ",
                        h.candidateImages())) + "</p>");
    }

    private static String text(Object value) {
        return value == null ? "" : value.toString();
    }

    private static String command(CatalogueEntry.Command command) {
        return command == null ? "" : String.join(" ", command.arguments()) + " (" + command.form() + ")";
    }
}
