package io.github.fiftieshousewife.dockerfileinventory.http;

import java.time.Duration;

final class Slowdown {

    static final long BETWEEN_CHANGES = Duration.ofSeconds(10).toNanos();
    static final double FLOOR = 1.0 / 16;

    private final double setting;
    private final Latencies latencies = new Latencies();
    private double rate;
    private long changed = Long.MIN_VALUE;

    Slowdown(double setting) {
        this.setting = setting;
        this.rate = setting;
    }

    void record(long now, Duration took, int status) {
        latencies.add(now, took);
        if (changed != Long.MIN_VALUE && now - changed < BETWEEN_CHANGES) {
            return;
        }
        double changedTo = target(status);
        if (changedTo != rate) {
            rate = changedTo;
            changed = now;
        }
    }

    double target(int status) {
        if (status == 429 || status == 503 || latencies.slowerThan(2)) {
            return Math.max(setting * FLOOR, rate / 2);
        }
        boolean prompt = latencies.known() && !latencies.slowerThan(1.5);
        return rate < setting && prompt ? Math.min(setting, rate * 2) : rate;
    }

    double rate() {
        return rate;
    }
}
