package io.github.fiftieshousewife.dockerfileinventory.report;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

/** The dashboard's charts, as inline SVG strings: no script, no external file, hover by SVG {@code <title>}. */
final class Charts {

    private Charts() {
    }

    /** The share of locations covered by the first 1, 2, ... archetypes in rank order. */
    static List<Double> cumulative(Ranking ranking) {
        double total = ranking.totalLocations();
        List<Double> shares = new ArrayList<>();
        int sum = 0;
        for (Archetype a : ranking.archetypes()) {
            sum += a.locations();
            shares.add(total == 0 ? 0 : sum / total);
        }
        return shares;
    }

    private static final int WIDTH = 640;
    private static final int HEIGHT = 240;
    private static final int LEFT = 44;
    private static final int RIGHT = 12;
    private static final int TOP = 12;
    private static final int BOTTOM = 28;
    private static final int MAX_POINTS = 200;

    /**
     * Locations covered against archetypes ranked, from the origin to 100%: how concentrated the estate is. The 80%
     * and 95% points are marked and labelled; a curve longer than {@value #MAX_POINTS} points is thinned, keeping the
     * last, so a large estate's page stays small.
     */
    static String coverage(Ranking ranking) {
        List<Double> shares = cumulative(ranking);
        int n = shares.size();
        int step = Math.max(1, (n + MAX_POINTS - 1) / MAX_POINTS);
        String points = IntStream.rangeClosed(0, n)
                .filter(rank -> rank == 0 || rank == n || rank % step == 0)
                .mapToObj(rank -> point(rank, n, rank == 0 ? 0 : shares.get(rank - 1)))
                .collect(Collectors.joining(" "));
        String plotBottom = number(HEIGHT - BOTTOM);
        return "<svg class=\"chart\" viewBox=\"0 0 " + WIDTH + " " + HEIGHT + "\" role=\"img\" aria-label=\""
                + "Share of locations covered by the top archetypes\">"
                + grid(0.0, "0%") + grid(0.5, "50%") + grid(1.0, "100%")
                + "<polyline class=\"line\" fill=\"none\" points=\"" + points + "\"/>"
                + marker(ranking.k80(), n, shares, "80%") + marker(ranking.k95(), n, shares, "95%")
                + "<text class=\"axis\" x=\"" + LEFT + "\" y=\"" + (HEIGHT - 8) + "\">archetypes, most locations "
                + "first (" + n + ")</text>"
                + "<line class=\"baseline\" x1=\"" + LEFT + "\" y1=\"" + plotBottom + "\" x2=\""
                + (WIDTH - RIGHT) + "\" y2=\"" + plotBottom + "\"/></svg>";
    }

    private static String grid(double share, String label) {
        String y = number(y(share));
        return "<line class=\"grid\" x1=\"" + LEFT + "\" y1=\"" + y + "\" x2=\"" + (WIDTH - RIGHT) + "\" y2=\"" + y
                + "\"/><text class=\"axis\" x=\"" + (LEFT - 6) + "\" y=\"" + number(y(share) + 4)
                + "\" text-anchor=\"end\">" + label + "</text>";
    }

    private static String marker(int rank, int n, List<Double> shares, String label) {
        if (rank < 1 || rank > n) {
            return "";
        }
        double x = x(rank, n);
        double y = y(shares.get(rank - 1));
        return "<g class=\"marker\"><circle cx=\"" + number(x) + "\" cy=\"" + number(y) + "\" r=\"5\"><title>"
                + label + " of locations: " + rank + " archetypes</title></circle><text class=\"label\" x=\""
                + number(x + 9) + "\" y=\"" + number(y + 14) + "\">" + label + " at " + rank + "</text></g>";
    }

    private static String point(int rank, int n, double share) {
        return number(x(rank, n)) + "," + number(y(share));
    }

    private static double x(int rank, int n) {
        return LEFT + (n == 0 ? 0 : (double) rank / n) * (WIDTH - LEFT - RIGHT);
    }

    private static double y(double share) {
        return HEIGHT - BOTTOM - share * (HEIGHT - TOP - BOTTOM);
    }

    private static String number(double value) {
        return String.format(Locale.ROOT, "%.1f", value);
    }

    private static final int ROW = 24;
    private static final int LABEL_END = 260;
    private static final int BAR_START = 270;
    private static final int BAR_WIDTH = 330;
    private static final int TOP_BASES = 20;
    /** What fits left of the bars at the label size; the hover text keeps the whole name. */
    private static final int LABEL_CHARS = 40;

    /**
     * The most-used bases as horizontal bars: length is locations, the class is the end-of-life status (the legend
     * beside the chart names each in words), and the part of each bar pinned by digest is hatched.
     */
    static String bases(List<BaseRow> bases) {
        List<BaseRow> top = bases.stream().limit(TOP_BASES).toList();
        int max = top.stream().mapToInt(BaseRow::locations).max().orElse(1);
        StringBuilder rows = new StringBuilder();
        for (int i = 0; i < top.size(); i++) {
            rows.append(baseRow(top.get(i), i * ROW + 4, max));
        }
        return "<svg class=\"chart bars\" viewBox=\"0 0 " + WIDTH + " " + (top.size() * ROW + 8)
                + "\" role=\"img\" aria-label=\"Most-used bases by locations\"><defs><pattern id=\"hatch\" "
                + "width=\"6\" height=\"6\" patternUnits=\"userSpaceOnUse\" patternTransform=\"rotate(45)\">"
                + "<line x1=\"0\" y1=\"0\" x2=\"0\" y2=\"6\" class=\"hatch\"/></pattern></defs>" + rows + "</svg>";
    }

    private static String baseRow(BaseRow base, int y, int max) {
        String full = base.path() + (base.tag() == null ? "" : ":" + base.tag());
        String name = Dashboard.escape(full);
        String shown = Dashboard.escape(
                full.length() <= LABEL_CHARS ? full : full.substring(0, LABEL_CHARS - 1) + "\u2026");
        double width = (double) base.locations() / max * BAR_WIDTH;
        double pinned = base.locations() == 0 ? 0 : (double) base.pinnedLocations() / base.locations() * width;
        String status = Dashboard.escape(base.status());
        return "<g><title>" + name + ": " + base.locations() + " locations, " + base.pinnedLocations()
                + " pinned by digest, " + status + "</title>"
                + "<text class=\"label\" x=\"" + LABEL_END + "\" y=\"" + (y + 13) + "\" text-anchor=\"end\">"
                + shown + "</text>"
                + "<rect class=\"bar " + status + "\" width=\"" + number(width) + "\" x=\"" + BAR_START
                + "\" y=\"" + y + "\" height=\"16\" rx=\"3\"/>"
                + (pinned > 0 ? "<rect class=\"pinned\" width=\"" + number(pinned) + "\" x=\"" + BAR_START
                        + "\" y=\"" + y + "\" height=\"16\" rx=\"3\" fill=\"url(#hatch)\"/>" : "")
                + "<text class=\"axis\" x=\"" + number(BAR_START + width + 6) + "\" y=\"" + (y + 13) + "\">"
                + base.locations() + "</text></g>";
    }

    /** One part of a stacked bar. */
    record Segment(String label, int count) {
    }

    private static final int SLOTS = 8;
    private static final int STACK_START = 20;
    private static final int STACK_WIDTH = 600;
    private static final int GAP = 2;

    /**
     * One total split into parts, in the order given, which the caller keeps fixed so a part keeps its colour from run
     * to run; past {@value #SLOTS} parts the rest fold into "other". The legend gives each part's count and share, so
     * colour is never the only key.
     */
    static String stacked(String name, List<Segment> segments) {
        List<Segment> parts = folded(segments);
        int total = parts.stream().mapToInt(Segment::count).sum();
        double available = STACK_WIDTH - GAP * (parts.size() - 1.0);
        StringBuilder bars = new StringBuilder();
        StringBuilder legend = new StringBuilder();
        double x = STACK_START;
        for (int i = 0; i < parts.size(); i++) {
            Segment part = parts.get(i);
            double share = total == 0 ? 0 : (double) part.count() / total;
            double width = share * available;
            String label = Dashboard.escape(part.label());
            String slot = "s" + (i + 1);
            bars.append("<rect class=\"seg ").append(slot).append("\" x=\"").append(number(x)).append("\" width=\"")
                    .append(number(width)).append("\" y=\"4\" height=\"24\" rx=\"3\"><title>").append(label)
                    .append(": ").append(part.count()).append(" (").append(percent(share)).append(")</title></rect>");
            legend.append("<span class=\"key ").append(slot).append("\"></span>").append(label).append(' ')
                    .append(part.count()).append(" (").append(percent(share)).append(')');
            x += width + GAP;
        }
        return "<svg class=\"chart stack\" viewBox=\"0 0 " + WIDTH + " 32\" role=\"img\" aria-label=\""
                + Dashboard.escape(name) + "\">" + bars + "</svg><p class=\"legend\">" + legend + "</p>";
    }

    private static List<Segment> folded(List<Segment> segments) {
        if (segments.size() <= SLOTS) {
            return segments;
        }
        List<Segment> kept = new ArrayList<>(segments.subList(0, SLOTS - 1));
        kept.add(new Segment("other", segments.subList(SLOTS - 1, segments.size()).stream()
                .mapToInt(Segment::count).sum()));
        return kept;
    }

    private static String percent(double share) {
        return String.format(Locale.ROOT, "%.1f%%", share * 100);
    }
}
