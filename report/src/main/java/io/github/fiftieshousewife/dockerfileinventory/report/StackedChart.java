package io.github.fiftieshousewife.dockerfileinventory.report;

import static io.github.fiftieshousewife.dockerfileinventory.report.Html.escape;
import static io.github.fiftieshousewife.dockerfileinventory.report.Html.percent;
import static io.github.fiftieshousewife.dockerfileinventory.report.Svg.WIDTH;
import static io.github.fiftieshousewife.dockerfileinventory.report.Svg.number;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

final class StackedChart {

    record Segment(String label, int count) {
    }

    private static final int SLOTS = 8;
    private static final int STACK_START = 20;
    private static final int STACK_WIDTH = 600;
    private static final int GAP = 2;

    private StackedChart() {
    }

    static String render(String name, List<Segment> segments) {
        List<Segment> parts = folded(segments);
        List<Double> shares = shares(parts);
        double available = STACK_WIDTH - GAP * (parts.size() - 1.0);
        List<Double> widths = shares.stream().map(share -> share * available).toList();
        List<Double> starts = starts(widths);
        String bars = IntStream.range(0, parts.size())
                .mapToObj(i -> bar(i, parts.get(i), shares.get(i), starts.get(i), widths.get(i)))
                .collect(Collectors.joining());
        String legend = IntStream.range(0, parts.size())
                .mapToObj(i -> key(i, parts.get(i), shares.get(i)))
                .collect(Collectors.joining());
        return "<svg class=\"chart stack\" viewBox=\"0 0 " + WIDTH + " 32\" role=\"img\" aria-label=\""
                + escape(name) + "\">" + bars + "</svg><p class=\"legend\">" + legend + "</p>";
    }

    static List<Segment> folded(List<Segment> segments) {
        if (segments.size() <= SLOTS) {
            return segments;
        }
        List<Segment> kept = new ArrayList<>(segments.subList(0, SLOTS - 1));
        kept.add(new Segment("other", segments.subList(SLOTS - 1, segments.size()).stream()
                .mapToInt(Segment::count).sum()));
        return kept;
    }

    static List<Double> shares(List<Segment> parts) {
        int total = parts.stream().mapToInt(Segment::count).sum();
        return parts.stream().map(part -> total == 0 ? 0 : (double) part.count() / total).toList();
    }

    static List<Double> starts(List<Double> widths) {
        List<Double> starts = new ArrayList<>();
        double x = STACK_START;
        for (double width : widths) {
            starts.add(x);
            x += width + GAP;
        }
        return starts;
    }

    private static String bar(int index, Segment part, double share, double x, double width) {
        return "<rect class=\"seg s" + (index + 1) + "\" x=\"" + number(x) + "\" width=\"" + number(width)
                + "\" y=\"4\" height=\"24\" rx=\"3\"><title>" + escape(part.label()) + ": " + part.count() + " ("
                + percent(share) + ")</title></rect>";
    }

    private static String key(int index, Segment part, double share) {
        return "<span class=\"key s" + (index + 1) + "\"></span>" + escape(part.label()) + " " + part.count()
                + " (" + percent(share) + ")";
    }
}
