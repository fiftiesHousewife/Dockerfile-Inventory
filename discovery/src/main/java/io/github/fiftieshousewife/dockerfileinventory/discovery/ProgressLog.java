package io.github.fiftieshousewife.dockerfileinventory.discovery;

import java.time.Duration;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

final class ProgressLog implements AutoCloseable {

    private final Progress progress;
    private final Consumer<String> log;
    private final ScheduledExecutorService timer = Executors.newSingleThreadScheduledExecutor(
            Thread.ofPlatform().daemon().name("discovery-progress").factory());

    ProgressLog(Progress progress, Duration every, Consumer<String> log) {
        this.progress = progress;
        this.log = log;
        timer.scheduleAtFixedRate(this::tick, every.toMillis(), every.toMillis(), TimeUnit.MILLISECONDS);
    }

    void tick() {
        if (!progress.asking()) {
            log.accept(progress.line());
        }
    }

    boolean stopped() {
        return timer.isShutdown();
    }

    @Override
    public void close() {
        timer.shutdownNow();
    }
}
