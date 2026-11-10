package io.github.fiftieshousewife.dockerfileinventory.http;

import java.time.Duration;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;

final class Latencies {

    static final int FIRST_CALLS = 20;
    static final long MINUTE = Duration.ofMinutes(1).toNanos();

    private final List<Long> first = new ArrayList<>();
    private final Deque<Answer> lastMinute = new ArrayDeque<>();

    private record Answer(long at, long took) {
    }

    void add(long now, Duration took) {
        if (first.size() < FIRST_CALLS) {
            first.add(took.toNanos());
        }
        lastMinute.addLast(new Answer(now, took.toNanos()));
        while (lastMinute.peekFirst().at() < now - MINUTE) {
            lastMinute.removeFirst();
        }
    }

    boolean known() {
        return first.size() == FIRST_CALLS;
    }

    boolean slowerThan(double times) {
        return known() && median(lastMinute.stream().map(Answer::took).toList()) > times * median(first);
    }

    static long median(List<Long> values) {
        List<Long> sorted = values.stream().sorted().toList();
        return sorted.get(sorted.size() / 2);
    }
}
