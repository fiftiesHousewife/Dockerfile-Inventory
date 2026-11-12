package io.github.fiftieshousewife.dockerfileinventory.http;

import java.time.Duration;
import java.util.Map;
import java.util.function.LongSupplier;

/**
 * Spaces a run's calls to one server at most a set number a second, across every thread, and slows down when the
 * server does: when the last minute's median answer takes twice as long as the run's first calls did, or the server
 * answers 429 or 503, the rate halves (to a sixteenth of the setting at least); once answers are prompt again it
 * doubles back to the setting. The rate changes at most once in ten seconds.
 */
public final class Throttle {

    /** No limit: for tests and tools that call a fake. */
    public static final Throttle NONE = new Throttle(Double.POSITIVE_INFINITY, System::nanoTime, Sleeper.REAL);

    static final long BETWEEN_CHANGES = Duration.ofSeconds(10).toNanos();
    static final double FLOOR = 1.0 / 16;

    private final double setting;
    private final LongSupplier nanos;
    private final Sleeper sleeper;
    private final Object lock = new Object();
    private final Latencies latencies = new Latencies();
    private double rate;
    private long next = Long.MIN_VALUE;
    private long changed = Long.MIN_VALUE;
    private long calls;

    /**
     * A throttle at the given rate.
     *
     * @param requestsPerSecond the most calls a second
     * @param nanos the clock
     * @param sleeper how to wait
     */
    public Throttle(double requestsPerSecond, LongSupplier nanos, Sleeper sleeper) {
        if (!(requestsPerSecond > 0)) {
            throw new IllegalArgumentException("requests per second must be above 0, not " + requestsPerSecond);
        }
        this.setting = requestsPerSecond;
        this.rate = requestsPerSecond;
        this.nanos = nanos;
        this.sleeper = sleeper;
    }

    /**
     * A throttle at the given rate, on the system clock.
     *
     * @param requestsPerSecond the most calls a second
     * @return the throttle
     */
    public static Throttle of(double requestsPerSecond) {
        return new Throttle(requestsPerSecond, System::nanoTime, Sleeper.REAL);
    }

    /**
     * The rate a setting holds: required, a number of calls a second above 0.
     *
     * @param env the settings
     * @param name the setting
     * @return the rate
     */
    public static double rate(Map<String, String> env, String name) {
        String value = env.get(name);
        if (value == null || value.isBlank()) {
            throw new IllegalStateException(name + " is required: the most calls a second");
        }
        try {
            double rate = Double.parseDouble(value.strip());
            if (rate > 0 && Double.isFinite(rate)) {
                return rate;
            }
        } catch (NumberFormatException e) {
            throw new IllegalStateException(name + " is a number of calls a second above 0, not " + value, e);
        }
        throw new IllegalStateException(name + " is a number of calls a second above 0, not " + value);
    }

    /**
     * How many calls a setting allows at once: a positive whole number, or the fallback when unset.
     *
     * @param env the settings
     * @param name the setting
     * @param fallback the number when it is unset
     * @return the number
     */
    public static int concurrency(Map<String, String> env, String name, int fallback) {
        String value = env.get(name);
        if (value == null || value.isBlank()) {
            return fallback;
        }
        if (!value.strip().matches("[1-9][0-9]{0,5}")) {
            throw new IllegalStateException(name + " is a positive whole number, not " + value);
        }
        return Integer.parseInt(value.strip());
    }

    /**
     * Waits for this call's turn.
     *
     * @throws InterruptedException when interrupted while waiting
     */
    public void acquire() throws InterruptedException {
        Duration wait = reserve();
        if (!wait.isZero()) {
            sleeper.sleep(wait);
        }
    }

    Duration reserve() {
        synchronized (lock) {
            long now = nanos.getAsLong();
            long slot = next == Long.MIN_VALUE ? now : Math.max(now, next);
            next = slot + (long) (1e9 / rate);
            return Duration.ofNanos(slot - now);
        }
    }

    /**
     * Tells the throttle how a call went.
     *
     * @param took how long the answer took
     * @param status the answer's status
     */
    public void record(Duration took, int status) {
        synchronized (lock) {
            calls++;
            long now = nanos.getAsLong();
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
    }

    double target(int status) {
        if (status == 429 || status == 503 || latencies.slowerThan(2)) {
            return Math.max(setting * FLOOR, rate / 2);
        }
        boolean prompt = latencies.known() && !latencies.slowerThan(1.5);
        return rate < setting && prompt ? Math.min(setting, rate * 2) : rate;
    }

    double rate() {
        synchronized (lock) {
            return rate;
        }
    }

    /**
     * Counts the calls recorded, for a run's progress.
     *
     * @return how many calls have been recorded
     */
    public long calls() {
        synchronized (lock) {
            return calls;
        }
    }
}
