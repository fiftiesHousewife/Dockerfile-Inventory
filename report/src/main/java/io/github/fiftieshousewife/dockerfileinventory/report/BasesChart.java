package io.github.fiftieshousewife.dockerfileinventory.report;

import static io.github.fiftieshousewife.dockerfileinventory.report.Html.escape;
import static io.github.fiftieshousewife.dockerfileinventory.report.Svg.WIDTH;
import static io.github.fiftieshousewife.dockerfileinventory.report.Svg.number;

import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

final class BasesChart {

    private static final int ROW = 24;
    private static final int LABEL_END = 260;
    private static final int BAR_START = 270;
    private static final int BAR_WIDTH = 330;
    private static final int TOP_BASES = 20;
    private static final int LABEL_CHARS = 40;

    private BasesChart() {
    }

    static String render(List<BaseRow> bases) {
        List<BaseRow> top = bases.stream().limit(TOP_BASES).toList();
        int max = top.stream().mapToInt(BaseRow::locations).max().orElse(1);
        String rows = IntStream.range(0, top.size())
                .mapToObj(i -> row(top.get(i), i * ROW + 4, max))
                .collect(Collectors.joining());
        return "<svg class=\"chart bars\" viewBox=\"0 0 " + WIDTH + " " + (top.size() * ROW + 8)
                + "\" role=\"img\" aria-label=\"Most-used bases by locations\"><defs><pattern id=\"hatch\" "
                + "width=\"6\" height=\"6\" patternUnits=\"userSpaceOnUse\" patternTransform=\"rotate(45)\">"
                + "<line x1=\"0\" y1=\"0\" x2=\"0\" y2=\"6\" class=\"hatch\"/></pattern></defs>" + rows + "</svg>";
    }

    static String row(BaseRow base, int y, int max) {
        String full = base.path() + (base.tag() == null ? "" : ":" + base.tag());
        double width = (double) base.locations() / max * BAR_WIDTH;
        double pinned = pinnedWidth(base, width);
        String status = escape(base.status());
        return "<g><title>" + escape(full) + ": " + base.locations() + " locations, " + base.pinnedLocations()
                + " pinned by digest, " + status + "</title>"
                + "<text class=\"label\" x=\"" + LABEL_END + "\" y=\"" + (y + 13) + "\" text-anchor=\"end\">"
                + escape(shortened(full)) + "</text>"
                + "<rect class=\"bar " + status + "\" width=\"" + number(width) + "\" x=\"" + BAR_START
                + "\" y=\"" + y + "\" height=\"16\" rx=\"3\"/>"
                + (pinned > 0 ? "<rect class=\"pinned\" width=\"" + number(pinned) + "\" x=\"" + BAR_START
                        + "\" y=\"" + y + "\" height=\"16\" rx=\"3\" fill=\"url(#hatch)\"/>" : "")
                + "<text class=\"axis\" x=\"" + number(BAR_START + width + 6) + "\" y=\"" + (y + 13) + "\">"
                + base.locations() + "</text></g>";
    }

    static String shortened(String name) {
        return name.length() <= LABEL_CHARS ? name : name.substring(0, LABEL_CHARS - 1) + "…";
    }

    static double pinnedWidth(BaseRow base, double width) {
        return base.locations() == 0 ? 0 : (double) base.pinnedLocations() / base.locations() * width;
    }
}
