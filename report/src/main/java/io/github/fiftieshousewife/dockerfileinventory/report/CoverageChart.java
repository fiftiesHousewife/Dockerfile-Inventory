package io.github.fiftieshousewife.dockerfileinventory.report;

import static io.github.fiftieshousewife.dockerfileinventory.report.Svg.WIDTH;
import static io.github.fiftieshousewife.dockerfileinventory.report.Svg.number;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

final class CoverageChart {

    private static final int HEIGHT = 240;
    private static final int LEFT = 44;
    private static final int RIGHT = 12;
    private static final int TOP = 12;
    private static final int BOTTOM = 28;
    private static final int MAX_POINTS = 200;

    private CoverageChart() {
    }

    static String render(Ranking ranking) {
        List<Double> shares = cumulative(ranking);
        String plotBottom = number(HEIGHT - BOTTOM);
        return "<svg class=\"chart\" viewBox=\"0 0 " + WIDTH + " " + HEIGHT + "\" role=\"img\" aria-label=\""
                + "Share of locations covered by the top archetypes\">"
                + grid(0.0, "0%") + grid(0.5, "50%") + grid(1.0, "100%")
                + "<polyline class=\"line\" fill=\"none\" points=\"" + points(shares) + "\"/>"
                + marker(ranking.k80(), shares, "80%") + marker(ranking.k95(), shares, "95%")
                + "<text class=\"axis\" x=\"" + LEFT + "\" y=\"" + (HEIGHT - 8) + "\">archetypes, most locations "
                + "first (" + shares.size() + ")</text>"
                + "<line class=\"baseline\" x1=\"" + LEFT + "\" y1=\"" + plotBottom + "\" x2=\""
                + (WIDTH - RIGHT) + "\" y2=\"" + plotBottom + "\"/></svg>";
    }

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

    static String points(List<Double> shares) {
        int n = shares.size();
        int step = Math.max(1, (n + MAX_POINTS - 1) / MAX_POINTS);
        return IntStream.rangeClosed(0, n)
                .filter(rank -> rank == 0 || rank == n || rank % step == 0)
                .mapToObj(rank -> number(x(rank, n)) + "," + number(y(rank == 0 ? 0 : shares.get(rank - 1))))
                .collect(Collectors.joining(" "));
    }

    static String grid(double share, String label) {
        String y = number(y(share));
        return "<line class=\"grid\" x1=\"" + LEFT + "\" y1=\"" + y + "\" x2=\"" + (WIDTH - RIGHT) + "\" y2=\"" + y
                + "\"/><text class=\"axis\" x=\"" + (LEFT - 6) + "\" y=\"" + number(y(share) + 4)
                + "\" text-anchor=\"end\">" + label + "</text>";
    }

    static String marker(int rank, List<Double> shares, String label) {
        int n = shares.size();
        if (rank < 1 || rank > n) {
            return "";
        }
        double x = x(rank, n);
        double y = y(shares.get(rank - 1));
        return "<g class=\"marker\"><circle cx=\"" + number(x) + "\" cy=\"" + number(y) + "\" r=\"5\"><title>"
                + label + " of locations: " + rank + " archetypes</title></circle><text class=\"label\" x=\""
                + number(x + 9) + "\" y=\"" + number(y + 14) + "\">" + label + " at " + rank + "</text></g>";
    }

    static double x(int rank, int n) {
        return LEFT + (n == 0 ? 0 : (double) rank / n) * (WIDTH - LEFT - RIGHT);
    }

    static double y(double share) {
        return HEIGHT - BOTTOM - share * (HEIGHT - TOP - BOTTOM);
    }
}
